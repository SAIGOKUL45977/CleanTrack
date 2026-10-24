package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String, // Supabase user ID in the active flow
    val name: String,
    val email: String,
    val passwordHash: String,
    val mobileNumber: String = ""
)
