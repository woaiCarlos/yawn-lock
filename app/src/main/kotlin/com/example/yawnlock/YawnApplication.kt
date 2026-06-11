package com.example.yawnlock

import android.app.Application
import com.example.yawnlock.domain.TimerRepository

class YawnApplication : Application() {
    val timerRepository: TimerRepository by lazy { TimerRepository() }
}
