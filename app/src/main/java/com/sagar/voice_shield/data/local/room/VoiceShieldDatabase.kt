package com.sagar.voice_shield.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CallHistoryEntity::class], version = 1, exportSchema = false)
abstract class VoiceShieldDatabase : RoomDatabase() {

    abstract fun callHistoryDao(): CallHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceShieldDatabase? = null

        fun getDatabase(context: Context): VoiceShieldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceShieldDatabase::class.java,
                    "voice_shield_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
