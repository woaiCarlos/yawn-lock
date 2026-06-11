package com.example.yawnlock.service

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.yawnlock.YawnApplication
import com.example.yawnlock.data.DeviceAdminReceiver

class LockReceiver : BroadcastReceiver() {
    companion object { const val ACTION_FIRE = "com.example.yawnlock.FIRE" }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val app = context.applicationContext as YawnApplication
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            // 降级:全屏 Activity 提示
            val fallback = Intent(context, LockedFallbackActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
        app.timerRepository.onAlarmFired()
    }
}
