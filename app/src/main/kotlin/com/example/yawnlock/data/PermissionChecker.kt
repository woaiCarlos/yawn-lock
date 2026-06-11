package com.example.yawnlock.data

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

data class PermissionState(
    val overlayGranted: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val notificationGranted: Boolean = true,
) {
    val canStartCountdown: Boolean get() = overlayGranted && deviceAdminActive
}

object PermissionChecker {
    fun check(context: Context): PermissionState = PermissionState(
        overlayGranted = Settings.canDrawOverlays(context),
        deviceAdminActive = isDeviceAdminActive(context),
        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .areNotificationsEnabled()
        } else true,
    )

    private fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, DeviceAdminReceiver::class.java)
        return dpm.isAdminActive(component)
    }
}
