package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.remote.SupabaseApi
import com.example.data.remote.SupabaseHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Supabase is the source of truth; legacy Room demo rows are not uploaded. */
class CleanTrackRepository(context: Context) {
    private val api = SupabaseApi(context)
    private val signedImages = mutableMapOf<String, Pair<String, Long>>()
    fun hasSession() = api.hasSession()
    fun logout() { api.clearSession(); signedImages.clear() }
    suspend fun login(email: String, password: String): UserEntity {
        api.signIn(email, password); return currentUser()
    }
    suspend fun register(name: String, email: String, password: String): UserEntity? {
        if (!api.signUp(name, email, password)) return null
        return currentUser()
    }
    suspend fun currentUser(): UserEntity {
        val auth = JSONObject(api.request("/auth/v1/user"))
        val rows = JSONArray(api.request("/rest/v1/profiles?id=eq.${auth.getString("id")}&select=id,full_name,role"))
        if (rows.length() != 1) throw Exception("Profile not found. Run the CleanTrack database migration.")
        val profile = rows.getJSONObject(0)
        return UserEntity(profile.getString("id"), profile.getString("full_name"), auth.optString("email"), "")
    }
    suspend fun getCitizenComplaints(user: UserEntity): List<ComplaintEntity> {
        val rows = JSONArray(api.request("/rest/v1/complaints?citizen_id=eq.${user.id}&select=*&order=created_at.desc&limit=100"))
        if (rows.length() == 0) return emptyList()
        val ids = (0 until rows.length()).joinToString(",") { rows.getJSONObject(it).getLong("id").toString() }
        val events = JSONArray(api.request("/rest/v1/complaint_events?complaint_id=in.($ids)&select=*&order=created_at.asc,id.asc"))
        val history = mutableMapOf<Long, MutableList<StatusLogItem>>()
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            history.getOrPut(event.getLong("complaint_id")) { mutableListOf() }.add(StatusLogItem(
                event.getString("status"), parseTime(event.getString("created_at")), event.getString("note")))
        }
        return (0 until rows.length()).map { index ->
            val c = rows.getJSONObject(index)
            ComplaintEntity(id = c.getLong("id"), citizenEmail = user.email,
                citizenName = c.getString("citizen_name"), category = c.getString("category"),
                photoUri = signImage(c.getString("photo_path")),
                afterPhotoUri = if (c.isNull("resolution_photo_path")) null else signImage(c.getString("resolution_photo_path")),
                latitude = c.getDouble("latitude"), longitude = c.getDouble("longitude"),
                locationAddress = c.getString("location_address"),
                description = if (c.isNull("description")) null else c.getString("description"),
                status = c.getString("status"), aiCheckResult = c.getString("verification_status"),
                createdAt = parseTime(c.getString("created_at")),
                statusHistoryJson = ComplaintEntity.createHistoryJson(history[c.getLong("id")] ?: emptyList()))
        }
    }
    private suspend fun signImage(path: String): String {
        signedImages[path]?.let { if (it.second > System.currentTimeMillis()) return it.first }
        val response = JSONObject(api.request("/storage/v1/object/sign/complaint-images/$path", "POST", JSONObject().put("expiresIn", 3600)))
        val signed = response.getString("signedURL")
        val result = if (signed.startsWith("https://")) signed else api.url + "/storage/v1" + signed
        require(Uri.parse(result).host == Uri.parse(api.url).host) { "Unexpected image host." }
        signedImages[path] = result to (System.currentTimeMillis() + 3_000_000)
        return result
    }
    suspend fun submitComplaint(context: Context, submissionId: String, category: String, photoUri: Uri,
        latitude: Double, longitude: Double, locationAddress: String, description: String?): Long {
        val path = "${api.userId ?: throw Exception("Sign in again.")}/reports/$submissionId.jpg"
        // The same ID/path can be retried after a lost response without creating a duplicate report.
        val prior = JSONArray(api.request("/rest/v1/complaints?submission_id=eq.$submissionId&select=id"))
        if (prior.length() > 0) return prior.getJSONObject(0).getLong("id")
        val jpeg = compressPhoto(context, photoUri)
        try { api.requestBytes("/storage/v1/object/complaint-images/$path", "POST", jpeg, "image/jpeg") }
        catch (e: SupabaseHttpException) {
            if (e.code != 409 && !(e.code == 400 && e.message?.contains("already exists", true) == true)) throw e
        }
        val body = JSONObject().put("p_submission_id", submissionId).put("p_category", category)
            .put("p_photo_path", path).put("p_latitude", latitude).put("p_longitude", longitude)
            .put("p_location_address", locationAddress).put("p_description", description ?: JSONObject.NULL)
        return JSONArray(api.request("/rest/v1/rpc/submit_complaint", "POST", body)).getJSONObject(0).getLong("id")
    }
    private suspend fun compressPhoto(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The captured photo could not be read. Retake it." }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw Exception("Retake the photo.")
        try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
                output.toByteArray().also { require(it.size <= 5 * 1024 * 1024) { "Photo must be smaller than 5 MB." } }
            }
        } finally { bitmap.recycle() }
    }
    private fun parseTime(value: String): Long {
        val normalized = value.replace(Regex("\\.(\\d+)(?=Z|[+-])")) { match -> "." + match.groupValues[1].take(3).padEnd(3, '0') }
        val pattern = if (normalized.substringAfter('T').contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
        return SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(normalized)?.time
            ?: throw Exception("Invalid server timestamp.")
    }
}
