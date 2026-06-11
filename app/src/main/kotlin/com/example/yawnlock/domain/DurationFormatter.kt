package com.example.yawnlock.domain

import kotlin.math.floor

object DurationFormatter {
    fun toMmSs(remainingMs: Long): String {
        val total = (remainingMs / 1000L).coerceAtLeast(0L)
        val m = floor(total / 60.0).toLong()
        val s = total % 60L
        return "%02d:%02d".format(m, s)
    }

    fun minutesToMs(minutes: Double): Long = (minutes * 60_000L).toLong()
}
