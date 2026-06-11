package com.example.yawnlock.data

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "已授权设备管理员", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? =
        "取消后无法定时锁屏,确定要取消吗?"

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "设备管理员已撤销", Toast.LENGTH_SHORT).show()
    }
}
