package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ComplaintDao {
    @Query("SELECT * FROM complaints WHERE citizenEmail = :email ORDER BY createdAt DESC")
    fun getComplaintsForCitizen(email: String): Flow<List<ComplaintEntity>>

    @Query("SELECT * FROM complaints ORDER BY createdAt DESC")
    fun getAllComplaints(): Flow<List<ComplaintEntity>>

    @Query("SELECT * FROM complaints WHERE id = :id LIMIT 1")
    suspend fun getComplaintById(id: Long): ComplaintEntity?

    @Query("SELECT * FROM complaints WHERE id = :id LIMIT 1")
    fun getComplaintFlowById(id: Long): Flow<ComplaintEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaint(complaint: ComplaintEntity): Long

    @Update
    suspend fun updateComplaint(complaint: ComplaintEntity)

    @Query("DELETE FROM complaints")
    suspend fun deleteAll()
}
