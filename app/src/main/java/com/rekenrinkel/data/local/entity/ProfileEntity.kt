package com.rekenrinkel.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val age: Int,
    val theme: String,
    val currentLevel: Int,
    val totalXp: Int,
    val currentStreak: Int,
    val lastSessionDate: Long?,
    val longestStreak: Int,
    val placementCompleted: Boolean,
    val startingBand: String
)