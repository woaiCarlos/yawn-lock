package com.example.yawnlock.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import kotlin.math.abs

class FloatingBubbleController(private val context: Context) {
    companion object {
        private const val TAG = "FloatingBubble"
        // 视觉尺寸(dp)
        private const val PINNED_WIDTH_DP = 3
        private const val PINNED_HEIGHT_DP = 60
        private const val COLLAPSED_WIDTH_DP = 36
        private const val EXPANDED_WIDTH_DP = 200
        // EXPANDED bubble 的实际 wrap_content 高度(用作 maxY fallback,
        // 真实高度要 bubbleView.height 取 — 它在 WM layout 后才有值)
        private const val EXPANDED_FALLBACK_HEIGHT_DP = 140

        // 边距
        private const val EDGE_MARGIN_DP = 6

        // 自动收起:CIRCLE 状态静默 N 毫秒后回 LINE
        private const val AUTO_COLLAPSE_DELAY_MS = 2_500L
    }

    private enum class VisualState { LINE, CIRCLE, EXPANDED }

    private enum class SnapSide { LEFT, RIGHT }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val bubbleView: View =
        LayoutInflater.from(context).inflate(R.layout.floating_bubble, null)
    private val pinnedView: View = bubbleView.findViewById(R.id.bubble_pinned)
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

    private val handler = Handler(Looper.getMainLooper())
    private val autoCollapseRunnable = Runnable {
        Log.d(TAG, "auto-collapse timer fired, snap to LINE")
        setVisualState(VisualState.LINE)
    }
    private var autoCollapseScheduled = false

    private var startX = 0f
    private var startY = 0f
    private var startParamsX = 0
    private var startParamsY = 0
    private var moved = false
    private var visualState: VisualState = VisualState.EXPANDED
    private var lastSide: SnapSide = SnapSide.LEFT
    private var attached = false

    init {
        // 默认展开:与初始化 visualState = EXPANDED 一致
        applyVisibilityForState()
        bubbleView.setOnTouchListener { _, ev -> handleTouch(ev) }
        pauseBtn.setOnClickListener { togglePause() }
        stopBtn.setOnClickListener { stopCountdown() }
    }

    fun show() {
        if (attached) {
            Log.d(TAG, "show: already attached, skip")
            return
        }
        // 根据当前 visualState 调整初始 width,确保 hide/show 切换不破坏视觉态
        params.width = widthForState(visualState)
        try {
            wm.addView(bubbleView, params)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed; bubble will not show", e)
            return
        }
        attached = true
        val s = (context.applicationContext as YawnApplication).timerRepository.state.value
        timeView.text = DurationFormatter.toMmSs(s.remainingMs)
        updateStatus(s.status is TimerStatus.Paused)
        // 防止 hide/show 边界上挂着 stale timer 在下一拍秒收
        refreshAutoCollapseSchedule()
    }

