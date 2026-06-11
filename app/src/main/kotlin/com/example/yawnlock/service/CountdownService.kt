package com.example.yawnlock.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Stub for compile-time. Real implementation lands in Task 5.
 */
class CountdownService : Service() {
    companion object {
        const val ACTION_START = "com.example.yawnlock.START"
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
