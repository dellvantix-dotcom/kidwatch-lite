package com.dellvantix.kidwatch.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.dellvantix.kidwatch.service.MonitorService

// ─── Boot Receiver ────────────────────────────────────────────────────────────
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val serviceIntent = Intent(context, MonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}

// ─── Device Admin Receiver ────────────────────────────────────────────────────
// Prevents app from being uninstalled without first disabling admin.
// Disabling admin requires the parent PIN set during setup.
class KidWatchAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "KidWatch device protection enabled.", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "To disable KidWatch, you need the parent PIN."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "KidWatch device protection disabled.", Toast.LENGTH_SHORT).show()
    }
}
