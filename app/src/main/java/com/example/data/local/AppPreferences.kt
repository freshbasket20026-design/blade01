package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AdminAppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blade_prefs")

data class StoredSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val email: String = ""
) {
    val isLoggedIn: Boolean get() = accessToken.isNotBlank() && userId.isNotBlank()
}

data class StoredAdminSession(
    val isAuthenticated: Boolean = false,
    val adminId: String = ""
)

data class WatchAffinitySnapshot(
    val categories: Map<String, Int> = emptyMap(),
    val creators: Map<String, Int> = emptyMap(),
    val hashtags: Map<String, Int> = emptyMap(),
    val seenVideos: Map<String, Int> = emptyMap(),
    val lastShownSeq: Map<String, Int> = emptyMap()
)

data class FeedReloadState(
    val reloadCycle: Int = 0,
    val surfacedSeq: Int = 0,
    val forYouOffset: Int = 0,
    val followingOffset: Int = 0,
    val lastBatchIds: List<String> = emptyList()
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE = stringPreferencesKey("language_code")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val WATCH_AFFINITY = stringPreferencesKey("watch_affinity_json")
        val FEED_RELOAD_STATE = stringPreferencesKey("feed_reload_state_json")
        val ADMIN_LOGGED_IN = booleanPreferencesKey("admin_logged_in")
        val ADMIN_LOGGED_ID = stringPreferencesKey("admin_logged_id")
        val ADMIN_CRED_ID = stringPreferencesKey("admin_cred_id")
        val ADMIN_CRED_PASSWORD = stringPreferencesKey("admin_cred_password")
        val ADMIN_APP_SETTINGS = stringPreferencesKey("admin_app_settings_json")
        val FAVORITE_FILTERS = stringSetPreferencesKey("studio_favorite_filters")
        val DOWNLOADED_FILTERS = stringSetPreferencesKey("studio_downloaded_filters")
        val STUDIO_DRAFT_JSON = stringPreferencesKey("studio_project_draft_json")
        val OAUTH_CODE_VERIFIER = stringPreferencesKey("oauth_code_verifier")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: true
    }

    val languageCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE] ?: "en"
    }

    val sessionFlow: Flow<StoredSession> = context.dataStore.data.map { prefs ->
        StoredSession(
            accessToken = prefs[Keys.ACCESS_TOKEN] ?: "",
            refreshToken = prefs[Keys.REFRESH_TOKEN] ?: "",
            userId = prefs[Keys.USER_ID] ?: "",
            email = prefs[Keys.USER_EMAIL] ?: ""
        )
    }

    suspend fun getSessionSnapshot(): StoredSession = sessionFlow.first()

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
    }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_EMAIL] = email
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
        }
    }

    val adminSessionFlow: Flow<StoredAdminSession> = context.dataStore.data.map { prefs ->
        StoredAdminSession(
            isAuthenticated = prefs[Keys.ADMIN_LOGGED_IN] ?: false,
            adminId = prefs[Keys.ADMIN_LOGGED_ID] ?: ""
        )
    }

    suspend fun getAdminSessionSnapshot(): StoredAdminSession = adminSessionFlow.first()

    suspend fun saveAdminSession(adminId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADMIN_LOGGED_IN] = true
            prefs[Keys.ADMIN_LOGGED_ID] = adminId
        }
    }

    suspend fun clearAdminSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ADMIN_LOGGED_IN)
            prefs.remove(Keys.ADMIN_LOGGED_ID)
        }
    }

    suspend fun getAdminCredentials(defaultId: String, defaultPass: String): Pair<String, String> {
        val prefs = context.dataStore.data.first()
        val id = prefs[Keys.ADMIN_CRED_ID]?.takeIf { it.isNotBlank() } ?: defaultId
        val pass = prefs[Keys.ADMIN_CRED_PASSWORD]?.takeIf { it.isNotBlank() } ?: defaultPass
        return id to pass
    }

    suspend fun saveAdminCredentials(adminId: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADMIN_CRED_ID] = adminId
            prefs[Keys.ADMIN_CRED_PASSWORD] = password
        }
    }

    val appSettingsFlow: Flow<AdminAppSettings> = context.dataStore.data.map { prefs ->
        parseAdminAppSettings(prefs[Keys.ADMIN_APP_SETTINGS].orEmpty())
    }

    suspend fun getAppSettingsSnapshot(): AdminAppSettings = appSettingsFlow.first()

    suspend fun saveAppSettings(settings: AdminAppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADMIN_APP_SETTINGS] = JSONObject().apply {
                put("allowRegistrations", settings.allowRegistrations)
                put("allowVideoUploads", settings.allowVideoUploads)
                put("allowComments", settings.allowComments)
                put("maintenanceMode", settings.maintenanceMode)
                put("announcementBanner", settings.announcementBanner)
            }.toString()
        }
    }

    private fun parseAdminAppSettings(raw: String): AdminAppSettings = runCatching {
        if (raw.isBlank()) return AdminAppSettings()
        val obj = JSONObject(raw)
        AdminAppSettings(
            allowRegistrations = obj.optBoolean("allowRegistrations", true),
            allowVideoUploads = obj.optBoolean("allowVideoUploads", true),
            allowComments = obj.optBoolean("allowComments", true),
            maintenanceMode = obj.optBoolean("maintenanceMode", false),
            announcementBanner = obj.optString("announcementBanner", "")
        )
    }.getOrDefault(AdminAppSettings())

    suspend fun getFeedReloadState(): FeedReloadState {
        val raw = context.dataStore.data.first()[Keys.FEED_RELOAD_STATE].orEmpty()
        if (raw.isBlank()) return FeedReloadState()
        return parseFeedReloadState(raw)
    }

    suspend fun updateFeedReloadState(
        forYou: Boolean,
        newlySurfacedIds: List<String>,
        incrementCycle: Boolean,
        nextOffset: Int
    ): FeedReloadState {
        var updated = FeedReloadState()
        context.dataStore.edit { prefs ->
            val current = parseFeedReloadState(prefs[Keys.FEED_RELOAD_STATE].orEmpty())
            val affinityRoot = readAffinityRoot(prefs)
            val videosObj = affinityRoot.optJSONObject("videos") ?: JSONObject()
            val seqObj = affinityRoot.optJSONObject("lastShownSeq") ?: JSONObject()

            var seq = current.surfacedSeq
            val cleanIds = newlySurfacedIds.filter { it.isNotBlank() }.distinct()
            cleanIds.forEach { id ->
                seq += 1
                if (videosObj.optInt(id, 0) == 0) {
                    putWithMinEviction(videosObj, id, 1, maxKeys = 1000)
                }
                putWithMinEviction(seqObj, id, seq, maxKeys = 1000)
            }
            if (cleanIds.isNotEmpty()) {
                affinityRoot.put("videos", videosObj)
                affinityRoot.put("lastShownSeq", seqObj)
                prefs[Keys.WATCH_AFFINITY] = affinityRoot.toString()
            }

            updated = current.copy(
                reloadCycle = if (incrementCycle) current.reloadCycle + 1 else current.reloadCycle,
                surfacedSeq = seq,
                forYouOffset = if (forYou) nextOffset.coerceAtLeast(0) else current.forYouOffset,
                followingOffset = if (!forYou) nextOffset.coerceAtLeast(0) else current.followingOffset,
                lastBatchIds = if (incrementCycle) cleanIds.takeLast(25) else current.lastBatchIds
            )
            prefs[Keys.FEED_RELOAD_STATE] = serializeFeedReloadState(updated)
        }
        return updated
    }

    private fun parseFeedReloadState(raw: String): FeedReloadState = runCatching {
        if (raw.isBlank()) return FeedReloadState()
        val root = JSONObject(raw)
        val idsArray = root.optJSONArray("lastBatchIds") ?: root.optJSONArray("recentVideoIds")
        val ids = buildList {
            if (idsArray != null) {
                for (i in 0 until idsArray.length()) {
                    val id = idsArray.optString(i, "")
                    if (id.isNotBlank()) add(id)
                }
            }
        }
        FeedReloadState(
            reloadCycle = root.optInt("reloadCycle", 0),
            surfacedSeq = root.optInt("surfacedSeq", 0),
            forYouOffset = root.optInt("forYouOffset", 0),
            followingOffset = root.optInt("followingOffset", 0),
            lastBatchIds = ids
        )
    }.getOrDefault(FeedReloadState())

    private fun serializeFeedReloadState(state: FeedReloadState): String {
        val arr = JSONArray()
        state.lastBatchIds.forEach { arr.put(it) }
        return JSONObject().apply {
            put("reloadCycle", state.reloadCycle)
            put("surfacedSeq", state.surfacedSeq)
            put("forYouOffset", state.forYouOffset)
            put("followingOffset", state.followingOffset)
            put("lastBatchIds", arr)
        }.toString()
    }

    suspend fun getWatchAffinity(): WatchAffinitySnapshot {
        val raw = context.dataStore.data.first()[Keys.WATCH_AFFINITY].orEmpty()
        if (raw.isBlank()) return WatchAffinitySnapshot()
        return runCatching {
            val root = JSONObject(raw)
            WatchAffinitySnapshot(
                categories = jsonToIntMap(root.optJSONObject("categories")),
                creators = jsonToIntMap(root.optJSONObject("creators")),
                hashtags = jsonToIntMap(root.optJSONObject("hashtags")),
                seenVideos = jsonToIntMap(root.optJSONObject("videos")),
                lastShownSeq = jsonToIntMap(root.optJSONObject("lastShownSeq"))
            )
        }.getOrDefault(WatchAffinitySnapshot())
    }

    suspend fun markVideoSeen(videoId: String) {
        if (videoId.isBlank()) return
        context.dataStore.edit { prefs ->
            val reloadState = parseFeedReloadState(prefs[Keys.FEED_RELOAD_STATE].orEmpty())
            val nextSeq = reloadState.surfacedSeq + 1

            val root = readAffinityRoot(prefs)
            val videosObj = root.optJSONObject("videos") ?: JSONObject()
            val seqObj = root.optJSONObject("lastShownSeq") ?: JSONObject()
            val currentSeen = videosObj.optInt(videoId, 0)
            putWithMinEviction(videosObj, videoId, (currentSeen + 1).coerceAtMost(10_000), maxKeys = 1000)
            putWithMinEviction(seqObj, videoId, nextSeq, maxKeys = 1000)
            root.put("videos", videosObj)
            root.put("lastShownSeq", seqObj)

            prefs[Keys.WATCH_AFFINITY] = root.toString()
            prefs[Keys.FEED_RELOAD_STATE] = serializeFeedReloadState(
                reloadState.copy(surfacedSeq = nextSeq)
            )
        }
    }

    suspend fun recordWatchAffinity(
        creatorId: String,
        categories: List<String>,
        hashtags: List<String>,
        weight: Int
    ) {
        if (weight == 0) return
        context.dataStore.edit { prefs ->
            val root = readAffinityRoot(prefs)

            val categoriesObj = root.optJSONObject("categories") ?: JSONObject()
            val creatorsObj = root.optJSONObject("creators") ?: JSONObject()
            val hashtagsObj = root.optJSONObject("hashtags") ?: JSONObject()

            categories.forEach { cat ->
                val cleanCategory = cat.trim().lowercase()
                if (cleanCategory.isNotBlank() && cleanCategory != "all") {
                    adjustCapped(categoriesObj, cleanCategory, weight)
                }
            }
            if (creatorId.isNotBlank()) {
                adjustCapped(creatorsObj, creatorId, weight)
            }
            hashtags.forEach { tag ->
                val cleanTag = tag.trim().removePrefix("#").lowercase()
                if (cleanTag.isNotBlank()) {
                    adjustCapped(hashtagsObj, cleanTag, weight)
                }
            }

            root.put("categories", categoriesObj)
            root.put("creators", creatorsObj)
            root.put("hashtags", hashtagsObj)
            prefs[Keys.WATCH_AFFINITY] = root.toString()
        }
    }

    private fun readAffinityRoot(prefs: Preferences): JSONObject = runCatching {
        val existing = prefs[Keys.WATCH_AFFINITY].orEmpty()
        if (existing.isBlank()) JSONObject() else JSONObject(existing)
    }.getOrDefault(JSONObject())

    private fun putWithMinEviction(
        obj: JSONObject,
        key: String,
        value: Int,
        maxKeys: Int
    ) {
        obj.put(key, value)
        if (obj.length() > maxKeys) {
            var evictKey: String? = null
            var minVal = Int.MAX_VALUE
            val iter = obj.keys()
            while (iter.hasNext()) {
                val k = iter.next()
                val v = obj.optInt(k, 0)
                if (v < minVal && k != key) {
                    minVal = v
                    evictKey = k
                }
            }
            if (evictKey != null) obj.remove(evictKey)
        }
    }

    private fun adjustCapped(
        obj: JSONObject,
        key: String,
        delta: Int,
        minVal: Int = -200,
        maxVal: Int = 10_000,
        maxKeys: Int = 120
    ) {
        val current = obj.optInt(key, 0)
        obj.put(key, (current + delta).coerceIn(minVal, maxVal))
        if (obj.length() > maxKeys) {
            var evictKey: String? = null
            var minAbs = Int.MAX_VALUE
            val iter = obj.keys()
            while (iter.hasNext()) {
                val k = iter.next()
                val v = kotlin.math.abs(obj.optInt(k, 0))
                if (v < minAbs && k != key) {
                    minAbs = v
                    evictKey = k
                }
            }
            if (evictKey != null) obj.remove(evictKey)
        }
    }

    private fun jsonToIntMap(obj: JSONObject?): Map<String, Int> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, Int>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            result[k] = obj.optInt(k, 0)
        }
        return result
    }

    suspend fun getFavoriteFilterIds(defaults: Set<String>): Set<String> {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.FAVORITE_FILTERS] ?: defaults
    }

    suspend fun saveFavoriteFilterIds(ids: Set<String>) {
        context.dataStore.edit { it[Keys.FAVORITE_FILTERS] = ids }
    }

    suspend fun getDownloadedFilterIds(defaults: Set<String>): Set<String> {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.DOWNLOADED_FILTERS] ?: defaults
    }

    suspend fun saveDownloadedFilterIds(ids: Set<String>) {
        context.dataStore.edit { it[Keys.DOWNLOADED_FILTERS] = ids }
    }

    suspend fun getSavedStudioDraftJson(): String? {
        return context.dataStore.data.first()[Keys.STUDIO_DRAFT_JSON]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveStudioDraftJson(json: String) {
        context.dataStore.edit { it[Keys.STUDIO_DRAFT_JSON] = json }
    }

    suspend fun clearSavedStudioDraft() {
        context.dataStore.edit { it.remove(Keys.STUDIO_DRAFT_JSON) }
    }

    suspend fun savePendingOAuthVerifier(verifier: String) {
        context.dataStore.edit { it[Keys.OAUTH_CODE_VERIFIER] = verifier }
    }

    suspend fun consumePendingOAuthVerifier(): String {
        val prefs = context.dataStore.data.first()
        val verifier = prefs[Keys.OAUTH_CODE_VERIFIER].orEmpty()
        if (verifier.isNotBlank()) {
            context.dataStore.edit { it.remove(Keys.OAUTH_CODE_VERIFIER) }
        }
        return verifier
    }
}
