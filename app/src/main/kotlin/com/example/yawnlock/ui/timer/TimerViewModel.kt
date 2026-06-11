package com.example.yawnlock.ui.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.yawnlock.YawnApplication
import com.example.yawnlock.data.PermissionChecker
import com.example.yawnlock.data.PermissionState
import com.example.yawnlock.domain.TimerRepository
import com.example.yawnlock.domain.TimerState
import kotlinx.coroutines.flow.StateFlow

class TimerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: TimerRepository = (app as YawnApplication).timerRepository
    val state: StateFlow<TimerState> = repo.state

    fun setSeconds(s: Long) {
        if (state.value.isActive) return
        // 24h 上限(86400s):允许自定义 wheel 选 0..24 小时,不再被自动 clamp 回 2 小时
        // Preset chip 都在 2h 内,不受影响
        val clamped = s.coerceIn(5L, 86400L)
        repo.preview(clamped * 1000L)
    }

    fun checkPermissions(): PermissionState = PermissionChecker.check(getApplication())

    fun start() {
        val s = state.value
        if (s.status !is com.example.yawnlock.domain.TimerStatus.Idle) return
        if (s.durationMs <= 0L) return
        repo.start(s.durationMs)
    }

    fun pause() = repo.pause()
    fun resume() = repo.resume()
    fun stop() = repo.stop()
}
