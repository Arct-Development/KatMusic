package com.itzfallenme.katmusic.data.blend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itzfallenme.katmusic.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class BlendCloudRepository @Inject constructor(
    private val client: OkHttpClient,
    private val preferences: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val url = BuildConfig.BLEND_SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.BLEND_SUPABASE_PUBLISHABLE_KEY
    private val accessKey = stringPreferencesKey("blend_cloud_access_token")
    private val refreshKey = stringPreferencesKey("blend_cloud_refresh_token")
    private val userKey = stringPreferencesKey("blend_cloud_user_id")
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean get() = url.startsWith("https://") && key.isNotBlank()

    data class Account(val userId: String, val email: String)
    data class CloudMember(val userId: String, val displayName: String, val profile: BlendTasteProfile)

    suspend fun signUp(email: String, password: String): String = auth("signup", email, password)
    suspend fun signIn(email: String, password: String): String = auth("token?grant_type=password", email, password)

    private suspend fun auth(path: String, email: String, password: String): String = withContext(Dispatchers.IO) {
        requireConfigured()
        require(email.contains("@") && email.length <= 254) { "Enter a valid email address." }
        require(password.length >= 8) { "Password must contain at least 8 characters." }
        val response = request("POST", "/auth/v1/$path", body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }.toString(), authenticated = false)
        val access = response["access_token"]?.jsonPrimitive?.content
        val refresh = response["refresh_token"]?.jsonPrimitive?.content
        val user = response["user"]?.jsonObject
        val id = user?.get("id")?.jsonPrimitive?.content
        if (access.isNullOrBlank() || refresh.isNullOrBlank() || id.isNullOrBlank()) {
            throw IllegalStateException("Account created. Check your email for confirmation, then sign in.")
        }
        preferences.edit {
            it[accessKey] = access
            it[refreshKey] = refresh
            it[userKey] = id
        }
        user?.get("email")?.jsonPrimitive?.content ?: email
    }

    suspend fun account(): Account? {
        val p = preferences.data.first()
        val id = p[userKey] ?: return null
        return Account(id, "")
    }

    suspend fun signOut() {
        val token = accessToken() ?: return
        runCatching { request("POST", "/auth/v1/logout", authenticated = true, explicitToken = token) }
        preferences.edit { it.remove(accessKey); it.remove(refreshKey); it.remove(userKey) }
    }

    suspend fun syncProfile(profile: BlendTasteProfile) {
        require(profile.schemaVersion == BlendTasteProfile.CURRENT_SCHEMA_VERSION)
        require(profile.artistAffinity.size <= BlendTasteProfile.MAX_ARTISTS)
        require(profile.trackAffinity.size <= BlendTasteProfile.MAX_TRACKS)
        val userId = account()?.userId ?: error("Sign in to sync your Blend profile.")
        request(
            "POST", "/rest/v1/blend_profiles?on_conflict=user_id",
            body = buildJsonObject {
                put("user_id", userId)
                put("schema_version", profile.schemaVersion)
                put("profile", json.encodeToJsonElement(BlendTasteProfile.serializer(), profile))
                put("updated_at", java.time.Instant.now().toString())
            }.toString(),
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    suspend fun createBlend(name: String, displayName: String, inviteCode: String): String {
        val response = request("POST", "/rest/v1/rpc/create_blend", body = buildJsonObject {
            put("p_name", name.trim())
            put("p_display_name", displayName.trim())
            put("p_invite_code", inviteCode)
        }.toString())
        return response.jsonPrimitive.content
    }

    suspend fun joinBlend(inviteCode: String, displayName: String): String {
        val response = request("POST", "/rest/v1/rpc/join_blend", body = buildJsonObject {
            put("p_invite_code", inviteCode.trim())
            put("p_display_name", displayName.trim())
        }.toString())
        return response.jsonPrimitive.content
    }

    suspend fun members(blendId: String): List<CloudMember> = withContext(Dispatchers.IO) {
        val rows = request(
            "GET",
            "/rest/v1/blend_members?select=user_id,display_name&blend_id=eq.$blendId&order=joined_at.asc",
        ).jsonArray
        rows.mapNotNull { row ->
            val userId = row.jsonObject["user_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val display = row.jsonObject["display_name"]?.jsonPrimitive?.content ?: "Listener"
            val profileRows = request(
                "GET",
                "/rest/v1/blend_profiles?select=profile&user_id=eq.$userId",
            ).jsonArray
            val profile = profileRows.firstOrNull()?.jsonObject?.get("profile")
                ?.let { json.decodeFromJsonElement(BlendTasteProfile.serializer(), it) }
                ?: BlendTasteProfile()
            CloudMember(userId, display, profile)
        }
    }

    suspend fun leaveBlend(blendId: String) {
        request("POST", "/rest/v1/rpc/leave_blend", body = buildJsonObject {
            put("p_blend_id", blendId)
        }.toString())
    }

    private suspend fun accessToken(): String? = preferences.data.first()[accessKey]

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        authenticated: Boolean = true,
        explicitToken: String? = null,
        prefer: String? = null,
    ): kotlinx.serialization.json.JsonElement = withContext(Dispatchers.IO) {
        requireConfigured()
        val token = if (authenticated) explicitToken ?: accessToken() ?: error("Sign in to Group Blend first.") else key
        val builder = Request.Builder().url(url + path)
            .header("apikey", key)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
        if (prefer != null) builder.header("Prefer", prefer)
        val requestBody = body?.toRequestBody(mediaType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(mediaType))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody(mediaType))
            "DELETE" -> builder.delete(requestBody)
            else -> error("Unsupported request method")
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    val obj = json.parseToJsonElement(text).jsonObject
                    obj["message"]?.jsonPrimitive?.content
                        ?: obj["msg"]?.jsonPrimitive?.content
                        ?: obj["error_description"]?.jsonPrimitive?.content
                        ?: obj["error"]?.jsonPrimitive?.content
                }.getOrNull()
                throw IllegalStateException(message ?: "Blend server error (${response.code}).")
            }
            if (text.isBlank()) kotlinx.serialization.json.JsonNull else json.parseToJsonElement(text)
        }
    }

    private fun requireConfigured() {
        check(isConfigured) { "Group Blend cloud is not configured yet. Add its Supabase URL and publishable key to the build environment." }
    }
}
