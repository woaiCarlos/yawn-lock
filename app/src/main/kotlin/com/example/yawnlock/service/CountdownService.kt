package com.example.yawnlock.service

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.example.yawnlock.YawnApplication
import com.example.yawnlock.data.DeviceAdminReceiver
import com.example.yawnlock.data.NotificationCenter
import com.example.yawnlock.domain.TimerState
import com.example.yawnlock.domain.TimerStatus

class CountdownService : Service() {
    companion object {
        const val ACTION_START = "com.example.yawnlock.START"
        const val ACTION_STOP = "com.example.yawnlock.STOP"
        const val ACTION_PAUSE = "com.example.yawnlock.PAUSE"
        const val ACTION_RESUME = "com.example.yawnlock.RESUME"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val repo get() = (application as YawnApplication).timerRepository
    private var bubble: FloatingBubbleController? = null

    private val endRunnable = Runnable {
        val s = repo.state.value
        if (s.status !is TimerStatus.Counting) return@Runnable
        triggerLockNow()
    }

    private fun triggerLockNow() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            val fallback = Intent(this, LockedFallbackActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(fallback)
        }
        repo.onAlarmFired()
    }

    private fun scheduleEnd(durationMs: Long) {
        handler.removeCallbacks(endRunnable)
        if (durationMs <= 5L * 60L * 1000L) {
            // 短时长:进程内 Handler 调度,可靠不依赖 OS scheduler
            handler.postDelayed(endRunnable, durationMs)
        } else {
            // 长时长:AlarmManager(走原 scheduleAlarm 路径)
            val state = repo.state.value
            scheduleAlarm(state)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val state = repo.state.value
            if (!state.isActive) {
                stopSelf()
                return
            }
            if (state.status is TimerStatus.Counting) {
                repo.tick()
            }
            val refreshed = repo.state.value
            NotificationCenter.update(
                this@CountdownService,
                refreshed.remainingMs,
                refreshed.status is TimerStatus.Paused,
            )
            bubble?.updateTime(refreshed.remainingMs)
            if (refreshed.status is TimerStatus.Finished) {
                stopSelf()
                return
            }
            handler.postDelayed(this, 100L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationCenter.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> { handler.removeCallbacks(endRunnable); cancelAlarm(); repo.stop(); stopSelf() }
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
        }
        return START_STICKY
    }

    private fun handleStart() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return
        startForegroundCompat(state)
        scheduleEnd(state.remainingMs)
        try {
            ensureBubble()
        } catch (e: Exception) {
            android.util.Log.w("CountdownService", "bubble show failed; ticker continues", e)
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun handlePause() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return
        repo.pause()
        handler.removeCallbacks(endRunnable)
        cancelAlarm()
        NotificationCenter.update(this, state.remainingMs, isPaused = true)
    }

    private fun handleResume() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Paused) return
        repo.resume()
        scheduleEnd(state.remainingMs)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun startForegroundCompat(state: TimerState) {
        val notif = NotificationCenter.build(this, state.remainingMs, isPaused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationCenter.NOTIF_ID,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationCenter.NOTIF_ID, notif)
        }
    }

    private fun scheduleAlarm(state: TimerState) {
        val triggerAt = SystemClock.elapsedRealtime() + state.remainingMs
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = android.app.PendingIntent.getBroadcast(
            this, 0,
            Intent(this, LockReceiver::class.java).setAction(LockReceiver.ACTION_FIRE),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt, pi,
                )
            } else {
                am.setAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt, pi,
                )
            }
        } catch (e: SecurityException) {
            // 降级
            am.setAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt, pi,
            )
        }
    }

    private fun cancelAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = android.app.PendingIntent.getBroadcast(
            this, 0,
            Intent(this, LockReceiver::class.java).setAction(LockReceiver.ACTION_FIRE),
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(endRunnable)
        bubble?.hide()
        bubble = null
        NotificationCenter.cancel(this)
        super.onDestroy()
    }

    private fun ensureBubble() {
        if (bubble == null) {
            bubble = FloatingBubbleController(this).also { it.show() }
        }
    }
}
