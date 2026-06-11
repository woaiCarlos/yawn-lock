package com.example.yawnlock.ui.permissions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yawnlock.R
import com.example.yawnlock.data.DeviceAdminReceiver
import com.example.yawnlock.ui.theme.Purple50
import com.example.yawnlock.ui.theme.Purple500
import com.example.yawnlock.ui.theme.Purple900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    vm: PermissionsViewModel,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Purple50)
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Purple900),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_moon), null, tint = Color(0xFFFFD97A)) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Sleepy Lock 需要这些权限",
                            fontWeight = FontWeight.SemiBold, color = Purple900)
                        Spacer(Modifier.height(4.dp))
                        Text("每项权限都只用于其对应的功能,我们不会收集你的任何数据。点击下方权限可前往系统设置调整。",
                            fontSize = 13.sp, color = Purple500)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    PermissionRow(
                        title = "悬浮窗",
                        desc = "在其他 App 上方显示倒计时与控制气泡",
                        granted = state.overlayGranted,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = "设备管理员",
                        desc = "用于到点强制锁屏,这是核心功能所需",
                        granted = state.deviceAdminActive,
                        onClick = {
                            val component = ComponentName(context, DeviceAdminReceiver::class.java)
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    context.getString(R.string.device_admin_explanation))
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧 icon 盒:已授权用实色紫,未授权用浅紫
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (granted) Purple500 else Purple50),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_moon), null,
                tint = if (granted) Color.White else Purple500,
            )
        }
        Spacer(Modifier.width(16.dp))
        // 中间 title + desc
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = Purple900,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                fontSize = 12.sp,
                color = Color(0xFF6B6B6B),
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 右侧状态 pill:与 icon 盒同色系,已授权=紫底白字,未授权=浅紫底深紫字
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (granted) Purple500 else Purple50)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                if (granted) "已授权" else "未授权",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (granted) Color.White else Purple900,
            )
        }
    }
}
