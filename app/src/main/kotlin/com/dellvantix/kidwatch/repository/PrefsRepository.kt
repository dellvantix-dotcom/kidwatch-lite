package com.dellvantix.kidwatch.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kidwatch_prefs")

@Singleton
class PrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.dataStore

    companion object {
        val KEY_CONSENT = booleanPreferencesKey("consent_given")
        val KEY_MONITORING = booleanPreferencesKey("monitoring_active")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_PARENT_PIN = stringPreferencesKey("parent_pin")
        val KEY_SCREENSHOT_INTERVAL = longPreferencesKey("screenshot_interval_ms")
        val KEY_SYNC_INTERVAL = longPreferencesKey("sync_interval_ms")
        val KEY_LAST_SMS_SYNC = longPreferencesKey("last_sms_sync")
        val KEY_LAST_CALL_SYNC = longPreferencesKey("last_call_sync")
    }

    suspend fun isConsentGiven(): Boolean =
        store.data.map { it[KEY_CONSENT] ?: false }.first()

    suspend fun setConsentGiven(value: Boolean) =
        store.edit { it[KEY_CONSENT] = value }

    suspend fun isMonitoringActive(): Boolean =
        store.data.map { it[KEY_MONITORING] ?: false }.first()

    suspend fun setMonitoringActive(value: Boolean) =
        store.edit { it[KEY_MONITORING] = value }

    suspend fun getDeviceId(): String {
        val existing = store.data.map { it[KEY_DEVICE_ID] }.first()
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString().take(8).uppercase()
        store.edit { it[KEY_DEVICE_ID] = newId }
        return newId
    }

    suspend fun getParentPin(): String? =
        store.data.map { it[KEY_PARENT_PIN] }.first()

    suspend fun saveParentPin(pin: String) =
        store.edit { it[KEY_PARENT_PIN] = pin }

    suspend fun getScreenshotIntervalMs(): Long =
        store.data.map { it[KEY_SCREENSHOT_INTERVAL] ?: 60_000L }.first()

    suspend fun getSyncIntervalMs(): Long =
        store.data.map { it[KEY_SYNC_INTERVAL] ?: 60_000L }.first()

    suspend fun getLastSmsSyncTime(): Long =
        store.data.map { it[KEY_LAST_SMS_SYNC] ?: 0L }.first()

    suspend fun setLastSmsSyncTime(ts: Long) =
        store.edit { it[KEY_LAST_SMS_SYNC] = ts }

    suspend fun getLastCallSyncTime(): Long =
        store.data.map { it[KEY_LAST_CALL_SYNC] ?: 0L }.first()

    suspend fun setLastCallSyncTime(ts: Long) =
        store.edit { it[KEY_LAST_CALL_SYNC] = ts }
}
