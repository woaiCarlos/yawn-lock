package com.example.yawnlock.domain

sealed interface TimerStatus {
    data object Idle : TimerStatus
    data object Counting : TimerStatus
    data object Paused : TimerStatus
    data object Finished : TimerStatus
}

data class TimerState(
    val status: TimerStatus = TimerStatus.Idle,
    val durationMs: Long = 0L,
    val remainingMs: Long = 0L,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f else 1f - (remainingMs.toFloat() / durationMs)

    val isActive: Boolean
        get() = status is TimerStatus.Counting || status is TimerStatus.Paused
}
