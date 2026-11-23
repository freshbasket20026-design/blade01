package com.example.data.remote

import com.example.BuildConfig
import java.net.URI

object SupabaseConfig {

    val url: String
        get() = normalizeSupabaseUrl(BuildConfig.SUPABASE_URL)

    val anonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim().trim('"', '\'')

    val googleWebClientId: String
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim().trim('"', '\'')
            .takeIf { it.isNotBlank() && it != "YOUR_GOOGLE_WEB_CLIENT_ID" }
            ?: ""

    val defaultAdminUserId: String
        get() = BuildConfig.ADMIN_USER_ID.trim().trim('"', '\'').ifBlank { "blade_admin" }

    val defaultAdminPassword: String
        get() = BuildConfig.ADMIN_PASSWORD.trim().trim('"', '\'').ifBlank { "BladeAdmin@2026" }

    val isConfigured: Boolean
        get() = url.startsWith("https://") &&
            !url.contains("your-project.supabase.co") &&
            anonKey.isNotBlank() &&
            anonKey != "YOUR_SUPABASE_ANON_KEY"

    internal fun normalizeSupabaseUrl(raw: String): String {
        var cleaned = raw.trim().trim('"', '\'').trimEnd('/')
        if (cleaned.isBlank()) return ""

        // Handle accidental dashboard URL paste: https://supabase.com/dashboard/project/<ref>
        val dashboardMatch = Regex("""supabase\.com/dashboard/project/([a-zA-Z0-9_-]+)""").find(cleaned)
        if (dashboardMatch != null) {
            val projectRef = dashboardMatch.groupValues[1]
            return "https://$projectRef.supabase.co"
        }

        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            cleaned = "https://$cleaned"
        }

        return try {
            val uri = URI(cleaned)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return cleaned.trimEnd('/')
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            cleaned.trimEnd('/')
        }
    }
}
