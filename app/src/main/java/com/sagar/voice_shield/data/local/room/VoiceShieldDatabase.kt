package com.sagar.voice_shield.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CallHistoryEntity::class, TrustedContactEntity::class],
    version = 3,
    exportSchema = false
)
abstract class VoiceShieldDatabase : RoomDatabase() {

    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun trustedContactDao(): TrustedContactDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceShieldDatabase? = null

        fun getDatabase(context: Context): VoiceShieldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceShieldDatabase::class.java,
                    "voice_shield_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

