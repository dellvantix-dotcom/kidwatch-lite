package com.dellvantix.kidwatch.repository

import android.util.Log
import com.dellvantix.kidwatch.model.CallLog
import com.dellvantix.kidwatch.model.DeviceInfo
import com.dellvantix.kidwatch.model.ScreenshotRecord
import com.dellvantix.kidwatch.model.SmsMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val prefsRepo: PrefsRepository
) {

    private val TAG = "FirebaseRepo"

    // ─── Firestore path: /devices/{deviceId}/... ──────────────────
    private suspend fun deviceId() = prefsRepo.getDeviceId()

    // ─── Device Heartbeat ─────────────────────────────────────────
    suspend fun updateHeartbeat() {
        val id = deviceId()
        val info = DeviceInfo(
            deviceId = id,
            deviceModel = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            lastSeen = System.currentTimeMillis()
        )
        firestore.collection("devices")
            .document(id)
            .set(info.toMap(), SetOptions.merge())
            .await()
    }

    // ─── SMS Upload ───────────────────────────────────────────────
    suspend fun uploadSmsMessages(messages: List<SmsMessage>) {
        val id = deviceId()
        val batch = firestore.batch()
        val col = firestore.collection("devices").document(id).collection("sms")

        messages.forEach { msg ->
            val docId = msg.timestamp.toString() + "_" + msg.address.hashCode()
            val ref = col.document(docId)
            batch.set(ref, msg.toMap())
        }

        try {
            batch.commit().await()
            Log.d(TAG, "Uploaded ${messages.size} SMS messages")
        } catch (e: Exception) {
            Log.e(TAG, "SMS upload failed: ${e.message}")
        }
    }

    // ─── Call Log Upload ──────────────────────────────────────────
    suspend fun uploadCallLog(log: CallLog) {
        val id = deviceId()
        val docId = log.timestamp.toString() + "_" + log.number.hashCode()
        try {
            firestore.collection("devices")
                .document(id)
                .collection("calls")
                .document(docId)
                .set(log.toMap())
                .await()
            Log.d(TAG, "Call log uploaded: ${log.type} ${log.number}")
        } catch (e: Exception) {
            Log.e(TAG, "Call log upload failed: ${e.message}")
        }
    }

    suspend fun uploadCallLogs(logs: List<CallLog>) {
        logs.forEach { uploadCallLog(it) }
    }

    // ─── Screenshot Upload ────────────────────────────────────────
    suspend fun uploadScreenshot(jpegBytes: ByteArray) {
        val id = deviceId()
        val timestamp = System.currentTimeMillis()
        val fileName = "screenshots/$id/$timestamp.jpg"

        try {
            // Upload to Firebase Storage
            val storageRef = storage.reference.child(fileName)
            storageRef.putBytes(jpegBytes).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Save metadata to Firestore
            val record = ScreenshotRecord(
                storageUrl = downloadUrl,
                capturedAt = timestamp,
                fileSizeBytes = jpegBytes.size.toLong()
            )

            firestore.collection("devices")
                .document(id)
                .collection("screenshots")
                .document(timestamp.toString())
                .set(record.toMap())
                .await()

            Log.d(TAG, "Screenshot uploaded: ${jpegBytes.size / 1024}KB")
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot upload failed: ${e.message}")
        }
    }
}
