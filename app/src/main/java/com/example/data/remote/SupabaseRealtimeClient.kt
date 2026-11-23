package com.example.data.remote

import com.example.data.model.MessageItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

sealed interface RealtimeChatEvent {
    data object Connected : RealtimeChatEvent
    data object Disconnected : RealtimeChatEvent
    data class MessageInserted(val message: MessageItem) : RealtimeChatEvent
    data class MessageUpdated(val message: MessageItem) : RealtimeChatEvent
    data object InboxActivityChanged : RealtimeChatEvent
}

class SupabaseRealtimeClient(
    private val okHttpClient: OkHttpClient
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val messageAdapter = moshi.adapter(MessageItem::class.java)

    fun observeConversation(
        conversationId: String,
        accessToken: String
    ): Flow<RealtimeChatEvent> = observePostgresChanges(
        topic = "realtime:chat_$conversationId",
        accessToken = accessToken,
        changesConfig = listOf(
            ChangeSubscription(
                event = "*",
                table = "messages",
                filter = "conversation_id=eq.$conversationId"
            )
        )
    )

    fun observeUserInbox(
        userId: String,
        accessToken: String
    ): Flow<RealtimeChatEvent> = observePostgresChanges(
        topic = "realtime:inbox_$userId",
        accessToken = accessToken,
        changesConfig = listOf(
            ChangeSubscription(event = "*", table = "messages", filter = null),
            ChangeSubscription(event = "*", table = "conversations", filter = null)
        )
    )

    private data class ChangeSubscription(
        val event: String,
        val table: String,
        val filter: String?
    )

    private fun observePostgresChanges(
        topic: String,
        accessToken: String,
        changesConfig: List<ChangeSubscription>
    ): Flow<RealtimeChatEvent> = callbackFlow {
        if (!SupabaseConfig.isConfigured) {
            trySend(RealtimeChatEvent.Disconnected)
            close()
            return@callbackFlow
        }

        val wsBase = SupabaseConfig.url
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val wsUrl = "$wsBase/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&vsn=1.0.0"

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var heartbeatJob: Job? = null
        var webSocket: WebSocket? = null
        var refCounter = 1

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val changesArray = JSONArray().apply {
                    changesConfig.forEach { cfg ->
                        put(JSONObject().apply {
                            put("event", cfg.event)
                            put("schema", "public")
                            put("table", cfg.table)
                            if (!cfg.filter.isNullOrBlank()) {
                                put("filter", cfg.filter)
                            }
                        })
                    }
                }
                val joinMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("ref", (refCounter++).toString())
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("broadcast", JSONObject().put("self", false))
                            put("presence", JSONObject().put("key", ""))
                            put("postgres_changes", changesArray)
                        })
                        if (accessToken.isNotBlank()) {
                            put("access_token", accessToken)
                        }
                    })
                }
                webSocket.send(joinMsg.toString())

                heartbeatJob?.cancel()
                heartbeatJob = scope.launch {
                    while (isActive) {
                        delay(25_000)
                        val hb = JSONObject().apply {
                            put("topic", "phoenix")
                            put("event", "heartbeat")
                            put("payload", JSONObject())
                            put("ref", (refCounter++).toString())
                        }
                        webSocket.send(hb.toString())
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val root = JSONObject(text)
                    val event = root.optString("event")
                    val payload = root.optJSONObject("payload") ?: return@runCatching

                    when (event) {
                        "phx_reply" -> {
                            val status = payload.optString("status")
                            if (status == "ok" && root.optString("topic") == topic) {
                                trySend(RealtimeChatEvent.Connected)
                            }
                        }
                        "postgres_changes" -> {
                            val data = payload.optJSONObject("data") ?: return@runCatching
                            val table = data.optString("table")
                            val changeType = data.optString("type").uppercase()
                            if (table == "messages") {
                                val recordJson = data.optJSONObject("record")?.toString()
                                val parsed = recordJson?.let { runCatching { messageAdapter.fromJson(it) }.getOrNull() }
                                if (parsed != null) {
                                    when (changeType) {
                                        "INSERT" -> trySend(RealtimeChatEvent.MessageInserted(parsed))
                                        "UPDATE" -> trySend(RealtimeChatEvent.MessageUpdated(parsed))
                                    }
                                }
                                trySend(RealtimeChatEvent.InboxActivityChanged)
                            } else if (table == "conversations") {
                                trySend(RealtimeChatEvent.InboxActivityChanged)
                            }
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                trySend(RealtimeChatEvent.Disconnected)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                trySend(RealtimeChatEvent.Disconnected)
            }
        }

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, listener)

        awaitClose {
            heartbeatJob?.cancel()
            runCatching { webSocket?.close(1000, "closed") }
        }
    }
}
