package com.example.yawnlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.yawnlock.ui.permissions.PermissionsScreen
import com.example.yawnlock.ui.permissions.PermissionsViewModel
import com.example.yawnlock.ui.theme.YawnLockTheme
import com.example.yawnlock.ui.timer.TimerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YawnLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    val permsVm: PermissionsViewModel = viewModel()
    LaunchedEffect(Unit) { permsVm.refresh() }
    val perms by permsVm.state.collectAsState()

    val startDest = if (perms.canStartCountdown) "timer" else "permissions"

    NavHost(navController = nav, startDestination = startDest) {
        composable("timer") {
            TimerScreen(onNavigatePermissions = { nav.navigate("permissions") })
        }
        composable("permissions") {
            PermissionsScreen(
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
}
