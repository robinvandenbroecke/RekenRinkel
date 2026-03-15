package com.rekenrinkel.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skill_progress")
data class SkillProgressEntity(
    @PrimaryKey
    val skillId: String,
    val masteryScore: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val averageResponseTimeMs: Long,
    val lastPracticed: Long?,
    val currentDifficulty: Int
)