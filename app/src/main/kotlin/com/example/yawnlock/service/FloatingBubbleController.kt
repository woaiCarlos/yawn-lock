package com.example.yawnlock.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yawnlock.R
import com.example.yawnlock.YawnApplication
import com.example.yawnlock.domain.DurationFormatter
import com.example.yawnlock.domain.TimerStatus
import com.example.yawnlock.ui.theme.Night900
import com.example.yawnlock.ui.theme.Purple500
import com.example.yawnlock.ui.theme.Purple900

class FloatingBubbleController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val bubbleView: ComposeView =
        LayoutInflater.from(context).inflate(R.layout.floating_bubble, null) as ComposeView

    private val params = WindowManager.LayoutParams(
        dp(200), WindowManager.LayoutParams.WRAP_CONTENT,
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

    private var collapsed by mutableStateOf(false)
    private var remainingMs by mutableStateOf(0L)
    private var isPaused by mutableStateOf(false)

    private var startX = 0f
    private var startY = 0f
    private var startParamsX = 0
    private var startParamsY = 0
    private var moved = false

    init {
        bubbleView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        bubbleView.setContent {
            BubbleContent(
                remainingMs = remainingMs,
                isPaused = isPaused,
                collapsed = collapsed,
                onPauseToggle = ::togglePause,
                onStop = ::stopCountdown,
            )
        }
        bubbleView.setOnTouchListener { _, ev -> handleTouch(ev) }
    }

    fun show() {
        try {
            wm.addView(bubbleView, params)
        } catch (e: WindowManager.BadTokenException) {
            // token 失效,静默忽略
        }
        // 启动时同步当前状态
        val s = (context.applicationContext as YawnApplication).timerRepository.state.value
        remainingMs = s.remainingMs
        isPaused = s.status is TimerStatus.Paused
    }

    fun hide() {
        try { wm.removeView(bubbleView) } catch (_: Exception) {}
    }

    fun updateTime(ms: Long) {
        remainingMs = ms
        isPaused = (context.applicationContext as YawnApplication).timerRepository.state.value.status is TimerStatus.Paused
    }

    private fun togglePause() {
        val repo = (context.applicationContext as YawnApplication).timerRepository
        if (repo.state.value.status is TimerStatus.Paused) {
            repo.resume()
            context.startService(
                Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_RESUME)
            )
        } else {
            repo.pause()
            context.startService(
                Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_PAUSE)
            )
        }
    }

    private fun stopCountdown() {
        val repo = (context.applicationContext as YawnApplication).timerRepository
        repo.stop()
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
                if (moved && params.x < dp(36)) collapse()
                else if (!moved && collapsed) expand()
            }
        }
        return true
    }

    private fun collapse() {
        collapsed = true
        params.width = dp(36)
        params.x = dp(6)
        try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
    }

    private fun expand() {
        collapsed = false
        params.width = dp(200)
        params.x = dp(40)
        try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}

@Composable
private fun BubbleContent(
    remainingMs: Long,
    isPaused: Boolean,
    collapsed: Boolean,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Purple900, Purple500)))
            .padding(14.dp),
    ) {
        if (collapsed) {
            Icon(
                painter = painterResource(R.drawable.ic_moon),
                contentDescription = "展开",
                tint = Color(0xFFFFD97A),
                modifier = Modifier.size(16.dp).clickable { onPauseToggle.run {} },
            )
        } else {
            Column {
                // handle bar
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.3f))
                        .align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFD97A).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_moon),
                            contentDescription = null,
                            tint = Color(0xFFFFD97A),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Sleepy Lock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (isPaused) "已暂停" else "计时中", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    DurationFormatter.toMmSs(remainingMs),
                    color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BubbleButton(
                        label = if (isPaused) "继续" else "暂停",
                        onClick = onPauseToggle,
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.16f),
                        textColor = Color.White,
                    )
                    BubbleButton(
                        label = "停止",
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFFFD97A),
                        textColor = Night900,
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color,
    textColor: Color,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
