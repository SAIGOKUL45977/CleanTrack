package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseHttpException(val code: Int, message: String) : Exception(message)

/** Supabase REST APIs. Only the public publishable/anon key belongs in the APK. */
class SupabaseApi(context: Context) {
    val url = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
    private val store = EncryptedSessionStore(context)
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val refreshLock = Mutex()
    @Volatile private var session: JSONObject? = store.read()
    @Volatile private var generation = 0
    val userId: String? get() = session?.optJSONObject("user")?.optString("id")
    private fun configured() {
        require(url.startsWith("https://") && !url.contains("REPLACE") && key.isNotBlank() && !key.contains("REPLACE")) {
            "Configure SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in .env, then rebuild the app."
        }
        require(!key.startsWith("sb_secret_")) { "Use the publishable key, never a secret key." }
        if (key.startsWith("eyJ")) {
            val payload = runCatching { String(android.util.Base64.decode(key.split('.')[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)) }.getOrNull()
            require(payload == null || JSONObject(payload).optString("role") != "service_role") { "Use the anon key, never the service_role key." }
        }
    }
    private fun save(data: JSONObject) {
        if (!data.has("expires_at")) data.put("expires_at", System.currentTimeMillis() / 1000 + data.optLong("expires_in", 3600))
        store.write(data); session = data
    }
    suspend fun signIn(email: String, password: String) {
        val data = JSONObject(raw("/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", email).put("password", password).toString().toByteArray(), null))
        generation++; save(data)
    }
    suspend fun signUp(name: String, email: String, password: String): Boolean {
        val data = JSONObject(raw("/auth/v1/signup", "POST", JSONObject().put("email", email)
            .put("password", password).put("data", JSONObject().put("full_name", name)).toString().toByteArray(), null))
        if (data.optString("access_token").isBlank()) return false
        generation++; save(data); return true
    }
    fun clearSession() { generation++; session = null; store.clear() }
    fun hasSession() = session != null
    private suspend fun accessToken(force: Boolean = false, failedToken: String? = null): String = refreshLock.withLock {
        val current = session ?: throw Exception("Please sign in again.")
        val token = current.getString("access_token")
        if (force && failedToken != null && token != failedToken) return@withLock token
        if (!force && current.optLong("expires_at") > System.currentTimeMillis() / 1000 + 60) return@withLock token
        val before = generation
        try {
            val updated = JSONObject(raw("/auth/v1/token?grant_type=refresh_token", "POST",
                JSONObject().put("refresh_token", current.getString("refresh_token")).toString().toByteArray(), null))
            if (before != generation) throw Exception("The session changed. Please sign in again.")
            save(updated); updated.getString("access_token")
        } catch (e: SupabaseHttpException) {
            if (e.code == 400 || e.code == 401 || e.code == 403) clearSession()
            throw e
        }
    }
    suspend fun request(path: String, method: String = "GET", json: JSONObject? = null): String =
        requestBytes(path, method, json?.toString()?.toByteArray(), "application/json")
    suspend fun requestBytes(path: String, method: String, bytes: ByteArray?, mime: String): String {
        val token = accessToken()
        return try { raw(path, method, bytes, token, mime) }
        catch (e: SupabaseHttpException) {
            if (e.code != 401) throw e
            raw(path, method, bytes, accessToken(true, token), mime)
        }
    }
    private suspend fun raw(path: String, method: String, bytes: ByteArray?, token: String?, mime: String = "application/json"): String = withContext(Dispatchers.IO) {
        configured()
        val body = if (method in listOf("GET", "HEAD")) null else (bytes ?: ByteArray(0)).toRequestBody(mime.toMediaType())
        val request = Request.Builder().url(url + path).header("apikey", key)
            .method(method, body).apply { if (token != null) header("Authorization", "Bearer $token") }.build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { JSONObject(text) }.getOrNull()
                val message = error?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: error?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: error?.optString("error_description")?.takeIf { it.isNotBlank() }
                    ?: "Supabase request failed (${response.code})."
                throw SupabaseHttpException(response.code, message)
            }
            text
        }
    }
}
