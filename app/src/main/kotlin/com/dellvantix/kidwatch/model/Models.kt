package com.dellvantix.kidwatch.model

// ─── SMS Message ──────────────────────────────────────────────────────────────
data class SmsMessage(
    val id: String = "",
    val address: String = "",       // Phone number
    val contactName: String = "",   // Resolved from contacts (optional)
    val body: String = "",
    val type: String = "",          // INCOMING | OUTGOING
    val timestamp: Long = 0L,
    val syncedAt: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "address" to address,
        "contactName" to contactName,
        "body" to body,
        "type" to type,
        "timestamp" to timestamp,
        "syncedAt" to syncedAt
    )
}

// ─── Call Log ─────────────────────────────────────────────────────────────────
data class CallLog(
    val id: String = "",
    val number: String = "",
    val contactName: String = "",
    val type: String = "",          // INCOMING | OUTGOING | MISSED
    val duration: Int = 0,          // seconds
    val timestamp: Long = 0L,
    val syncedAt: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "number" to number,
        "contactName" to contactName,
        "type" to type,
        "duration" to duration,
        "timestamp" to timestamp,
        "syncedAt" to syncedAt
    )
}

// ─── Screenshot Record ────────────────────────────────────────────────────────
data class ScreenshotRecord(
    val id: String = "",
    val storageUrl: String = "",
    val capturedAt: Long = 0L,
    val fileSizeBytes: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "storageUrl" to storageUrl,
        "capturedAt" to capturedAt,
        "fileSizeBytes" to fileSizeBytes
    )
}

// ─── Device Info ──────────────────────────────────────────────────────────────
data class DeviceInfo(
    val deviceId: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val lastSeen: Long = 0L,
    val monitoringActive: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "deviceId" to deviceId,
        "deviceModel" to deviceModel,
        "androidVersion" to androidVersion,
        "lastSeen" to lastSeen,
        "monitoringActive" to monitoringActive
    )
}
