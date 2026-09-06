package com.sagar.voice_shield.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallHistoryDao {
    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE id = :callId")
    suspend fun getCallById(callId: String): CallHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallHistoryEntity)

    @Query("DELETE FROM call_history")
    suspend fun clearAll()
}
