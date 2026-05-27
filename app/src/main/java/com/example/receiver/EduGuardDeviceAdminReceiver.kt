package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EduGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun logAdminEvent(context: Context, details: String, severity: String) {
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                db.mdmDao().insertLog(
                    ActivityLog(
                        target = "DeviceAdmin",
                        activityType = "SYSTEM_ALERT",
                        details = details,
                        severity = severity
                    )
                )
            } catch (e: Exception) {
                Log.e("EduGuardAdmin", "Failed to log admin event", e)
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "EduGuard Device Administration Activated", Toast.LENGTH_SHORT).show()
        logAdminEvent(context, "School administration system activated on this device", "INFO")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "EduGuard Device Administration Deactivated!", Toast.LENGTH_LONG).show()
        logAdminEvent(context, "WARNING: School administration system deactivated by the user!", "WARNING")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        logAdminEvent(context, "Failed system passcode unlock attempt detected", "WARNING")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        logAdminEvent(context, "User successfully unlocked the device", "INFO")
    }
}
