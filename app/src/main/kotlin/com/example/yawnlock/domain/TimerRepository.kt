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
        // 保留 durationMs:用户停止后想再开一次,滑块位置 + 「开始计时」按钮 enabled,不用先拖滑块
        val current = _state.value
        _state.value = TimerState(
            status = TimerStatus.Idle,
            durationMs = current.durationMs,
            remainingMs = current.durationMs,
        )
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
        // 从 Finished 状态切到 Idle:用户滑动滑块调整时间意味着「我要开始新的一次倒计时」
        // 如果不重置,vm.start() 会因为 status=Finished(不是 Idle)而 return
        val newStatus = if (current.status is TimerStatus.Finished) TimerStatus.Idle else current.status
        _state.value = current.copy(
            status = newStatus,
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
