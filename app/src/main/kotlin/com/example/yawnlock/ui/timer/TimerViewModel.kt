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
        val clamped = s.coerceIn(5L, 7200L)
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
