package wh.rj.aiphone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import wh.rj.aiphone.adapter.AppListAdapter
import wh.rj.aiphone.model.AppInfo
import wh.rj.aiphone.model.LocationRecord
import wh.rj.aiphone.service.AIAccessibilityService
import wh.rj.aiphone.service.ForegroundService
import wh.rj.aiphone.service.FloatingWindowService

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvSelectedApp: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnRequestOverlayPermission: Button
    private lateinit var btnStartService: Button
    private lateinit var btnViewLogs: Button
    private lateinit var btnLocationRecord: Button
    private lateinit var rvAppList: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    
    private var selectedApp: AppInfo? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var statusUpdateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        updateAccessibilityStatus()
        loadInstalledApps()

        btnOpenAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        btnRequestOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }

        btnStartService.setOnClickListener {
            selectedApp?.let { app ->
                smartStartService(app.packageName)
            }
        }
        
        btnViewLogs.setOnClickListener {
            val intent = Intent(this, LogViewActivity::class.java)
            startActivity(intent)
        }
        
        btnLocationRecord.setOnClickListener {
            showLocationRecords()
        }
        
        startStatusMonitoring()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvSelectedApp = findViewById(R.id.tvSelectedApp)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)
        btnRequestOverlayPermission = findViewById(R.id.btnRequestOverlayPermission)
        btnStartService = findViewById(R.id.btnStartService)
        btnViewLogs = findViewById(R.id.btnViewLogs)
        btnLocationRecord = findViewById(R.id.btnLocationRecord)
        rvAppList = findViewById(R.id.rvAppList)
    }

    private fun setupRecyclerView() {
        appListAdapter = AppListAdapter(emptyList()) { app ->
            selectedApp = app
            tvSelectedApp.text = "${app.appName} (${app.packageName})"
            updateServiceButtonState()
        }
        
        rvAppList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appListAdapter
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { !isSystemApp(it.flags) }
            .map { appInfo ->
                AppInfo(
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    icon = pm.getApplicationIcon(appInfo.packageName)
                )
            }
            .sortedBy { it.appName }
        
        appListAdapter.updateApps(apps)
    }

    private fun isSystemApp(flags: Int): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateOverlayPermissionStatus()
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled(this, AIAccessibilityService::class.java)
        val gestureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        
        val statusText = when {
            !enabled -> "❌ 无障碍服务未开启"
            !gestureSupported -> "⚠️ 无障碍服务已开启，但Android版本过低（需要7.0+支持手势）"
            else -> "✅ 无障碍服务已开启，支持手势操作"
        }
        
        tvStatus.text = statusText
        updateServiceButtonState()
    }

    private fun updateServiceButtonState() {
        val accessibilityEnabled = isAccessibilityServiceEnabled(this, AIAccessibilityService::class.java)
        btnStartService.isEnabled = accessibilityEnabled && selectedApp != null
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this, "✋ 请在无障碍设置中开启AIPhone服务，并确保允许手势操作", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun smartStartService(packageName: String) {
        try {
            // 检查无障碍服务是否已启用
            if (!isAccessibilityServiceEnabled(this, AIAccessibilityService::class.java)) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return
            }

            wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "🚀 打开任务配置页面，目标包名：$packageName")
            
            // 获取应用名称
            val appName = selectedApp?.appName ?: "未知应用"
            
            // 跳转到任务配置页面
            val configIntent = Intent(this, wh.rj.aiphone.ui.TaskConfigActivity::class.java).apply {
                putExtra("package_name", packageName)
                putExtra("app_name", appName)
            }
            
            startActivity(configIntent)
            
            wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "已打开任务配置页面: $appName ($packageName)")
            
        } catch (e: Exception) {
            wh.rj.aiphone.utils.LogCollector.addLog("E", "MainActivity", "打开任务配置失败: ${e.message}")
            Toast.makeText(this, "打开任务配置失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startFloatingService(packageName: String) {
        wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "启动悬浮窗服务")
        
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            wh.rj.aiphone.utils.LogCollector.addLog("W", "MainActivity", "需要悬浮窗权限")
            Toast.makeText(this, "需要悬浮窗权限，请授权", Toast.LENGTH_SHORT).show()
            requestFloatingPermission()
            return
        }
        
        // 停止之前的悬浮窗服务
        FloatingWindowService.stopExistingService()
        
        // 启动新的悬浮窗服务
        val floatingIntent = Intent(this, FloatingWindowService::class.java)
        floatingIntent.putExtra("target_package", packageName)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(floatingIntent)
        } else {
            startService(floatingIntent)
        }
        
        wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "悬浮窗服务已启动")
        Toast.makeText(this, "🔮 悬浮窗已显示", Toast.LENGTH_SHORT).show()
    }
    
    private fun requestFloatingPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.data = android.net.Uri.parse("package:$packageName")
        startActivity(intent)
    }
    
    private fun requestOverlayPermission() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("🔐 申请AIPhone悬浮窗权限")
                    .setMessage("为了在其他应用上显示任务状态悬浮按钮，需要获取悬浮窗权限。\n\n⚠️ 重要说明：\n• 这是给AIPhone应用申请权限\n• 用于在任何应用上层显示控制面板\n\n此权限将用于：\n• 显示任务执行状态\n• 控制任务启动/停止\n• 自定义操作设置")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "🔐 跳转到系统设置申请AIPhone悬浮窗权限")
                    }
                    .setNegativeButton("取消") { _, _ ->
                        Toast.makeText(this, "⚠️ 没有悬浮窗权限将无法显示任务状态", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            } else {
                Toast.makeText(this, "✅ AIPhone悬浮窗权限已获取", Toast.LENGTH_SHORT).show()
                updateOverlayPermissionStatus()
            }
        } catch (e: Exception) {
            wh.rj.aiphone.utils.LogCollector.addLog("E", "MainActivity", "❌ 申请AIPhone悬浮窗权限失败: ${e.message}")
            Toast.makeText(this, "❌ 权限申请失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateOverlayPermissionStatus() {
        val hasPermission = Settings.canDrawOverlays(this)
        btnRequestOverlayPermission.text = if (hasPermission) "✅ 悬浮窗权限" else "🔐 悬浮窗权限"
        
        if (hasPermission) {
            wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "✅ AIPhone悬浮窗权限状态: 已授权")
        } else {
            wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "⚠️ AIPhone悬浮窗权限状态: 未授权")
        }
    }



    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val serviceName = ComponentName(context, service).flattenToString()
        wh.rj.aiphone.utils.LogCollector.addLog("D", "MainActivity", "检查服务名称：$serviceName")
        
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        wh.rj.aiphone.utils.LogCollector.addLog("D", "MainActivity", "系统已启用的无障碍服务：$enabledServices")
        
        if (!TextUtils.isEmpty(enabledServices)) {
            val colonSplitter = enabledServices.split(":")
            wh.rj.aiphone.utils.LogCollector.addLog("D", "MainActivity", "解析到${colonSplitter.size}个已启用服务")
            
            for (enabledService in colonSplitter) {
                wh.rj.aiphone.utils.LogCollector.addLog("D", "MainActivity", "检查服务：$enabledService")
                if (enabledService.equals(serviceName, ignoreCase = true)) {
                    wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "✅ 找到我们的无障碍服务")
                    return true
                }
            }
        }
        
        wh.rj.aiphone.utils.LogCollector.addLog("W", "MainActivity", "❌ 未找到我们的无障碍服务")
        return false
    }
    
    /**
     * 显示定位记录对话框
     */
    private fun showLocationRecords() {
        try {
            val records = LocationRecord.getAllLocationRecords(this)
            
            if (records.isEmpty()) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("📍 定位记录")
                    .setMessage("暂无定位记录\n\n💡 使用方法：\n1. 选择应用并启动任务配置\n2. 在悬浮窗中点击「定位」按钮\n3. 选择操作类型（点赞、评论等）\n4. 在屏幕上精确定位目标位置")
                    .setPositiveButton("确定", null)
                    .show()
                return
            }
            
            // 构建记录列表显示
            val recordsText = buildString {
                records.values.forEachIndexed { index, record ->
                    if (index > 0) append("\n\n")
                    append("📱 应用：${record.appName}\n")
                    append("📦 包名：${record.packageName}\n")
                    append("🎯 类型：${record.type}\n")
                    append("🎮 操作：${record.getOperationSummary()}\n")
                    append("⏰ 时间：${record.getFormattedTime()}")
                }
            }
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("📍 定位记录 (${records.size}个应用)")
                .setMessage(recordsText)
                .setPositiveButton("确定", null)
                .setNegativeButton("清空记录") { _, _ ->
                    clearLocationRecords()
                }
                .show()
                
            wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "📍 显示定位记录，共${records.size}个应用")
            
        } catch (e: Exception) {
            wh.rj.aiphone.utils.LogCollector.addLog("E", "MainActivity", "❌ 显示定位记录失败: ${e.message}")
            Toast.makeText(this, "显示定位记录失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 清空定位记录
     */
    private fun clearLocationRecords() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ 确认清空")
            .setMessage("确定要清空所有定位记录吗？\n此操作不可恢复。")
            .setPositiveButton("确定清空") { _, _ ->
                try {
                    LocationRecord.clearAllLocationRecords(this)
                    Toast.makeText(this, "✅ 定位记录已清空", Toast.LENGTH_SHORT).show()
                    wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", "🗑️ 用户清空了所有定位记录")
                } catch (e: Exception) {
                    wh.rj.aiphone.utils.LogCollector.addLog("E", "MainActivity", "❌ 清空定位记录失败: ${e.message}")
                    Toast.makeText(this, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    

    
    private fun startStatusMonitoring() {
        statusUpdateRunnable = object : Runnable {
            override fun run() {
                updateServiceStatus()
                handler.postDelayed(this, 2000) // 每2秒检查一次
            }
        }
        handler.post(statusUpdateRunnable!!)
    }
    
    private fun updateServiceStatus() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this, AIAccessibilityService::class.java)
        val logCount = wh.rj.aiphone.utils.LogCollector.getLogCount()
        
        val statusText = buildString {
            append("🔧 无障碍服务: ")
            append(if (isAccessibilityEnabled) "✅ 已启用" else "❌ 未启用")
            append("\n📊 日志条数: $logCount")
            append("\n⏰ 状态更新: ${getCurrentTime()}")
        }
        
        tvServiceStatus.text = statusText
        
        // 记录状态检查日志
        if (logCount % 10 == 0 && logCount > 0) { // 每10条日志记录一次状态
            wh.rj.aiphone.utils.LogCollector.addLog("I", "MainActivity", 
                "状态检查 - 无障碍服务: ${if (isAccessibilityEnabled) "已启用" else "未启用"}, 日志数: $logCount")
        }
    }
    
    private fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
    
    override fun onDestroy() {
        super.onDestroy()
        statusUpdateRunnable?.let { handler.removeCallbacks(it) }
    }
}
