package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

object ComplaintCategories {
    val LIST = listOf(
        "Overflowing Waste Bin",
        "Plastic Waste Accumulation",
        "Organic/Food Waste",
        "Dry Leaf & Bio Litter",
        "E-Waste Disposal"
    )
}

object ComplaintStatus {
    const val SUBMITTED = "Submitted"
    const val AI_VERIFIED = "AI Verified"
    const val PENDING_MANUAL_REVIEW = "Pending Manual Review"
    const val IN_PROGRESS = "In Progress"
    const val RESOLVED = "Resolved"
    const val REJECTED = "Rejected"
}

data class StatusLogItem(
    val status: String,
    val timestamp: Long,
    val note: String
)

@Entity(tableName = "complaints")
data class ComplaintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val citizenEmail: String,
    val citizenName: String,
    val category: String,
    val photoUri: String,
    val afterPhotoUri: String? = null,
    val latitude: Double,
    val longitude: Double,
    val locationAddress: String,
    val description: String?,
    val status: String,
    val aiCheckResult: String, // "AI Verified", "Pending Manual Review", or "Rejected"
    val createdAt: Long = System.currentTimeMillis(),
    val statusHistoryJson: String // Serialized JSONArray
) {
    fun getStatusLogs(): List<StatusLogItem> {
        val list = mutableListOf<StatusLogItem>()
        try {
            val array = JSONArray(statusHistoryJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    StatusLogItem(
                        status = obj.optString("status", ""),
                        timestamp = obj.optLong("timestamp", 0L),
                        note = obj.optString("note", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    companion object {
        fun createHistoryJson(logs: List<StatusLogItem>): String {
            val array = JSONArray()
            logs.forEach { log ->
                val obj = JSONObject()
                obj.put("status", log.status)
                obj.put("timestamp", log.timestamp)
                obj.put("note", log.note)
                array.put(obj)
            }
            return array.toString()
        }
    }
}
