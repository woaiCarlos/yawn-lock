package com.example.yawnlock.domain

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TimerRepository {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var deadlineElapsed: Long = 0L  // SystemClock.elapsedRealtime() when alarm should fire

    fun start(durationMs: Long) {
        val now = SystemClock.elapsedRealtime()
        deadlineElapsed = now + durationMs
        _state.value = TimerState(
            status = TimerStatus.Counting,
            durationMs = durationMs,
            remainingMs = durationMs,
        )
    }

    fun pause() {
        val current = _state.value
        if (current.status !is TimerStatus.Counting) return
        val now = SystemClock.elapsedRealtime()
        val newRemaining = (deadlineElapsed - now).coerceAtLeast(0L)
        _state.update { it.copy(status = TimerStatus.Paused, remainingMs = newRemaining) }
    }

    fun resume() {
        val current = _state.value
        if (current.status !is TimerStatus.Paused) return
        val now = SystemClock.elapsedRealtime()
        deadlineElapsed = now + current.remainingMs
        _state.update { it.copy(status = TimerStatus.Counting) }
    }

    fun stop() {
        _state.value = TimerState()
    }

    fun tick() {
        val current = _state.value
        if (current.status !is TimerStatus.Counting) return
        val now = SystemClock.elapsedRealtime()
        val newRemaining = (deadlineElapsed - now).coerceAtLeast(0L)
        _state.update { it.copy(remainingMs = newRemaining) }
    }

    fun preview(durationMs: Long) {
        val current = _state.value
        if (current.isActive) return
        _state.value = current.copy(
            durationMs = durationMs,
            remainingMs = durationMs,
        )
    }

    /** Called by LockReceiver when alarm fires. */
    fun onAlarmFired() {
        _state.value = _state.value.copy(status = TimerStatus.Finished, remainingMs = 0L)
    }

    /** Called by Service when device admin is revoked mid-countdown. */
    fun onDeviceAdminRevoked() {
        _state.value = TimerState()
    }
}
