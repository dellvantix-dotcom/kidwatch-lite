package com.dellvantix.kidwatch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.dellvantix.kidwatch.repository.FirebaseRepository
import com.dellvantix.kidwatch.model.SmsMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var firebaseRepo: FirebaseRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val pending = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val smsModels = messages.map { sms ->
                        SmsMessage(
                            address = sms.originatingAddress ?: "Unknown",
                            body = sms.messageBody,
                            type = "INCOMING",
                            timestamp = sms.timestampMillis,
                            syncedAt = System.currentTimeMillis()
                        )
                    }
                    firebaseRepo.uploadSmsMessages(smsModels)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
