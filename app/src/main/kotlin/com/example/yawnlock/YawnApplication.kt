package com.example.yawnlock

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.yawnlock.domain.TimerRepository
import com.example.yawnlock.service.FloatingBubbleController

class YawnApplication : Application() {
    val timerRepository: TimerRepository by lazy { TimerRepository() }
    val bubbleController: FloatingBubbleController by lazy { FloatingBubbleController(this) }

    private val appLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                // app 进后台:仅在倒计时还在跑时显示气泡
                if (timerRepository.state.value.isActive) {
                    bubbleController.show()
                }
            }
            Lifecycle.Event.ON_START -> {
                // app 回前台:隐藏气泡
                bubbleController.hide()
            }
            else -> {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 必须在 Application.onCreate 注册,避免 Service 启动较晚错过 ON_STOP
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        // 预热气泡 view(触发 inflate + findViewById),首次 show() 不再卡顿
        bubbleController
    }
}
