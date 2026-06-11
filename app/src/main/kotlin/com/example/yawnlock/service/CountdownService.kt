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
import android.provider.Settings
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
        private const val TAG = "CountdownService"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val repo get() = (application as YawnApplication).timerRepository
    private val bubble get() = (application as YawnApplication).bubbleController

    private val endRunnable = Runnable {
        val s = repo.state.value
        if (s.status !is TimerStatus.Counting) return@Runnable
        triggerLockNow()
    }

    private fun triggerLockNow() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        val isAdmin = dpm.isAdminActive(admin)
        android.util.Log.d(TAG, "triggerLockNow: isAdminActive=$isAdmin")
        if (isAdmin) {
            try {
                dpm.lockNow()
                android.util.Log.d(TAG, "dpm.lockNow() called")
            } catch (e: SecurityException) {
                android.util.Log.e(TAG, "dpm.lockNow() threw SecurityException", e)
            }
        } else {
            android.util.Log.w(TAG, "falling back to LockedFallbackActivity")
            val fallback = Intent(this, LockedFallbackActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(fallback)
        }
        repo.onAlarmFired()
        // 锁屏瞬间关闭气泡 —— 防止锁屏后 MainActivity.onStop 触发 ProcessLifecycle ON_STOP
        // 把它重新显示在锁屏界面上
        bubble.hide()
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
            bubble.updateTime(refreshed.remainingMs)
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
        // 注意:气泡的 ProcessLifecycle 监听在 YawnApplication.onCreate 中注册
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

        // 诊断:start 时的授权状态(用户可在 adb logcat 中验证)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        val isAdmin = dpm.isAdminActive(admin)
        val canOverlay = Settings.canDrawOverlays(this)
        android.util.Log.d(TAG, "handleStart: isAdminActive=$isAdmin, canDrawOverlays=$canOverlay, status=${state.status}, remainingMs=${state.remainingMs}")

        if (!isAdmin) {
            android.util.Log.w(TAG, "device admin NOT active; lockNow() will fall back to LockedFallbackActivity")
            NotificationCenter.showAdminMissingWarning(this)
        }
        if (!canOverlay) {
            android.util.Log.w(TAG, "overlay permission NOT granted; bubble may not show")
        }

        startForegroundCompat(state)
        scheduleEnd(state.remainingMs)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun handlePause() {
        // 幂等:无论 state 是不是已经是 Paused,都做清理
        // (气泡先调 repo.pause() 再发 ACTION_PAUSE,service 看到 state=Paused 也要清 endRunnable / cancelAlarm)
        val state = repo.state.value
        if (state.status is TimerStatus.Counting) {
            repo.pause()
        }
        handler.removeCallbacks(endRunnable)
        cancelAlarm()
        NotificationCenter.update(this, state.remainingMs, isPaused = state.status is TimerStatus.Paused)
    }

    private fun handleResume() {
        // 幂等:无论 state 是不是已经是 Counting,都重新调度
        // (气泡先调 repo.resume() 再发 ACTION_RESUME,service 也要重新 scheduleEnd 和 ticker)
        val state = repo.state.value
        if (state.status is TimerStatus.Paused) {
            repo.resume()
        }
        scheduleEnd(state.remainingMs)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        NotificationCenter.update(this, state.remainingMs, isPaused = false)
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
        // 不在 Service.onDestroy 清理 bubble(由 Application 持有单例,ProcessLifecycle 决定显示)
        NotificationCenter.cancel(this)
        super.onDestroy()
    }
}
