package com.sagar.voice_shield.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_history")
data class CallHistoryEntity(
    @PrimaryKey
    val id: String,
    val callerName: String,
    val callerNumber: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val riskScore: Int,
    val deepfakeProbability: Float,
    val isBlocked: Boolean
)
