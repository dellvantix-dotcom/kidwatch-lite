package com.dellvantix.kidwatch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.dellvantix.kidwatch.model.CallLog
import com.dellvantix.kidwatch.repository.FirebaseRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallReceiver : BroadcastReceiver() {

    @Inject lateinit var firebaseRepo: FirebaseRepository

    // Track call start for duration calculation
    companion object {
        private var callStartTime: Long = 0L
        private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
        private var lastNumber: String = ""
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?: intent.getStringExtra("android.intent.extra.PHONE_NUMBER")
            ?: lastNumber

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                lastState = state
                lastNumber = number
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                callStartTime = System.currentTimeMillis()
                if (lastState == TelephonyManager.EXTRA_STATE_IDLE) {
                    lastNumber = number  // Outgoing call
                }
                lastState = state
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val duration = if (callStartTime > 0) {
                    ((System.currentTimeMillis() - callStartTime) / 1000).toInt()
                } else 0

                val callType = when {
                    lastState == TelephonyManager.EXTRA_STATE_RINGING && callStartTime == 0L -> "MISSED"
                    lastState == TelephonyManager.EXTRA_STATE_RINGING -> "INCOMING"
                    else -> "OUTGOING"
                }

                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val log = CallLog(
                            number = lastNumber,
                            type = callType,
                            duration = duration,
                            timestamp = System.currentTimeMillis(),
                            syncedAt = System.currentTimeMillis()
                        )
                        firebaseRepo.uploadCallLog(log)
                    } finally {
                        pending.finish()
                    }
                }

                // Reset
                callStartTime = 0L
                lastState = state
                lastNumber = ""
            }
        }
    }
}
