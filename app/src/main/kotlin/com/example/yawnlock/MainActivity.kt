package com.example.yawnlock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.yawnlock.ui.permissions.PermissionsScreen
import com.example.yawnlock.ui.permissions.PermissionsViewModel
import com.example.yawnlock.ui.theme.YawnLockTheme
import com.example.yawnlock.ui.timer.TimerScreen

class MainActivity : ComponentActivity(), DefaultLifecycleObserver {
    private val permsVm: PermissionsViewModel by lazy { PermissionsViewModel(application) }

    private val oemPrefs: SharedPreferences by lazy {
        getSharedPreferences("yawn_lock_prefs", MODE_PRIVATE)
    }

    private fun isOemWarnedShown(): Boolean = oemPrefs.getBoolean("oem_warned_shown", false)

    fun markOemWarnedShown() {
        oemPrefs.edit().putBoolean("oem_warned_shown", true).apply()
    }

    private fun isAggressiveOem(manufacturer: String): Boolean = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus" -> true
        else -> false
    }

    fun shouldShowOemWarning(): String? {
        if (isOemWarnedShown()) return null
        val manufacturer = Build.MANUFACTURER
        if (!isAggressiveOem(manufacturer)) {
            markOemWarnedShown()
            return null
        }
        return manufacturer
    }

    fun openBatteryOptimizationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super<ComponentActivity>.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(this)
        setContent {
            YawnLockTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    AppNavHost(permsVm = permsVm, host = this)
                }
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        permsVm.refresh()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 用户按 home / 切到最近任务 / 切到其他 app:立即显示气泡
        // 不等 ProcessLifecycle 的 700ms 防抖延迟
        val app = application as YawnApplication
        if (app.timerRepository.state.value.isActive) {
            app.bubbleController.show()
        }
    }

    override fun onDestroy() {
        lifecycle.removeObserver(this)
        super<ComponentActivity>.onDestroy()
    }
}

@Composable
private fun AppNavHost(permsVm: PermissionsViewModel, host: MainActivity) {
    val nav = rememberNavController()
    LaunchedEffect(Unit) { permsVm.refresh() }
    val perms by permsVm.state.collectAsState()

    var oemDialogShown by remember { mutableStateOf(false) }
    val oemManufacturer = remember {
        // 首次组合时检查,后续不会重弹
        host.shouldShowOemWarning()
    }

    val startDest = remember {
        if (perms.canStartCountdown) "timer" else "permissions"
    }

    NavHost(navController = nav, startDestination = startDest) {
        composable("timer") {
            TimerScreen(onNavigatePermissions = { nav.navigate("permissions") })
        }
        composable("permissions") {
            PermissionsScreen(
                vm = permsVm,
                onBack = {
                    if (perms.canStartCountdown) {
                        nav.navigate("timer") {
                            popUpTo("permissions") { inclusive = true }
                        }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
    }

    if (oemManufacturer != null && !oemDialogShown) {
        AlertDialog(
            onDismissRequest = { /* 强制点按钮 */ },
            title = { Text(host.getString(R.string.oem_warning_title)) },
            text = { Text(host.getString(R.string.oem_warning_message, oemManufacturer)) },
            confirmButton = {
                TextButton(onClick = {
                    host.openBatteryOptimizationSettings()
                    host.markOemWarnedShown()
                    oemDialogShown = true
                }) { Text(host.getString(R.string.oem_warning_goto)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    host.markOemWarnedShown()
                    oemDialogShown = true
                }) { Text(host.getString(R.string.oem_warning_dismiss)) }
            },
        )
    }
}