    fun hide() {
        if (!attached) return
        // 关键:无论 wm.removeView 抛不抛,attached 必须无条件重置。
        // 否则一旦 removeView 失败(overlay 权限抖动 / 视图已 detach),
        // 后续 show() 全部 if (attached) return,气泡永远不再出现。
        try {
            wm.removeView(bubbleView)
        } catch (e: Exception) {
            Log.w(TAG, "wm.removeView threw, but resetting attached anyway", e)
        } finally {
            attached = false
        }
        // 隐藏期间挂着 auto-collapse timer 没意义,清掉
        cancelAutoCollapse()
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
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        // 拖动期间 params.width 不会变(params.x / y 才是被改的),所以这里取 params.width
        // 比读 bubbleView.width 更准(bubbleView.width 在状态切换后会有 1 frame 的 WM layout 延迟)。
        val currentWidth = params.width
        // bubbleView.height 是 wrap_content 实际渲染高度(EXPANDED ~140dp, CIRCLE 36dp, LINE 60dp)
        // bubbleView.height 在 WM layout 完成后才被赋值,首次 ACTION_DOWN 取不到,
        // 用 dp(EXPANDED_FALLBACK_HEIGHT_DP) 作为默认上限以避免首帧 maxY 爆掉。
        val currentHeight = bubbleView.height.takeIf { it > 0 } ?: when (visualState) {
            VisualState.LINE -> dp(PINNED_HEIGHT_DP)
            VisualState.CIRCLE -> dp(COLLAPSED_WIDTH_DP)
            VisualState.EXPANDED -> dp(EXPANDED_FALLBACK_HEIGHT_DP)
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX; startY = ev.rawY
                startParamsX = params.x; startParamsY = params.y
                moved = false
                // 任何开始触摸:取消已挂的 auto-collapse,避免「刚点就被收」
                cancelAutoCollapse()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (abs(dx) + abs(dy) > slop) moved = true
                val maxX = (screenWidth - currentWidth).coerceAtLeast(0)
                val maxY = (screenHeight - currentHeight).coerceAtLeast(0)
                params.x = (startParamsX + dx.toInt()).coerceIn(0, maxX)
                params.y = (startParamsY + dy.toInt()).coerceIn(0, maxY)
                try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
            }
            MotionEvent.ACTION_UP -> {
                if (moved) {
                    // 拖动后:只在「实际贴边」才吸到 CIRCLE,其他任何位置都落 EXPANDED。
                    // 之前有「屏幕 30% 区域就吸边」的兜底分支,实测 30% 在 1080dp 屏 = 324dp
                    // 太宽,用户「稍微动一下就被吸过去」。新规则严格按 0dp 边距触发吸附。
                    val bubbleLeft = params.x
                    val bubbleRight = params.x + currentWidth
                    when {
                        bubbleLeft <= 0 -> {
                            lastSide = SnapSide.LEFT
                            setVisualState(VisualState.CIRCLE)
                        }
                        bubbleRight >= screenWidth -> {
                            lastSide = SnapSide.RIGHT
                            setVisualState(VisualState.CIRCLE)
                        }
                        else -> setVisualState(VisualState.EXPANDED)
                    }
                } else {
                    // 没移动:按当前状态 tap → 下一态
                    when (visualState) {
                        VisualState.LINE -> setVisualState(VisualState.CIRCLE)
                        VisualState.CIRCLE -> setVisualState(VisualState.EXPANDED)
                        VisualState.EXPANDED -> setVisualState(VisualState.CIRCLE)
                    }
                }
            }
        }
        return true
    }

    // ---- 状态机核心:setVisualState 是唯一原子切换入口 ----

    private fun setVisualState(next: VisualState) {
        if (visualState != next) {
            Log.d(TAG, "state: $visualState → $next")
            visualState = next
        }
        applyVisibilityForState()
        params.width = widthForState(next)
        // 三态统一按 lastSide 重算 params.x:切到 EXPANDED 时 width 从 36 变 200,
        // 不重算会让贴右屏的圆展开后半截被屏幕裁掉。这里无条件重算保证全宽可见。
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        params.x = when (lastSide) {
            SnapSide.LEFT -> dp(EDGE_MARGIN_DP)
            SnapSide.RIGHT -> screenWidth - params.width - dp(EDGE_MARGIN_DP)
        }
        if (attached) {
            try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
        }
        refreshAutoCollapseSchedule()
    }

    private fun applyVisibilityForState() {
        when (visualState) {
            VisualState.LINE -> {
                pinnedView.visibility = View.VISIBLE
                collapsedView.visibility = View.GONE
                expandedView.visibility = View.GONE
            }
            VisualState.CIRCLE -> {
                pinnedView.visibility = View.GONE
                collapsedView.visibility = View.VISIBLE
                expandedView.visibility = View.GONE
            }
            VisualState.EXPANDED -> {
                pinnedView.visibility = View.GONE
                collapsedView.visibility = View.GONE
                expandedView.visibility = View.VISIBLE
            }
        }
    }

    private fun widthForState(state: VisualState): Int = when (state) {
        VisualState.LINE -> dp(PINNED_WIDTH_DP)
        VisualState.CIRCLE -> dp(COLLAPSED_WIDTH_DP)
        VisualState.EXPANDED -> dp(EXPANDED_WIDTH_DP)
    }

    // ---- 自动收起定时器 ----

    private fun scheduleAutoCollapse() {
        handler.removeCallbacks(autoCollapseRunnable)
        handler.postDelayed(autoCollapseRunnable, AUTO_COLLAPSE_DELAY_MS)
        autoCollapseScheduled = true
        Log.d(TAG, "auto-collapse scheduled in ${AUTO_COLLAPSE_DELAY_MS}ms")
    }

    private fun cancelAutoCollapse() {
        if (autoCollapseScheduled) {
            handler.removeCallbacks(autoCollapseRunnable)
            autoCollapseScheduled = false
            Log.d(TAG, "auto-collapse cancelled")
        }
    }

    private fun refreshAutoCollapseSchedule() {
        cancelAutoCollapse()
        if (visualState == VisualState.CIRCLE) {
            scheduleAutoCollapse()
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}
