package com.dellvantix.kidwatch.util

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import com.dellvantix.kidwatch.model.SmsMessage
import com.dellvantix.kidwatch.repository.PrefsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// ─── SMS Syncer ───────────────────────────────────────────────────────────────
@Singleton
class SmsSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: PrefsRepository
) {
    suspend fun getNewMessages(): List<SmsMessage> {
        val lastSync = prefsRepo.getLastSmsSyncTime()
        val messages = mutableListOf<SmsMessage>()

        try {
            // Inbox
            messages.addAll(querySms(Telephony.Sms.Inbox.CONTENT_URI, "INCOMING", lastSync))
            // Sent
            messages.addAll(querySms(Telephony.Sms.Sent.CONTENT_URI, "OUTGOING", lastSync))
        } catch (e: SecurityException) {
            // Permission not granted
        }

        if (messages.isNotEmpty()) {
            prefsRepo.setLastSmsSyncTime(System.currentTimeMillis())
        }

        return messages
    }

    private fun querySms(
        uri: android.net.Uri,
        type: String,
        afterTimestamp: Long
    ): List<SmsMessage> {
        val result = mutableListOf<SmsMessage>()
        val cursor = context.contentResolver.query(
            uri,
            arrayOf("address", "body", "date"),
            "date > ?",
            arrayOf(afterTimestamp.toString()),
            "date DESC"
        ) ?: return result

        cursor.use {
            while (it.moveToNext()) {
                val address = it.getString(0) ?: ""
                val body = it.getString(1) ?: ""
                val date = it.getLong(2)

                result.add(
                    SmsMessage(
                        address = address,
                        contactName = resolveContactName(address),
                        body = body,
                        type = type,
                        timestamp = date,
                        syncedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        return result
    }

    private fun resolveContactName(phoneNumber: String): String {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else ""
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

// ─── Call Log Syncer ──────────────────────────────────────────────────────────
@Singleton
class CallLogSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: PrefsRepository
) {
    suspend fun getNewCalls(): List<com.dellvantix.kidwatch.model.CallLog> {
        val lastSync = prefsRepo.getLastCallSyncTime()
        val calls = mutableListOf<com.dellvantix.kidwatch.model.CallLog>()

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                "${CallLog.Calls.DATE} > ?",
                arrayOf(lastSync.toString()),
                "${CallLog.Calls.DATE} DESC"
            ) ?: return calls

            cursor.use {
                while (it.moveToNext()) {
                    val number = it.getString(0) ?: ""
                    val name = it.getString(1) ?: ""
                    val typeInt = it.getInt(2)
                    val duration = it.getInt(3)
                    val date = it.getLong(4)

                    val type = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        else -> "UNKNOWN"
                    }

                    calls.add(
                        com.dellvantix.kidwatch.model.CallLog(
                            number = number,
                            contactName = name,
                            type = type,
                            duration = duration,
                            timestamp = date,
                            syncedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            if (calls.isNotEmpty()) {
                prefsRepo.setLastCallSyncTime(System.currentTimeMillis())
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }

        return calls
    }
}
