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
        // 回到 Idle 但保留 durationMs,与 onAlarmFired() 落地的 Finished 对齐——
        // Stop 后用户点 Start 应该能直接用上次设的时长重启动,不需要重新选时间。
        val cur = _state.value
        _state.value = TimerState(
            status = TimerStatus.Idle,
            durationMs = cur.durationMs,
            remainingMs = cur.durationMs,
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
        val now = SystemClock.elapsedRealtime()
        // 1.0.3 起:任何状态下的 preview 都直接重置为 Idle+newDuration+newDuration。
        // 不再「Counting 状态保留、Paused 状态保留、Finished 才转 Idle」的分层逻辑。
        // 用户实测反馈:暂停中调时间、或正在倒计时调时间,都应该「清掉旧状态、准备好
        // 用新时长开始」,而不是「timer 自动按新值继续」或「暂停中改完时间还得手动
        // resume」。任何「改主意」都应该把之前的 counting/paused 状态彻底清掉,要求
        // 用户重新点 Start 才计时。
        _state.value = TimerState(
            status = TimerStatus.Idle,
            durationMs = durationMs,
            remainingMs = durationMs,
        )
        // 不再同步 deadlineElapsed:preview 之后 status=Idle,tick() 不会跑;下次 start()
        // 会重新设 deadlineElapsed。保留旧值没意义。
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
