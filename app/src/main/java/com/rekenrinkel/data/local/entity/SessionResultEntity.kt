package com.rekenrinkel.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_results")
data class SessionResultEntity(
    @PrimaryKey
    val sessionId: String,
    val startTime: Long,
    val endTime: Long?,
    val xpEarned: Int,
    val stars: Int,
    val totalExercises: Int,
    val correctAnswers: Int
)