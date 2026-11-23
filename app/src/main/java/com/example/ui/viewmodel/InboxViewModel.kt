package com.example.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ConversationItem
import com.example.data.model.MessageItem
import com.example.data.model.NotificationItem
import com.example.data.model.UiState
import com.example.data.remote.RealtimeChatEvent
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID

data class InboxUiState(
    val notificationsState: UiState<List<NotificationItem>> = UiState.Loading,
    val conversationsState: UiState<List<ConversationItem>> = UiState.Loading,
    val selectedFilter: String = "All",
    val activeConversation: ConversationItem? = null,
    val messagesState: UiState<List<MessageItem>> = UiState.Loading,
    val isRealtimeConnected: Boolean = false,
    val isSendingMedia: Boolean = false,
    val stagedImageUri: Uri? = null,
    val unreadCount: Int = 0,
    val error: String? = null
)

class InboxViewModel(private val repository: BladeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private var chatPollingJob: Job? = null
    private var chatRealtimeJob: Job? = null
    private var inboxRealtimeJob: Job? = null
    private var activeChatId: String? = null

    fun refreshUnreadCount() {
        viewModelScope.launch {
            val count = repository.getUnreadNotificationsCount()
            _uiState.update { it.copy(unreadCount = count) }
        }
    }

    fun clearUnreadCount() {
        _uiState.update { it.copy(unreadCount = 0) }
    }

    fun loadInbox() {
        viewModelScope.launch {
            if (_uiState.value.notificationsState !is UiState.Success) {
                _uiState.update { it.copy(notificationsState = UiState.Loading) }
            }
            if (_uiState.value.conversationsState !is UiState.Success) {
                _uiState.update { it.copy(conversationsState = UiState.Loading) }
            }
            repository.getNotifications(markAsRead = true).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            notificationsState = UiState.Success(list),
                            unreadCount = 0
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(notificationsState = UiState.Error(err.message ?: "Error")) }
                }
            )
            refreshConversationsOnly()
        }
    }

    fun refreshConversationsOnly() {
        viewModelScope.launch {
            repository.getConversations().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(conversationsState = UiState.Success(list)) }
                },
                onFailure = { err ->
                    if (_uiState.value.conversationsState !is UiState.Success) {
                        _uiState.update { it.copy(conversationsState = UiState.Error(err.message ?: "Error")) }
                    }
                }
            )
        }
    }

    fun startInboxRealtime() {
        inboxRealtimeJob?.cancel()
        inboxRealtimeJob = viewModelScope.launch {
            repository.observeInboxRealtime().collect { event ->
                if (event is RealtimeChatEvent.InboxActivityChanged || event is RealtimeChatEvent.Connected) {
                    refreshConversationsOnly()
                }
            }
        }
    }

    fun stopInboxRealtime() {
        inboxRealtimeJob?.cancel()
        inboxRealtimeJob = null
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun openConversationWithUser(targetUserId: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            repository.startOrGetConversation(targetUserId).fold(
                onSuccess = { conv ->
                    _uiState.update { it.copy(activeConversation = conv) }
                    onOpened(conv.id)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(error = err.message) }
                }
            )
        }
    }

    fun startChatPolling(conversationId: String, currentUserId: String? = null) {
        if (activeChatId != conversationId) {
            activeChatId = conversationId
            _uiState.update {
                it.copy(
                    messagesState = UiState.Loading,
                    stagedImageUri = null,
                    isRealtimeConnected = false
                )
            }
        }

        viewModelScope.launch {
            if (_uiState.value.activeConversation?.id != conversationId) {
                repository.getConversationById(conversationId)?.let { conv ->
                    _uiState.update { it.copy(activeConversation = conv) }
                }
            }
        }

        chatRealtimeJob?.cancel()
        chatRealtimeJob = viewModelScope.launch {
            while (isActive) {
                runCatching {
                    repository.observeConversationRealtime(conversationId).collect { event ->
                        when (event) {
                            is RealtimeChatEvent.Connected -> {
                                _uiState.update { it.copy(isRealtimeConnected = true) }
                                syncConversationOnce(conversationId, currentUserId)
                            }
                            is RealtimeChatEvent.Disconnected -> {
                                _uiState.update { it.copy(isRealtimeConnected = false) }
                            }
                            is RealtimeChatEvent.MessageInserted -> {
                                val normalized = event.message.normalized()
                                upsertSingleMessage(normalized)
                                if (currentUserId != null && normalized.senderId != currentUserId) {
                                    repository.markConversationRead(conversationId)
                                }
                            }
                            is RealtimeChatEvent.MessageUpdated -> {
                                upsertSingleMessage(event.message.normalized())
                            }
                            else -> Unit
                        }
                    }
                }
                _uiState.update { it.copy(isRealtimeConnected = false) }
                delay(4000)
            }
        }

        chatPollingJob?.cancel()
        chatPollingJob = viewModelScope.launch {
            while (isActive) {
                syncConversationOnce(conversationId, currentUserId)
                val interval = if (_uiState.value.isRealtimeConnected) 10_000L else 2_500L
                delay(interval)
            }
        }
    }

    private suspend fun syncConversationOnce(conversationId: String, currentUserId: String?) {
        repository.getMessages(conversationId).fold(
            onSuccess = { serverMsgs ->
                val currentList = (_uiState.value.messagesState as? UiState.Success)?.data ?: emptyList()
                val merged = mergeMessages(currentList, serverMsgs)
                _uiState.update { it.copy(messagesState = UiState.Success(merged)) }

                val hasUnreadFromOther = currentUserId != null && serverMsgs.any {
                    it.senderId != currentUserId && it.resolvedStatus != "read"
                }
                if (hasUnreadFromOther) {
                    repository.markConversationRead(conversationId)
                    // Refresh immediately so local state reflects read status too
                    repository.getMessages(conversationId).getOrNull()?.let { refreshed ->
                        val latestList = (_uiState.value.messagesState as? UiState.Success)?.data ?: emptyList()
                        _uiState.update { it.copy(messagesState = UiState.Success(mergeMessages(latestList, refreshed))) }
                    }
                }
            },
            onFailure = { err ->
                if (_uiState.value.messagesState is UiState.Loading) {
                    _uiState.update { it.copy(messagesState = UiState.Error(err.message ?: "Failed to load messages")) }
                }
            }
        )
    }

    fun stopChatPolling() {
        chatPollingJob?.cancel()
        chatPollingJob = null
        chatRealtimeJob?.cancel()
        chatRealtimeJob = null
        _uiState.update { it.copy(isRealtimeConnected = false) }
    }

    fun stageImage(uri: Uri?) {
        _uiState.update { it.copy(stagedImageUri = uri) }
    }

    fun sendMessage(conversationId: String, text: String, senderId: String = "") {
        val clean = text.trim()
        if (clean.isBlank()) return
        val clientId = UUID.randomUUID().toString()
        val optimistic = MessageItem(
            id = "local_$clientId",
            conversationId = conversationId,
            senderId = senderId,
            clientId = clientId,
            messageType = "text",
            content = clean,
            status = "sending",
            createdAt = Instant.now().toString()
        )
        upsertSingleMessage(optimistic)

        viewModelScope.launch {
            sendWithRetry(
                conversationId = conversationId,
                content = clean,
                clientId = clientId,
                messageType = "text",
                mediaUrl = "",
                mediaDurationMs = 0L
            ).fold(
                onSuccess = { serverMsg ->
                    upsertSingleMessage(serverMsg)
                },
                onFailure = { err ->
                    markLocalMessageFailed(clientId)
                    _uiState.update { it.copy(error = err.message ?: "Message failed to send") }
                }
            )
        }
    }

    fun sendImageMessage(
        contentResolver: ContentResolver,
        conversationId: String,
        senderId: String,
        imageUri: Uri,
        caption: String = ""
    ) {
        val clientId = UUID.randomUUID().toString()
        val cleanCaption = caption.trim()
        val optimistic = MessageItem(
            id = "local_$clientId",
            conversationId = conversationId,
            senderId = senderId,
            clientId = clientId,
            messageType = "image",
            content = cleanCaption,
            mediaUrl = imageUri.toString(),
            status = "sending",
            createdAt = Instant.now().toString(),
            localPreviewUri = imageUri.toString()
        )
        _uiState.update { it.copy(stagedImageUri = null, isSendingMedia = true) }
        upsertSingleMessage(optimistic)

        viewModelScope.launch {
            val uploadRes = uploadImageWithRetry(contentResolver, imageUri)
            val uploadedUrl = uploadRes.getOrNull()
            if (uploadedUrl == null) {
                markLocalMessageFailed(clientId)
                _uiState.update {
                    it.copy(
                        isSendingMedia = false,
                        error = uploadRes.exceptionOrNull()?.message ?: "Failed to upload photo"
                    )
                }
                return@launch
            }

            sendWithRetry(
                conversationId = conversationId,
                content = cleanCaption,
                clientId = clientId,
                messageType = "image",
                mediaUrl = uploadedUrl,
                mediaDurationMs = 0L
            ).fold(
                onSuccess = { serverMsg ->
                    upsertSingleMessage(serverMsg.copy(localPreviewUri = imageUri.toString()))
                    _uiState.update { it.copy(isSendingMedia = false) }
                },
                onFailure = { err ->
                    markLocalMessageFailed(clientId)
                    _uiState.update {
                        it.copy(
                            isSendingMedia = false,
                            error = err.message ?: "Failed to send photo"
                        )
                    }
                }
            )
        }
    }

    fun sendVoiceMessage(
        contentResolver: ContentResolver,
        conversationId: String,
        senderId: String,
        voiceFile: File,
        durationMs: Long
    ) {
        val clientId = UUID.randomUUID().toString()
        val optimistic = MessageItem(
            id = "local_$clientId",
            conversationId = conversationId,
            senderId = senderId,
            clientId = clientId,
            messageType = "voice",
            content = "",
            mediaUrl = voiceFile.absolutePath,
            mediaDurationMs = durationMs,
            status = "sending",
            createdAt = Instant.now().toString(),
            localPreviewUri = voiceFile.absolutePath
        )
        _uiState.update { it.copy(isSendingMedia = true) }
        upsertSingleMessage(optimistic)

        viewModelScope.launch {
            val uploadRes = uploadVoiceWithRetry(contentResolver, voiceFile)
            val uploadedUrl = uploadRes.getOrNull()
            if (uploadedUrl == null) {
                markLocalMessageFailed(clientId)
                _uiState.update {
                    it.copy(
                        isSendingMedia = false,
                        error = uploadRes.exceptionOrNull()?.message ?: "Failed to upload voice message"
                    )
                }
                return@launch
            }

            sendWithRetry(
                conversationId = conversationId,
                content = "",
                clientId = clientId,
                messageType = "voice",
                mediaUrl = uploadedUrl,
                mediaDurationMs = durationMs
            ).fold(
                onSuccess = { serverMsg ->
                    upsertSingleMessage(serverMsg.copy(localPreviewUri = voiceFile.absolutePath))
                    _uiState.update { it.copy(isSendingMedia = false) }
                },
                onFailure = { err ->
                    markLocalMessageFailed(clientId)
                    _uiState.update {
                        it.copy(
                            isSendingMedia = false,
                            error = err.message ?: "Failed to send voice message"
                        )
                    }
                }
            )
        }
    }

    fun retryFailedMessage(contentResolver: ContentResolver, failedMsg: MessageItem) {
        val clientId = failedMsg.clientId.ifBlank { UUID.randomUUID().toString() }
        upsertSingleMessage(failedMsg.copy(clientId = clientId, status = "sending"))

        viewModelScope.launch {
            when {
                failedMsg.isImageMessage -> {
                    val uriStr = failedMsg.localPreviewUri ?: failedMsg.mediaUrl
                    val mediaUrl = if (uriStr.startsWith("http")) {
                        uriStr
                    } else {
                        val res = uploadImageWithRetry(contentResolver, Uri.parse(uriStr))
                        val url = res.getOrNull()
                        if (url == null) {
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = res.exceptionOrNull()?.message ?: "Retry failed") }
                            return@launch
                        }
                        url
                    }
                    sendWithRetry(
                        conversationId = failedMsg.conversationId,
                        content = failedMsg.content,
                        clientId = clientId,
                        messageType = "image",
                        mediaUrl = mediaUrl,
                        mediaDurationMs = 0L
                    ).fold(
                        onSuccess = { upsertSingleMessage(it.copy(localPreviewUri = failedMsg.localPreviewUri)) },
                        onFailure = { err ->
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = err.message) }
                        }
                    )
                }
                failedMsg.isVoiceMessage -> {
                    val mediaUrl = if (failedMsg.mediaUrl.startsWith("http")) {
                        failedMsg.mediaUrl
                    } else {
                        val file = File(failedMsg.localPreviewUri ?: failedMsg.mediaUrl)
                        val res = uploadVoiceWithRetry(contentResolver, file)
                        val url = res.getOrNull()
                        if (url == null) {
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = res.exceptionOrNull()?.message ?: "Retry failed") }
                            return@launch
                        }
                        url
                    }
                    sendWithRetry(
                        conversationId = failedMsg.conversationId,
                        content = "",
                        clientId = clientId,
                        messageType = "voice",
                        mediaUrl = mediaUrl,
                        mediaDurationMs = failedMsg.mediaDurationMs
                    ).fold(
                        onSuccess = { upsertSingleMessage(it.copy(localPreviewUri = failedMsg.localPreviewUri)) },
                        onFailure = { err ->
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = err.message) }
                        }
                    )
                }
                else -> {
                    sendWithRetry(
                        conversationId = failedMsg.conversationId,
                        content = failedMsg.content,
                        clientId = clientId,
                        messageType = "text",
                        mediaUrl = "",
                        mediaDurationMs = 0L
                    ).fold(
                        onSuccess = { upsertSingleMessage(it) },
                        onFailure = { err ->
                            markLocalMessageFailed(clientId)
                            _uiState.update { it.copy(error = err.message) }
                        }
                    )
                }
            }
        }
    }

    private suspend fun uploadImageWithRetry(contentResolver: ContentResolver, uri: Uri): Result<String> {
        val first = repository.uploadChatImage(contentResolver, uri)
        if (first.isSuccess) return first
        delay(1200)
        return repository.uploadChatImage(contentResolver, uri)
    }

    private suspend fun uploadVoiceWithRetry(contentResolver: ContentResolver, file: File): Result<String> {
        val first = repository.uploadChatVoice(contentResolver, file)
        if (first.isSuccess) return first
        delay(1200)
        return repository.uploadChatVoice(contentResolver, file)
    }

    private suspend fun sendWithRetry(
        conversationId: String,
        content: String,
        clientId: String,
        messageType: String,
        mediaUrl: String,
        mediaDurationMs: Long
    ): Result<MessageItem> {
        val first = repository.sendMessage(
            conversationId = conversationId,
            content = content,
            clientId = clientId,
            messageType = messageType,
            mediaUrl = mediaUrl,
            mediaDurationMs = mediaDurationMs
        )
        if (first.isSuccess) return first
        delay(1200)
        return repository.sendMessage(
            conversationId = conversationId,
            content = content,
            clientId = clientId,
            messageType = messageType,
            mediaUrl = mediaUrl,
            mediaDurationMs = mediaDurationMs
        )
    }

    private fun upsertSingleMessage(incoming: MessageItem) {
        _uiState.update { state ->
            val current = (state.messagesState as? UiState.Success)?.data ?: emptyList()
            val merged = mergeMessages(current, listOf(incoming))
            state.copy(messagesState = UiState.Success(merged))
        }
    }

    private fun markLocalMessageFailed(clientId: String) {
        _uiState.update { state ->
            val current = (state.messagesState as? UiState.Success)?.data ?: return@update state
            val updated = current.map { m ->
                if (m.clientId == clientId && m.id.startsWith("local_")) {
                    m.copy(status = "failed")
                } else m
            }
            state.copy(messagesState = UiState.Success(updated))
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopChatPolling()
        stopInboxRealtime()
    }

    companion object {
        private fun statusRank(msg: MessageItem): Int = when (msg.resolvedStatus) {
            "read" -> 4
            "delivered" -> 3
            "sent" -> 2
            "sending" -> 1
            else -> 0
        }

        private fun pickFresherMessage(existing: MessageItem, candidate: MessageItem): MessageItem {
            // Prefer non-local server message over optimistic local message
            val mergedClientId = candidate.clientId.ifBlank { existing.clientId }
            val existingLocal = existing.id.startsWith("local_")
            val candidateLocal = candidate.id.startsWith("local_")
            if (existingLocal && !candidateLocal) {
                return candidate.copy(
                    clientId = mergedClientId,
                    localPreviewUri = candidate.localPreviewUri ?: existing.localPreviewUri
                )
            }
            if (!existingLocal && candidateLocal) {
                return existing.copy(
                    clientId = mergedClientId,
                    localPreviewUri = existing.localPreviewUri ?: candidate.localPreviewUri
                )
            }

            // Both are server (or both local): never downgrade read -> delivered -> sent due to stale poll race
            val preferred = if (statusRank(candidate) >= statusRank(existing)) candidate else existing
            val mergedPreview = candidate.localPreviewUri ?: existing.localPreviewUri
            val mergedDeliveredAt = candidate.deliveredAt?.takeIf { it.isNotBlank() } ?: existing.deliveredAt
            val mergedReadAt = candidate.readAt?.takeIf { it.isNotBlank() } ?: existing.readAt
            val mergedIsRead = existing.isRead || candidate.isRead || preferred.resolvedStatus == "read"
            return preferred.copy(
                clientId = mergedClientId,
                localPreviewUri = mergedPreview,
                deliveredAt = mergedDeliveredAt,
                readAt = mergedReadAt,
                isRead = mergedIsRead
            )
        }

        internal fun mergeMessages(
            current: List<MessageItem>,
            incoming: List<MessageItem>
        ): List<MessageItem> {
            val byKey = LinkedHashMap<String, MessageItem>()
            val clientToKey = HashMap<String, String>()
            val idToKey = HashMap<String, String>()

            fun upsertIntoMap(item: MessageItem) {
                val byIdKey = idToKey[item.id]
                val byClientKey = item.clientId.takeIf { it.isNotBlank() }?.let { clientToKey[it] }
                val existingKey = byIdKey ?: byClientKey
                if (existingKey != null) {
                    var prev = byKey[existingKey]!!
                    if (byIdKey != null && byClientKey != null && byIdKey != byClientKey) {
                        val duplicateLocal = byKey.remove(byClientKey)
                        if (duplicateLocal != null) {
                            prev = pickFresherMessage(duplicateLocal, prev)
                            idToKey[duplicateLocal.id] = existingKey
                        }
                    }
                    val merged = pickFresherMessage(prev, item)
                    byKey[existingKey] = merged
                    idToKey[merged.id] = existingKey
                    if (merged.clientId.isNotBlank()) clientToKey[merged.clientId] = existingKey
                } else {
                    val newKey = if (item.clientId.isNotBlank()) "c:${item.clientId}" else "i:${item.id}"
                    byKey[newKey] = item
                    idToKey[item.id] = newKey
                    if (item.clientId.isNotBlank()) clientToKey[item.clientId] = newKey
                }
            }

            current.forEach(::upsertIntoMap)
            incoming.forEach(::upsertIntoMap)

            return byKey.values.sortedWith(
                compareBy<MessageItem>(
                    { if (it.id.startsWith("local_")) 1 else 0 },
                    { it.createdAt }
                )
            )
        }
    }
}
