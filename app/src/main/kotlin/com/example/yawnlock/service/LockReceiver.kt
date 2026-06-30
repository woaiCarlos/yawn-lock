package com.example.yawnlock.service

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.yawnlock.YawnApplication
import com.example.yawnlock.data.DeviceAdminReceiver

class LockReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FIRE = "com.example.yawnlock.FIRE"
        private const val TAG = "LockReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d(TAG, "onReceive: action=${intent.action}")
        if (intent.action != ACTION_FIRE) return
        val app = context.applicationContext as YawnApplication
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            android.util.Log.d(TAG, "lockNow() invoked")
        } else {
            android.util.Log.w(TAG, "admin not active; starting LockedFallbackActivity")
            val fallback = Intent(context, LockedFallbackActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
        app.timerRepository.onAlarmFired()
        // 长时长(> 5 分钟)的锁屏触发路径走 AlarmManager → LockReceiver,与短时长的
        // CountdownService.triggerLockNow 对称,这里必须也 hide 一次,否则浮窗会跨过锁屏界面
        // 一直挂着,直到用户解锁回 app 才会被 ON_START 生命周期收尾。
        app.bubbleController.hide()
    }
}
