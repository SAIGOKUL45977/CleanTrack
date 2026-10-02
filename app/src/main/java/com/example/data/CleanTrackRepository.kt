package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.random.Random

class CleanTrackRepository(private val db: AppDatabase) {

    val complaintDao = db.complaintDao()
    val userDao = db.userDao()

    // Citizen complaints flow
    fun getCitizenComplaints(email: String): Flow<List<ComplaintEntity>> {
        return complaintDao.getComplaintsForCitizen(email)
    }

    fun getComplaintFlowById(id: Long): Flow<ComplaintEntity?> {
        return complaintDao.getComplaintFlowById(id)
    }

    suspend fun getComplaintById(id: Long): ComplaintEntity? {
        return complaintDao.getComplaintById(id)
    }

    // User authentication
    suspend fun registerUser(user: UserEntity) {
        userDao.insertUser(user)
    }

    suspend fun getUserById(id: String): UserEntity? {
        return userDao.getUserById(id)
    }

    suspend fun getUserByEmail(email: String): UserEntity? {
        return userDao.getUserByEmail(email)
    }

    // Submit new complaint & trigger simulated AI check
    suspend fun submitComplaint(
        context: Context,
        citizenEmail: String,
        citizenName: String,
        category: String,
        photoUri: String,
        latitude: Double,
        longitude: Double,
        locationAddress: String,
        description: String?
    ): Long {
        val now = System.currentTimeMillis()
        val initialLogs = listOf(
            StatusLogItem(
                status = ComplaintStatus.SUBMITTED,
                timestamp = now,
                note = "Complaint reported by citizen"
            )
        )

        val complaint = ComplaintEntity(
            citizenEmail = citizenEmail,
            citizenName = citizenName,
            category = category,
            photoUri = photoUri,
            latitude = latitude,
            longitude = longitude,
            locationAddress = locationAddress,
            description = description,
            status = ComplaintStatus.SUBMITTED,
            aiCheckResult = "Processing AI Verification...",
            createdAt = now,
            statusHistoryJson = ComplaintEntity.createHistoryJson(initialLogs)
        )

        val newId = complaintDao.insertComplaint(complaint)

        // Trigger asynchronous simulated AI check after 2.5s delay
        CoroutineScope(Dispatchers.IO).launch {
            delay(2500)
            performAiCheck(newId)
        }

        return newId
    }

    // Simulated AI check with strictly 3 outcomes: "AI Verified", "Pending Manual Review", "Rejected"
    private suspend fun performAiCheck(complaintId: Long) {
        val existing = complaintDao.getComplaintById(complaintId) ?: return

        // 3 possible outcomes: AI Verified (75%), Pending Manual Review (20%), Rejected (5%)
        val rand = Random.nextInt(100)
        val outcome = when {
            rand < 75 -> ComplaintStatus.AI_VERIFIED
            rand < 95 -> ComplaintStatus.PENDING_MANUAL_REVIEW
            else -> ComplaintStatus.REJECTED
        }

        val logs = existing.getStatusLogs().toMutableList()
        val now = System.currentTimeMillis()
        val note = when (outcome) {
            ComplaintStatus.AI_VERIFIED -> "AI check completed: Image and location verified as waste site"
            ComplaintStatus.PENDING_MANUAL_REVIEW -> "AI check flagged for manual inspection"
            else -> "AI check rejected: Image unverified or duplicate submission"
        }

        logs.add(StatusLogItem(status = outcome, timestamp = now, note = note))

        val updated = existing.copy(
            status = outcome,
            aiCheckResult = outcome,
            statusHistoryJson = ComplaintEntity.createHistoryJson(logs)
        )

        complaintDao.updateComplaint(updated)
    }

