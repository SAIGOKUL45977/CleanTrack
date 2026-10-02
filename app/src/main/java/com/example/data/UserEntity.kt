package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String, // email for citizen
    val name: String,
    val email: String,
    val passwordHash: String
)
