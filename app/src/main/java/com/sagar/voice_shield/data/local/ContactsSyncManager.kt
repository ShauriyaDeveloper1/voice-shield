package com.sagar.voice_shield.data.local

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.sagar.voice_shield.data.local.room.TrustedContactDao
import com.sagar.voice_shield.data.local.room.TrustedContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ContactsSyncManager(
    private val context: Context,
    private val dao: TrustedContactDao
) {
    companion object {
        private const val TAG = "ContactsSyncManager"
    }

    suspend fun syncDeviceContacts(): Int = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return@withContext -1
        }

        var insertedCount = 0
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val seenNumbers = HashSet<String>()
                val contactsToInsert = mutableListOf<TrustedContactEntity>()

                while (it.moveToNext()) {
                    val rawName = if (nameIndex >= 0) it.getString(nameIndex) ?: "" else ""
                    val rawNumber = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                    val cleanDigits = rawNumber.filter { c -> c.isDigit() }

                    if (cleanDigits.length >= 7 && seenNumbers.add(cleanDigits)) {
                        val sanitizedName = rawName.replace("\n", " ").replace("\r", " ").trim().ifBlank { "Contact ($rawNumber)" }
                        val existing = dao.getContactByPhone(rawNumber.trim())

                        contactsToInsert.add(
                            TrustedContactEntity(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = sanitizedName,
                                phone = rawNumber.trim(),
                                relation = if (existing?.relation.isNullOrBlank() || existing?.relation == "Google Contact") "Google" else existing!!.relation,
                                voiceEnrolled = existing?.voiceEnrolled ?: false,
                                trustLevel = existing?.trustLevel ?: "MEDIUM",
                                lastVerified = existing?.lastVerified ?: "Synced",
                                isFavorite = existing?.isFavorite ?: false,
                                source = "GOOGLE"
                            )
                        )
                    }
                }

                if (contactsToInsert.isNotEmpty()) {
                    dao.insertContacts(contactsToInsert)
                    insertedCount = contactsToInsert.size
                    Log.i(TAG, "Successfully synced $insertedCount contacts from Google / Device")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}", e)
        }
        insertedCount
    }
}
