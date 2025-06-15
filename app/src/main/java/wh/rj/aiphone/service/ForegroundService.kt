package wh.rj.aiphone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import wh.rj.aiphone.R

class ForegroundService : Service() {
    private var isServiceRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServiceRunning) {
            Log.d(TAG, "Service is already running")
            return START_STICKY
        }

        val targetPackage = intent?.getStringExtra("target_package")
        if (targetPackage.isNullOrEmpty()) {
            Log.e(TAG, "No target package provided")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("自动化服务运行中")
            .setContentText("正在监控应用：$targetPackage")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isServiceRunning = true
        Log.d(TAG, "Service started for package: $targetPackage")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "自动化服务",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "用于保持自动化服务运行"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ForegroundService"
        private const val CHANNEL_ID = "AutomationServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
}

object AutoLauncher {
    fun launchApp(context: Context, packageName: String) {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d("AutoLauncher", "启动 App: $packageName")
            } else {
                Log.w("AutoLauncher", "未找到启动 Intent: $packageName")
            }
        } catch (e: Exception) {
            Log.e("AutoLauncher", "启动失败: $packageName", e)
        }
    }
}

object AutoActionExecutor {
    fun clickByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "点击文本: $text")
                return true
            }
        }
        return false
    }

    fun inputText(root: AccessibilityNodeInfo?, hintText: String, content: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(hintText)
        for (node in nodes) {
            if (node.isEditable) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, content)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d(TAG, "输入内容: $content 到字段: $hintText")
                return true
            }
        }
        return false
    }

    private const val TAG = "AutoActionExecutor"
}