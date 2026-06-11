# Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# AndroidX 反射元数据
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# Keep our domain classes (used in StateFlow)
-keep class com.example.yawnlock.domain.** { *; }
