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
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "source") val source: String = "LOCAL",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface TrustedContactDao {

    @Query("SELECT * FROM trusted_contacts ORDER BY name COLLATE NOCASE ASC")
    fun getAllContacts(): Flow<List<TrustedContactEntity>>

    @Query("SELECT * FROM trusted_contacts WHERE is_favorite = 1 ORDER BY name COLLATE NOCASE ASC")
    fun getFavoriteContacts(): Flow<List<TrustedContactEntity>>

    @Query("SELECT * FROM trusted_contacts WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC")
    fun searchContacts(query: String): Flow<List<TrustedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: TrustedContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<TrustedContactEntity>)

    @Update
    suspend fun updateContact(contact: TrustedContactEntity)

    @Delete
    suspend fun deleteContact(contact: TrustedContactEntity)

    @Query("DELETE FROM trusted_contacts WHERE id = :contactId")
    suspend fun deleteById(contactId: String)

    @Query("UPDATE trusted_contacts SET is_favorite = :isFav WHERE id = :contactId")
    suspend fun setFavorite(contactId: String, isFav: Boolean)

    @Query("SELECT * FROM trusted_contacts WHERE phone = :phone LIMIT 1")
    suspend fun getContactByPhone(phone: String): TrustedContactEntity?

    @Query("DELETE FROM trusted_contacts WHERE name IN (:names)")
    suspend fun deleteMockContacts(names: List<String>)

    @Query("SELECT COUNT(*) FROM trusted_contacts")
    suspend fun getContactCount(): Int
}
