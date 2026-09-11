package com.sagar.voice_shield.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trusted_contacts")
data class TrustedContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val relation: String = "",
    @ColumnInfo(name = "voice_enrolled") val voiceEnrolled: Boolean = false,
    @ColumnInfo(name = "trust_level") val trustLevel: String = "LOW",
    @ColumnInfo(name = "last_verified") val lastVerified: String = "Never",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface TrustedContactDao {

    @Query("SELECT * FROM trusted_contacts ORDER BY created_at DESC")
    fun getAllContacts(): Flow<List<TrustedContactEntity>>

    @Query("SELECT * FROM trusted_contacts WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'")
    fun searchContacts(query: String): Flow<List<TrustedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: TrustedContactEntity)

    @Delete
    suspend fun deleteContact(contact: TrustedContactEntity)

    @Query("DELETE FROM trusted_contacts WHERE id = :contactId")
    suspend fun deleteById(contactId: String)

    @Query("SELECT COUNT(*) FROM trusted_contacts")
    suspend fun getContactCount(): Int
}
