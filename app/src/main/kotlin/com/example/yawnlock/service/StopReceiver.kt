package com.example.yawnlock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.yawnlock.YawnApplication

class StopReceiver : BroadcastReceiver() {
    companion object { const val ACTION_STOP = "com.example.yawnlock.STOP" }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            val app = context.applicationContext as YawnApplication
            app.timerRepository.stop()
            context.startService(
                Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_STOP),
            )
        }
    }
}
