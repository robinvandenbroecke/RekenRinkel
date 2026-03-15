package com.rekenrinkel.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_results")
data class ExerciseResultEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val skillId: String,
    val isCorrect: Boolean,
    val responseTimeMs: Long,
    val givenAnswer: String,
    val timestamp: Long
)