    // Seeding demo data on first application launch
    suspend fun seedDemoDataIfEmpty(context: Context) {
        // Seed default citizen account if missing
        if (userDao.getUserById("citizen@cleantrack.org") == null) {
            userDao.insertUser(
                UserEntity(
                    id = "citizen@cleantrack.org",
                    name = "Sarah Jenkins",
                    email = "citizen@cleantrack.org",
                    passwordHash = "password"
                )
            )
        }

        // Seed initial complaints if empty
        val sampleList = complaintDao.getComplaintById(1L)
        if (sampleList == null) {
            val now = System.currentTimeMillis()

            val samplePhotoUri = com.example.ui.components.generateSyntheticWastePhoto(
                context,
                "Sample Overflow Bin"
            ).toString()

            val afterPhotoUri = com.example.ui.components.generateSyntheticWastePhoto(
                context,
                "Resolved Clean Bin"
            ).toString()

            // 1. AI Verified complaint
            val logs1 = listOf(
                StatusLogItem(ComplaintStatus.SUBMITTED, now - 86400000, "Reported by citizen"),
                StatusLogItem(ComplaintStatus.AI_VERIFIED, now - 82000000, "AI check completed: Image verified")
            )
            complaintDao.insertComplaint(
                ComplaintEntity(
                    citizenEmail = "citizen@cleantrack.org",
                    citizenName = "Sarah Jenkins",
                    category = "Overflowing Waste Bin",
                    photoUri = samplePhotoUri,
                    latitude = 12.9716,
                    longitude = 77.5946,
                    locationAddress = "Lat: 12.9716°, Lng: 77.5946° (Market St)",
                    description = "Waste bin overflowing near market entrance blocking pedestrian path.",
                    status = ComplaintStatus.AI_VERIFIED,
                    aiCheckResult = ComplaintStatus.AI_VERIFIED,
                    createdAt = now - 86400000,
                    statusHistoryJson = ComplaintEntity.createHistoryJson(logs1)
                )
            )

            // 2. In Progress complaint
            val logs2 = listOf(
                StatusLogItem(ComplaintStatus.SUBMITTED, now - 172800000, "Reported by citizen"),
                StatusLogItem(ComplaintStatus.AI_VERIFIED, now - 170000000, "AI check completed: Verified"),
                StatusLogItem(ComplaintStatus.IN_PROGRESS, now - 86400000, "Complaint marked In Progress")
            )
            complaintDao.insertComplaint(
                ComplaintEntity(
                    citizenEmail = "citizen@cleantrack.org",
                    citizenName = "Sarah Jenkins",
                    category = "Plastic Waste Accumulation",
                    photoUri = samplePhotoUri,
                    latitude = 12.9750,
                    longitude = 77.5990,
                    locationAddress = "Lat: 12.9750°, Lng: 77.5990° (Main Park)",
                    description = "Large collection of discarded plastic bottles near park pond.",
                    status = ComplaintStatus.IN_PROGRESS,
                    aiCheckResult = ComplaintStatus.AI_VERIFIED,
                    createdAt = now - 172800000,
                    statusHistoryJson = ComplaintEntity.createHistoryJson(logs2)
                )
            )

            // 3. Resolved complaint
            val logs3 = listOf(
                StatusLogItem(ComplaintStatus.SUBMITTED, now - 259200000, "Reported by citizen"),
                StatusLogItem(ComplaintStatus.AI_VERIFIED, now - 250000000, "AI check completed: Verified"),
                StatusLogItem(ComplaintStatus.IN_PROGRESS, now - 172800000, "Complaint marked In Progress"),
                StatusLogItem(ComplaintStatus.RESOLVED, now - 43200000, "Cleaned site verified with resolution photo")
            )
            complaintDao.insertComplaint(
                ComplaintEntity(
                    citizenEmail = "citizen@cleantrack.org",
                    citizenName = "Sarah Jenkins",
                    category = "Organic/Food Waste",
                    photoUri = samplePhotoUri,
                    afterPhotoUri = afterPhotoUri,
                    latitude = 12.9800,
                    longitude = 77.5910,
                    locationAddress = "Lat: 12.9800°, Lng: 77.5910° (Food Street)",
                    description = "Dumped vegetable waste behind commercial food court.",
                    status = ComplaintStatus.RESOLVED,
                    aiCheckResult = ComplaintStatus.AI_VERIFIED,
                    createdAt = now - 259200000,
                    statusHistoryJson = ComplaintEntity.createHistoryJson(logs3)
                )
            )
        }
    }
}
