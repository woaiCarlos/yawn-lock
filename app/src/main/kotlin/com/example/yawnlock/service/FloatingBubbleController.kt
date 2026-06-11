package com.example.yawnlock.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.example.yawnlock.R
import com.example.yawnlock.YawnApplication
import com.example.yawnlock.domain.DurationFormatter
import com.example.yawnlock.domain.TimerStatus

class FloatingBubbleController(private val context: Context) {
    companion object {
        private const val TAG = "FloatingBubble"
        private const val EXPANDED_WIDTH_DP = 200
        private const val COLLAPSED_WIDTH_DP = 36
        private const val EDGE_MARGIN_DP = 6
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val bubbleView: View =
        LayoutInflater.from(context).inflate(R.layout.floating_bubble, null)
    private val collapsedView: View = bubbleView.findViewById(R.id.bubble_collapsed)
    private val expandedView: View = bubbleView.findViewById(R.id.bubble_expanded)
    private val timeView: TextView = bubbleView.findViewById(R.id.bubble_time)
    private val subtitleView: TextView = bubbleView.findViewById(R.id.bubble_subtitle)
    private val pauseBtn: Button = bubbleView.findViewById(R.id.bubble_pause)
    private val stopBtn: Button = bubbleView.findViewById(R.id.bubble_stop)

    private val params = WindowManager.LayoutParams(
        dp(EXPANDED_WIDTH_DP), WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(40)
        y = dp(300)
    }

    private var startX = 0f
    private var startY = 0f
    private var startParamsX = 0
    private var startParamsY = 0
    private var moved = false
    private var collapsed = false
    private var attached = false

    init {
        // 默认展开
        expandedView.visibility = View.VISIBLE
        collapsedView.visibility = View.GONE
        bubbleView.setOnTouchListener { _, ev -> handleTouch(ev) }
        pauseBtn.setOnClickListener { togglePause() }
        stopBtn.setOnClickListener { stopCountdown() }
    }

    fun show() {
        if (attached) {
            Log.d(TAG, "show: already attached, skip")
            return
        }
        // 根据当前 collapsed 状态调整初始 width
        params.width = if (collapsed) dp(COLLAPSED_WIDTH_DP) else dp(EXPANDED_WIDTH_DP)
        try {
            wm.addView(bubbleView, params)
            attached = true
        } catch (e: Exception) {
            Log.e(TAG, "addView failed; bubble will not show", e)
            return
        }
        val s = (context.applicationContext as YawnApplication).timerRepository.state.value
        timeView.text = DurationFormatter.toMmSs(s.remainingMs)
        updateStatus(s.status is TimerStatus.Paused)
    }

    fun hide() {
        if (!attached) return
        try {
            wm.removeView(bubbleView)
            attached = false
        } catch (_: Exception) {}
    }

    fun updateTime(ms: Long) {
        timeView.text = DurationFormatter.toMmSs(ms)
    }

    fun updateStatus(isPaused: Boolean) {
        subtitleView.text = if (isPaused) "已暂停" else "计时中"
        pauseBtn.text = if (isPaused) "继续" else "暂停"
    }

    private fun togglePause() {
        val repo = (context.applicationContext as YawnApplication).timerRepository
        if (repo.state.value.status is TimerStatus.Paused) {
            repo.resume()
            // 自己立即更新 UI:不等 service(否则 service 看到 state 已是 Counting 还会跑 idempotent cleanup,
            // 但 in-app 路径在 service 那更新 UI 会变成双重来源不一致)
            updateStatus(isPaused = false)
            context.startService(
                Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_RESUME)
            )
        } else {
            repo.pause()
            updateStatus(isPaused = true)
            context.startService(
                Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_PAUSE)
            )
        }
    }

    private fun stopCountdown() {
        val repo = (context.applicationContext as YawnApplication).timerRepository
        repo.stop()
        // 关闭悬浮窗:ProcessLifecycle 还停在 ON_STOP 状态,不会自动关气泡,需要手动 hide
        hide()
        context.startService(
            Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_STOP)
        )
    }

    private fun handleTouch(ev: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX; startY = ev.rawY
                startParamsX = params.x; startParamsY = params.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > slop) moved = true
                val displayMetrics = context.resources.displayMetrics
                val maxX = displayMetrics.widthPixels - bubbleView.width
                val maxY = displayMetrics.heightPixels - bubbleView.height
                params.x = (startParamsX + dx.toInt()).coerceIn(0, maxX)
                params.y = (startParamsY + dy.toInt()).coerceIn(0, maxY)
                try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
            }
            MotionEvent.ACTION_UP -> {
                if (moved) {
                    // 拖动后:气泡边缘只要顶到屏幕边缘(<= 0 或 >= screenWidth)就折叠
                    val displayMetrics = context.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val bubbleLeft = params.x
                    val bubbleRight = params.x + bubbleView.width
                    if (bubbleLeft <= 0) {
                        // 气泡左边缘顶到/超出屏幕左边缘 → 折叠到左
                        collapseToLeft()
                    } else if (bubbleRight >= screenWidth) {
                        // 气泡右边缘顶到/超出屏幕右边缘 → 折叠到右
                        collapseToRight()
                    }
                    // 否则:停在拖到的位置,保持展开
                } else if (collapsed) {
                    // 没移动 + 当前是折叠态 → 展开
                    expand()
                }
                // 没移动 + 展开态:什么都不做(避免误触收起)
            }
        }
        return true
    }

    private fun collapseToLeft() {
        collapsed = true
        expandedView.visibility = View.GONE
        collapsedView.visibility = View.VISIBLE
        params.width = dp(COLLAPSED_WIDTH_DP)
        params.x = dp(EDGE_MARGIN_DP)
        try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
    }

    private fun collapseToRight() {
        collapsed = true
        expandedView.visibility = View.GONE
        collapsedView.visibility = View.VISIBLE
        val displayMetrics = context.resources.displayMetrics
        params.width = dp(COLLAPSED_WIDTH_DP)
        // 右侧贴边:右边距 EDGE_MARGIN_DP
        params.x = displayMetrics.widthPixels - dp(COLLAPSED_WIDTH_DP) - dp(EDGE_MARGIN_DP)
        try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
    }

    private fun expand() {
        collapsed = false
        expandedView.visibility = View.VISIBLE
        collapsedView.visibility = View.GONE
        val displayMetrics = context.resources.displayMetrics
        params.width = dp(EXPANDED_WIDTH_DP)
        // 展开默认位置:左侧 40dp,但不能超出屏幕
        val maxX = (displayMetrics.widthPixels - dp(EXPANDED_WIDTH_DP)).coerceAtLeast(0)
        params.x = dp(40).coerceAtMost(maxX)
        try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}
