package wh.rj.aiphone.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import kotlin.math.abs
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import wh.rj.aiphone.R
import wh.rj.aiphone.model.TaskStats
import wh.rj.aiphone.model.TaskConfig
import wh.rj.aiphone.model.LocationRecord
import wh.rj.aiphone.utils.LogCollector

/**
 * 状态悬浮按钮服务
 * 显示任务执行的实时统计信息，美观且便于查看
 */
class StatusFloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var taskStats: TaskStats? = null
    private val handler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "StatusFloatingChannel"
    private var isExpanded = false
    private var isTaskRunning = false
    
    // 定位记录相关变量
    private var isRecordingCoordinates = false
    private var recordedX = -1
    private var recordedY = -1
    private var coordinateOverlayView: View? = null
    private var currentOperationType = "点赞" // 当前选择的操作类型
    
    // UI组件
    private var mainStatusView: LinearLayout? = null
    private var expandedStatsView: LinearLayout? = null
    private var controlButtonsView: LinearLayout? = null
    private var statusIcon: TextView? = null
    private var quickStats: TextView? = null
    private var detailedStats: TextView? = null
    private var progressBar: View? = null
    private var btnStartStop: Button? = null
    private var btnSettings: Button? = null
    
    // 自定义操作设置
    private var customLikeCount = 10
    private var customLikeInterval = 2000L // 点赞间隔，毫秒
    private var customSwipeEnabled = false // 是否启用滑动
    private var customSwipeCount = 5 // 滑动次数
    private var customSwipeInterval = 3000L // 滑动间隔，毫秒
    private var targetPackageName = ""
    private var currentTaskConfig: TaskConfig? = null
    private var hasCustomSettings = false // 标记是否有自定义设置
    
    // 从任务配置界面获取默认设置
    private fun loadDefaultSettingsFromConfig() {
        try {
            val prefs = getSharedPreferences("task_configs", MODE_PRIVATE)
            val configJson = prefs.getString("config_${targetPackageName}", null)
            
            if (configJson != null) {
                // 这里可以解析JSON获取配置，但为了简化，我们直接从TaskConfig获取默认值
                val defaultConfig = TaskConfig.getDefaultByPackage(targetPackageName, currentTaskConfig?.appName ?: "")
                
                if (defaultConfig.isLiveMode) {
                    customLikeCount = defaultConfig.liveLikeMaxCount
                    customLikeInterval = defaultConfig.liveLikeInterval
                    LogCollector.addLog("I", TAG, "✅ 从任务配置加载默认设置: 点赞${customLikeCount}次, 间隔${customLikeInterval}ms")
                }
            }
        } catch (e: Exception) {
            LogCollector.addLog("W", TAG, "⚠️ 加载默认配置失败: ${e.message}")
        }
    }
    
    companion object {
        private var instance: StatusFloatingService? = null
        private const val TAG = "StatusFloating"
        
        fun getInstance(): StatusFloatingService? = instance
        
        fun updateStats(stats: TaskStats) {
            instance?.updateTaskStats(stats)
        }
        
        fun stopService() {
            instance?.stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        LogCollector.addLog("I", TAG, "🎯 状态悬浮按钮服务创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra("target_package") ?: ""
        val appName = intent?.getStringExtra("app_name") ?: ""
        
        targetPackageName = packageName
        taskStats = TaskStats(packageName = packageName, appName = appName)
        
        // 获取当前任务配置
        currentTaskConfig = TaskConfig.getDefaultByPackage(packageName, appName)
        
        // 加载默认设置
        loadDefaultSettingsFromConfig()
        
        if (Settings.canDrawOverlays(this)) {
            createStatusFloatingView()
            startForegroundNotification()
            startStatsUpdateTimer()
            LogCollector.addLog("I", TAG, "✅ 状态悬浮按钮启动成功: $appName")
        } else {
            LogCollector.addLog("W", TAG, "⚠️ 缺少悬浮窗权限，状态显示功能不可用")
            // 不要停止服务，让它在后台静默运行，避免影响主功能
            startForegroundNotification()
        }
        
        return START_STICKY
    }

    private fun createStatusFloatingView() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 主容器 - 圆角设计
            val mainContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
                
                // 创建渐变背景
                val gradient = GradientDrawable().apply {
                    colors = intArrayOf(
                        Color.parseColor("#FF6B6B"),  // 红色
                        Color.parseColor("#4ECDC4")   // 青色
                    )
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    cornerRadius = 24f
                    setStroke(2, Color.parseColor("#FFFFFF"))
                }
                background = gradient
                elevation = 8f
            }
            
            // 创建主状态视图（紧凑模式）
            mainStatusView = createMainStatusView()
            mainContainer.addView(mainStatusView)
            
            // 创建展开的统计视图（详细模式）
            expandedStatsView = createExpandedStatsView()
            expandedStatsView?.visibility = View.GONE
            mainContainer.addView(expandedStatsView)
            
            floatingView = mainContainer
            
            // 设置悬浮窗参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 20
                y = 200
            }

            windowManager?.addView(floatingView, params)
            
            // 设置点击和拖拽事件
            setupTouchEvents(mainContainer, params)
            
            LogCollector.addLog("I", TAG, "✅ 状态悬浮按钮创建成功")
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 状态悬浮按钮创建失败: ${e.message}")
        }
    }
    
    private fun createMainStatusView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 6, 8, 6)
            gravity = Gravity.CENTER_VERTICAL
            
            // 状态图标
            statusIcon = TextView(this@StatusFloatingService).apply {
                text = "🚀"
                textSize = 14f
                setPadding(0, 0, 6, 0)
            }
            addView(statusIcon)
            
            // 快速统计
            quickStats = TextView(this@StatusFloatingService).apply {
                text = "0/0"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(quickStats)
            
            // 右上角隐藏按钮
            val hideButton = TextView(this@StatusFloatingService).apply {
                text = "×"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(4, 2, 4, 2)
                setBackgroundColor(Color.parseColor("#80000000"))
                setOnClickListener {
                    // 收起悬浮窗到最小状态
                    isExpanded = false
                    expandedStatsView?.visibility = View.GONE
                    LogCollector.addLog("I", TAG, "👁 用户隐藏悬浮窗详细信息")
                }
            }
            addView(hideButton)
        }
    }
    
    private fun createExpandedStatsView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 12)
            
            // 详细统计信息
            detailedStats = TextView(this@StatusFloatingService).apply {
                text = "详细统计信息"
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 4)
            }
            addView(detailedStats)
            
            // 进度条
            val progressContainer = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            val progressBackground = View(this@StatusFloatingService).apply {
                setBackgroundColor(Color.parseColor("#FFFFFF40"))
                layoutParams = LinearLayout.LayoutParams(120, 6)
            }
            
            progressBar = View(this@StatusFloatingService).apply {
                setBackgroundColor(Color.parseColor("#FFFFFF"))
                layoutParams = LinearLayout.LayoutParams(0, 6)
            }
            
            progressContainer.addView(progressBackground)
            progressContainer.addView(progressBar)
            addView(progressContainer)
            
            // 控制按钮区域
            controlButtonsView = createControlButtonsView()
            addView(controlButtonsView)
        }
    }
    
    private fun createControlButtonsView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
            
            // 第一行按钮
            val firstRowButtons = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            
            // 启动/停止按钮
            btnStartStop = Button(this@StatusFloatingService).apply {
                text = if (isTaskRunning) "⏸️停止" else "▶️启动"
                textSize = 8f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#FF6B6B"))
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(0, 0, 2, 0)
                }
                
                setOnClickListener {
                    toggleTask()
                }
            }
            
            // 设置按钮
            btnSettings = Button(this@StatusFloatingService).apply {
                text = "⚙️设置"
                textSize = 8f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4ECDC4"))
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(2, 0, 0, 0)
                }
                
                setOnClickListener {
                    showSettingsDialog()
                }
            }
            
            firstRowButtons.addView(btnStartStop)
            firstRowButtons.addView(btnSettings)
            addView(firstRowButtons)
            
            // 第二行按钮
            val secondRowButtons = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }
            
            // 定位按钮
            val btnLocationRecord = Button(this@StatusFloatingService).apply {
                text = "📍定位"
                textSize = 8f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#9C27B0"))
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(0, 0, 2, 0)
                }
                
                setOnClickListener {
                    showOperationTypeSelection()
                }
            }
            
            // 退出按钮
            val btnExit = Button(this@StatusFloatingService).apply {
                text = "退出"
                textSize = 8f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#757575"))
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(2, 0, 0, 0)
                }
                
                setOnClickListener {
                    LogCollector.addLog("I", TAG, "🔴 用户手动关闭悬浮按钮")
                    stopSelf()
                }
            }
            
            secondRowButtons.addView(btnLocationRecord)
            secondRowButtons.addView(btnExit)
            addView(secondRowButtons)
        }
    }
    
    private fun setupTouchEvents(container: LinearLayout, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var clickStartTime = 0L
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    clickStartTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    
                    // 只有移动超过一定距离才开始拖拽，提高响应性
                    if (deltaX > 15 || deltaY > 15) {
                        isDragging = true
                        val offsetX = (event.rawX - initialTouchX).toInt()
                        val offsetY = (event.rawY - initialTouchY).toInt()
                        params.x = initialX - offsetX
                        params.y = initialY + offsetY
                        
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            LogCollector.addLog("W", TAG, "⚠️ 更新悬浮窗位置失败: ${e.message}")
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - clickStartTime
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    
                    // 如果没有拖拽且是快速点击，则切换展开状态
                    if (!isDragging && clickDuration < 300 && deltaX < 20 && deltaY < 20) {
                        // 使用异步执行，避免阻塞触摸事件
                        handler.post {
                            toggleExpansion()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }
    
    private fun toggleExpansion() {
        isExpanded = !isExpanded
        
        expandedStatsView?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        
        // 添加动画效果
        expandedStatsView?.animate()?.apply {
            if (isExpanded) {
                alpha(1f)
                scaleY(1f)
            } else {
                alpha(0f)
                scaleY(0f)
            }
            setDuration(200)
            start()
        }
        
        LogCollector.addLog("I", TAG, "🔄 状态面板${if (isExpanded) "展开" else "收起"}")
    }
    
    private fun updateTaskStats(stats: TaskStats) {
        taskStats = stats
        updateUI()
    }
    
    private fun updateUI() {
        handler.post {
            val stats = taskStats ?: return@post
            
            // 更新状态图标
            statusIcon?.text = stats.getStatusIcon()
            
            // 更新快速统计 - 根据配置和模式智能显示
            quickStats?.text = generateQuickStatsText(stats)
            
            // 更新详细统计 - 根据配置智能显示
            detailedStats?.text = generateDetailedStatsText(stats)
            
            // 更新进度条
            val progress = getProgressPercentage(stats)
            val progressWidth = (120 * progress / 100f).toInt()
            progressBar?.layoutParams = LinearLayout.LayoutParams(
                progressWidth,
                6
            )
        }
    }
    
    private fun generateQuickStatsText(stats: TaskStats): String {
        return if (hasCustomSettings) {
            // 自定义设置：根据是否启用滑动显示不同的进度
            if (customSwipeEnabled) {
                "${stats.swipeCount}/${customSwipeCount}"
            } else {
                "${stats.likeCount}/${customLikeCount}"
            }
        } else {
            currentTaskConfig?.let { config ->
                when {
                    config.isLiveMode -> {
                        // 直播模式：显示点赞进度
                        "${stats.likeCount}/${config.liveLikeMaxCount}"
                    }
                    config.enableSwipeTask -> {
                        // 普通模式且启用滑动：显示滑动进度
                        "${stats.swipeCount}/${config.maxOperations}"
                    }
                    else -> {
                        // 普通模式但没有滑动：显示总操作进度
                        "${stats.totalOperations}/${config.maxOperations}"
                    }
                }
            } ?: "${stats.totalOperations}/${stats.maxOperations}"
        }
    }
    
    private fun generateDetailedStatsText(stats: TaskStats): String {
        return buildString {
            // 根据优先级显示配置信息
            if (hasCustomSettings) {
                // 优先显示浮动图标的自定义设置
                appendLine("🎯 自定义任务配置")
                if (customLikeCount > 0) {
                    appendLine("👍 点赞目标：${stats.likeCount}/${customLikeCount}")
                }
                if (customSwipeEnabled && customSwipeCount > 0) {
                    appendLine("🔄 滑动目标：${stats.swipeCount}/${customSwipeCount}")
                }
            } else {
                // 显示任务配置界面的设置
                currentTaskConfig?.let { config ->
                    
                    // 检查是否使用自定义坐标
                    val useCustomCoords = config.useCustomCoordinates && config.customLikeX > 0 && config.customLikeY > 0
                    if (useCustomCoords) {
                        appendLine("📍 自定义坐标模式")
                        appendLine("位置: (${config.customLikeX}, ${config.customLikeY})")
                        if (config.customCoordinatesName.isNotEmpty()) {
                            appendLine("备注: ${config.customCoordinatesName}")
                        }
                    } else {
                        // 检查是否有已保存的坐标
                        val savedCoords = AIAccessibilityService.getInstance()?.getCustomCoordinates(targetPackageName)
                        if (savedCoords != null) {
                            appendLine("📍 已保存坐标")
                            appendLine("位置: (${savedCoords.first}, ${savedCoords.second})")
                        } else if (config.isLiveMode) {
                            appendLine("📺 直播模式")
                            appendLine("⚠️ 建议先记录坐标以提高准确性")
                        } else {
                            appendLine("📱 普通模式")
                            
                            // 根据开关状态显示不同的信息
                            val enabledOperations = mutableListOf<String>()
                            if (config.enableSwipeTask) enabledOperations.add("滑动")
                            if (config.enableLikeOperation) enabledOperations.add("点赞")
                            if (config.enableFollowOperation) enabledOperations.add("关注") 
                            if (config.enableCommentOperation) enabledOperations.add("评论")
                            
                            if (enabledOperations.isNotEmpty()) {
                                appendLine("✅ 已启用：${enabledOperations.joinToString("、")}")
                            }
                        }
                    }
                    
                    // 显示点赞统计
                    if (useCustomCoords || config.isLiveMode) {
                        appendLine("👍 点赞：${stats.likeCount}/${config.liveLikeMaxCount}")
                    } else {
                        // 只显示已启用操作的统计
                        if (config.enableLikeOperation) {
                            appendLine("👍 点赞数：${stats.likeCount}")
                        }
                        if (config.enableFollowOperation) {
                            appendLine("➕ 关注数：${stats.followCount}")
                        }
                        if (config.enableCommentOperation) {
                            appendLine("💬 评论数：${stats.commentCount}")
                        }
                        
                        appendLine("🔄 滑动数：${stats.swipeCount}")
                    }
                } ?: run {
                    // 默认显示全部统计信息
                    appendLine("📈 执行统计")
                    appendLine("👍 点赞数：${stats.likeCount}")
                    appendLine("➕ 关注数：${stats.followCount}")
                    appendLine("💬 评论数：${stats.commentCount}")
                    appendLine("🔄 滑动数：${stats.swipeCount}")
                }
            }
            
            appendLine("📊 总操作：${stats.totalOperations}")
            append("⏱️ 总用时：${stats.getRunDurationText()}")
        }
    }
    
    private fun getProgressPercentage(stats: TaskStats): Int {
        return if (hasCustomSettings) {
            // 使用自定义设置计算进度
            if (customSwipeEnabled) {
                // 以滑动为主要进度
                if (customSwipeCount > 0) {
                    ((stats.swipeCount.toFloat() / customSwipeCount) * 100).toInt().coerceAtMost(100)
                } else {
                    0
                }
            } else {
                // 以点赞为主要进度
                if (customLikeCount > 0) {
                    ((stats.likeCount.toFloat() / customLikeCount) * 100).toInt().coerceAtMost(100)
                } else {
                    0
                }
            }
        } else {
            // 使用原有的进度计算
            stats.getProgressPercentage()
        }
    }
    
    private fun startStatsUpdateTimer() {
        // var appCheckCounter = 0 // 暂时禁用应用检测
        val updateRunnable = object : Runnable {
            override fun run() {
                // 暂时禁用应用状态检测，避免误关闭
                // 用户可以手动关闭悬浮按钮或者应用真正退出时系统会回收服务
                /*
                appCheckCounter++
                if (appCheckCounter >= 15) { // 15 * 2秒 = 30秒检查一次
                    appCheckCounter = 0
                    if (!isTargetAppRunning()) {
                        LogCollector.addLog("I", TAG, "📱 目标应用已关闭，停止悬浮服务")
                        stopSelf()
                        return
                    }
                }
                */
                
                // 从AIAccessibilityService获取最新统计数据
                val service = wh.rj.aiphone.service.AIAccessibilityService.getInstance()
                service?.getCurrentTaskStats()?.let { stats ->
                    updateTaskStats(stats)
                    // 更新任务运行状态
                    val wasRunning = isTaskRunning
                    isTaskRunning = stats.status == TaskStats.TaskStatus.RUNNING
                    
                    // 只有状态发生变化时才更新按钮文本，减少UI操作
                    if (wasRunning != isTaskRunning) {
                        handler.post {
                            btnStartStop?.text = if (isTaskRunning) "⏸️停止" else "▶️启动"
                        }
                    }
                }
                
                // 如果没有自定义设置，尝试从服务获取当前任务配置
                if (!hasCustomSettings) {
                    service?.getCurrentTaskConfig()?.let { config ->
                        if (currentTaskConfig != config) {
                            currentTaskConfig = config
                            // 配置发生变化时立即更新UI
                            handler.post {
                                updateUI()
                            }
                        }
                    }
                }
                
                // 优化更新频率：直播模式500ms更新，普通任务运行时1秒更新，空闲时3秒更新
                val isLiveMode = currentTaskConfig?.isLiveMode ?: false
                val updateInterval = when {
                    isLiveMode && isTaskRunning -> 500L  // 直播模式快速更新
                    isTaskRunning -> 1000L              // 普通模式运行时
                    else -> 3000L                       // 空闲时
                }
                handler.postDelayed(this, updateInterval)
            }
        }
        handler.post(updateRunnable)
        
        LogCollector.addLog("I", TAG, "🎯 悬浮按钮服务已启动，应用状态检测已禁用以避免误关闭")
    }
    
    private fun isTargetAppRunning(): Boolean {
        return try {
            if (targetPackageName.isEmpty()) {
                LogCollector.addLog("D", TAG, "📱 目标包名为空，保持服务运行")
                return true
            }
            
            val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            
            // 方法1：检查正在运行的进程
            val runningApps = activityManager.runningAppProcesses
            val isProcessRunning = runningApps?.any { process ->
                val matches = process.processName == targetPackageName || 
                             process.processName.startsWith(targetPackageName) ||
                             process.processName.contains(targetPackageName)
                if (matches) {
                    LogCollector.addLog("D", TAG, "🔍 找到匹配进程: ${process.processName}")
                }
                matches
            } ?: false
            
            // 简化检测逻辑，主要依靠进程检测
            LogCollector.addLog("D", TAG, "🔍 应用状态检测: $targetPackageName (进程运行: $isProcessRunning)")
            
            // 如果进程还在运行，就认为应用还活着
            if (isProcessRunning) {
                LogCollector.addLog("D", TAG, "✅ 目标应用 $targetPackageName 进程仍在运行")
                return true
            }
            
            // 进程检测失败时，给应用一些宽松的判断
            // 可能应用刚刚切换或暂时后台，不要立即关闭服务
            LogCollector.addLog("W", TAG, "⚠️ 目标应用 $targetPackageName 进程未检测到，但保持服务运行以防误判")
            return true // 暂时保持宽松策略，避免误关闭
            
        } catch (e: Exception) {
            // 如果检查失败，保持服务运行避免误判
            LogCollector.addLog("W", TAG, "⚠️ 检查应用状态失败，保持服务运行: ${e.message}")
            true
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "状态显示服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示任务执行状态"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundNotification() {
        val stats = taskStats ?: return
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎯 任务状态监控")
            .setContentText("正在监控 ${stats.appName} 的任务执行状态")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        startForeground(2, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            floatingView?.let { view ->
                windowManager?.removeView(view)
            }
            settingsFloatingView?.let { view ->
                windowManager?.removeView(view)
            }
            coordinateOverlayView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "移除悬浮视图失败: ${e.message}")
        }
        
        handler.removeCallbacksAndMessages(null)
        instance = null
        
        LogCollector.addLog("I", TAG, "🛑 状态悬浮按钮服务已停止")
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun toggleTask() {
        try {
            if (isTaskRunning) {
                // 停止任务
                val accessibilityService = wh.rj.aiphone.service.AIAccessibilityService.getInstance()
                accessibilityService?.stopCurrentTask()
                isTaskRunning = false
                btnStartStop?.text = "▶️启动"
                LogCollector.addLog("I", TAG, "⏸️ 任务已停止")
                showToast("任务已停止")
            } else {
                // 启动任务
                startCustomTask()
            }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 任务切换失败: ${e.message}")
            showToast("操作失败: ${e.message}")
        }
    }
    
    private fun startCustomTask() {
        try {
            val accessibilityService = wh.rj.aiphone.service.AIAccessibilityService.getInstance()
            if (accessibilityService == null) {
                showToast("❌ 无障碍服务未可用")
                return
            }
            
            // 优先使用任务配置界面的配置
            val taskConfig = accessibilityService.getCurrentTaskConfig()
            val finalConfig = if (taskConfig != null) {
                // 使用任务配置界面的设置
                LogCollector.addLog("I", TAG, "✅ 使用任务配置界面的设置")
                taskConfig
            } else if (hasCustomSettings) {
                // 如果没有任务配置，使用浮动图标自定义设置
                LogCollector.addLog("I", TAG, "⚙️ 使用浮动图标自定义设置")
                
                val configText = if (customSwipeEnabled) {
                    "滑动${customSwipeCount}次, 间隔${customSwipeInterval/1000.0}秒"
                } else {
                    "点赞${customLikeCount}次, 间隔${customLikeInterval/1000.0}秒"
                }
                LogCollector.addLog("I", TAG, "🎯 浮动图标配置: $configText")
                
                TaskConfig(
                    packageName = targetPackageName,
                    appName = taskStats?.appName ?: "",
                    isLiveMode = true, // 浮动图标自定义设置默认为直播模式
                    liveLikeMaxCount = if (customSwipeEnabled) customSwipeCount else customLikeCount,
                    liveLikeInterval = if (customSwipeEnabled) customSwipeInterval else customLikeInterval,
                    enableLikeOperation = !customSwipeEnabled, // 如果启用滑动则禁用点赞概率控制
                    enableFollowOperation = false,
                    enableCommentOperation = false,
                    enableSwipeTask = customSwipeEnabled, // 根据用户设置决定是否启用滑动
                    maxOperations = if (customSwipeEnabled) customSwipeCount else customLikeCount,
                    swipeInterval = customSwipeInterval,
                    enableRandomDelay = false,
                    enableGesture = true, // 启用手势以支持滑动功能
                    enableSmartDetection = true // 启用智能检测
                )
            } else {
                // 最后使用默认配置
                LogCollector.addLog("I", TAG, "🔧 使用默认直播配置")
                TaskConfig.getDefaultByPackage(targetPackageName, taskStats?.appName ?: "").copy(
                    isLiveMode = true
                )
            }
            
            // 更新当前配置
            currentTaskConfig = finalConfig
            
            val success = accessibilityService.startAutomationTask(targetPackageName, finalConfig)
            if (success) {
                isTaskRunning = true
                btnStartStop?.text = "⏸️停止"
                
                val modeText = if (finalConfig.isLiveMode) "直播模式" else "普通模式"
                val configText = if (finalConfig.isLiveMode) {
                    if (finalConfig.enableSwipeTask) {
                        "滑动${finalConfig.liveLikeMaxCount}次, 间隔${finalConfig.liveLikeInterval/1000.0}秒"
                    } else {
                        "点赞${finalConfig.liveLikeMaxCount}次, 间隔${finalConfig.liveLikeInterval/1000.0}秒"
                    }
                } else {
                    "最大操作${finalConfig.maxOperations}次"
                }
                
                LogCollector.addLog("I", TAG, "▶️ 任务已启动($modeText): $configText")
                showToast("$modeText 任务已启动")
            } else {
                showToast("❌ 任务启动失败")
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 启动任务失败: ${e.message}")
            showToast("启动失败: ${e.message}")
        }
    }
    
    private fun showSettingsDialog() {
        handler.post {
            try {
                // 创建设置悬浮窗口
                createSettingsFloatingWindow()
            } catch (e: Exception) {
                LogCollector.addLog("E", TAG, "❌ 显示设置对话框失败: ${e.message}")
                showToast("设置界面打开失败")
            }
        }
    }
    
    private fun createSettingsFloatingWindow() {
        try {
            val settingsView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                
                // 渐变背景
                val gradient = GradientDrawable().apply {
                    colors = intArrayOf(
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#F8F9FA")
                    )
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    cornerRadius = 20f
                    setStroke(1, Color.parseColor("#E0E0E0"))
                    // 添加阴影效果
                }
                background = gradient
                elevation = 12f
            }
            
            // 标题栏
            val headerContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 20)
            }
            
            val iconView = TextView(this).apply {
                text = "🎯"
                textSize = 24f
                setPadding(0, 0, 12, 0)
            }
            
            val titleView = TextView(this).apply {
                text = "任务设置"
                textSize = 20f
                setTextColor(Color.parseColor("#2C3E50"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            headerContainer.addView(iconView)
            headerContainer.addView(titleView)
            settingsView.addView(headerContainer)
            
            // 说明卡片
            val infoCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                
                val cardDrawable = GradientDrawable().apply {
                    setColor(Color.parseColor("#E3F2FD"))
                    cornerRadius = 12f
                    setStroke(1, Color.parseColor("#BBDEFB"))
                }
                background = cardDrawable
            }
            
            val infoText = TextView(this).apply {
                text = "📝 使用滑动条调整参数，获得最佳的直播自动化体验"
                textSize = 13f
                setTextColor(Color.parseColor("#1976D2"))
                gravity = Gravity.CENTER
                setLineSpacing(1.2f, 1.0f)
            }
            
            infoCard.addView(infoText)
            settingsView.addView(infoCard)
            
            // 点赞次数设置容器
            val likeCountContainer = createSliderSection(
                "👍 点赞次数",
                customLikeCount,
                10, 100000,
                "次"
            ) { newValue ->
                customLikeCount = newValue
                LogCollector.addLog("I", TAG, "📝 点赞次数设置为: $customLikeCount")
            }
            settingsView.addView(likeCountContainer)
            
            // 点赞间隔设置容器
            val likeIntervalContainer = createIntervalSliderSection(
                "⏱️ 点赞间隔",
                customLikeInterval,
                100L, 5000L
            ) { newValue ->
                customLikeInterval = newValue
                LogCollector.addLog("I", TAG, "⏱️ 点赞间隔设置为: ${customLikeInterval}ms")
            }
            settingsView.addView(likeIntervalContainer)
            
            // 滑动设置容器
            val swipeContainer = createSwipeSettingsSection()
            settingsView.addView(swipeContainer)
            
            // 按钮容器
            val buttonContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            }
            
            // 取消按钮
            val cancelButton = Button(this).apply {
                text = "取消"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#757575"))
                setPadding(24, 8, 24, 8)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
                setOnClickListener {
                    removeSettingsWindow()
                }
            }
            
            // 保存按钮
            val saveButton = Button(this).apply {
                text = "保存"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setPadding(24, 8, 24, 8)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(8, 0, 0, 0)
                }
                setOnClickListener {
                    saveSettingsFromDialog()
                    removeSettingsWindow()
                }
            }
            
            buttonContainer.addView(cancelButton)
            buttonContainer.addView(saveButton)
            settingsView.addView(buttonContainer)
            
            // 创建悬浮窗参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            
            // 添加到窗口管理器
            windowManager?.addView(settingsView, params)
            
            // 保存引用以便后续移除
            settingsFloatingView = settingsView
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 创建设置悬浮窗失败: ${e.message}")
            showToast("设置界面创建失败")
        }
    }
    
    /**
     * 创建数量输入区域（支持键盘编辑）
     */
    private fun createCountInputSection(
        label: String,
        currentValue: Int,
        minValue: Int,
        maxValue: Int,
        step: Int,
        unit: String,
        onValueChanged: (Int) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
            
            // 标签
            val labelView = TextView(this@StatusFloatingService).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setPadding(0, 0, 0, 4)
            }
            addView(labelView)
            
            // 控制区域
            val controlContainer = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            
            // 数值显示/编辑框
            val editText = EditText(this@StatusFloatingService).apply {
                setText(currentValue.toString())
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
                gravity = Gravity.CENTER
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setBackgroundColor(Color.WHITE)
                setPadding(8, 8, 8, 8)
                layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT)
                
                // 输入完成监听
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        try {
                            val newValue = text.toString().toIntOrNull()?.coerceIn(minValue, maxValue) ?: currentValue
                            onValueChanged(newValue)
                            setText(newValue.toString())
                        } catch (e: Exception) {
                            setText(currentValue.toString())
                        }
                    }
                }
            }
            
            // 减少按钮
            val btnDecrease = Button(this@StatusFloatingService).apply {
                text = "-"
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#FF6B6B"))
                layoutParams = LinearLayout.LayoutParams(60, 60)
                setOnClickListener {
                    val newValue = (getCurrentValueForLabel(label) - step).coerceAtLeast(minValue)
                    if (newValue != getCurrentValueForLabel(label)) {
                        onValueChanged(newValue)
                        editText.setText(newValue.toString())
                    }
                }
            }
            
            // 单位显示
            val unitView = TextView(this@StatusFloatingService).apply {
                text = unit
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setPadding(8, 0, 8, 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            
            // 增加按钮
            val btnIncrease = Button(this@StatusFloatingService).apply {
                text = "+"
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                layoutParams = LinearLayout.LayoutParams(60, 60)
                setOnClickListener {
                    val newValue = (getCurrentValueForLabel(label) + step).coerceAtMost(maxValue)
                    if (newValue != getCurrentValueForLabel(label)) {
                        onValueChanged(newValue)
                        editText.setText(newValue.toString())
                    }
                }
            }
            
            controlContainer.addView(btnDecrease)
            controlContainer.addView(editText)
            controlContainer.addView(unitView)
            controlContainer.addView(btnIncrease)
            addView(controlContainer)
        }
    }
    
    /**
     * 创建间隔输入区域
     */
    private fun createIntervalInputSection(
        label: String,
        currentValue: Long,
        minValue: Long,
        maxValue: Long,
        step: Long,
        onValueChanged: (Long) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
            
            // 标签
            val labelView = TextView(this@StatusFloatingService).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setPadding(0, 0, 0, 4)
            }
            addView(labelView)
            
            // 控制区域
            val controlContainer = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            
            // 显示区域
            val displayView = TextView(this@StatusFloatingService).apply {
                text = formatIntervalTime(currentValue)
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
                gravity = Gravity.CENTER
                setBackgroundColor(Color.WHITE)
                setPadding(12, 12, 12, 12)
                layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            
            // 减少按钮
            val btnDecrease = Button(this@StatusFloatingService).apply {
                text = "-"
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#FF6B6B"))
                layoutParams = LinearLayout.LayoutParams(60, 60)
                setOnClickListener {
                    val newValue = (getCurrentValueForIntervalLabel(label) - step).coerceAtLeast(minValue)
                    if (newValue != getCurrentValueForIntervalLabel(label)) {
                        onValueChanged(newValue)
                        displayView.text = formatIntervalTime(newValue)
                    }
                }
            }
            
            // 增加按钮
            val btnIncrease = Button(this@StatusFloatingService).apply {
                text = "+"
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                layoutParams = LinearLayout.LayoutParams(60, 60)
                setOnClickListener {
                    val newValue = (getCurrentValueForIntervalLabel(label) + step).coerceAtMost(maxValue)
                    if (newValue != getCurrentValueForIntervalLabel(label)) {
                        onValueChanged(newValue)
                        displayView.text = formatIntervalTime(newValue)
                    }
                }
            }
            
            controlContainer.addView(btnDecrease)
            controlContainer.addView(displayView)
            controlContainer.addView(btnIncrease)
            addView(controlContainer)
        }
    }
    
    // 辅助函数
    private fun getCurrentValueForLabel(label: String): Int {
        return when (label) {
            "👍 点赞次数" -> customLikeCount
            "🔄 滑动次数" -> customSwipeCount
            else -> 0
        }
    }
    
    private fun getCurrentValueForIntervalLabel(label: String): Long {
        return when (label) {
            "⏱️ 点赞间隔" -> customLikeInterval
            "⏰ 滑动间隔" -> customSwipeInterval
            else -> 0L
        }
    }
    
    private fun formatIntervalTime(value: Long): String {
        return if (value < 1000) {
            "${value}ms"
        } else {
            "${value / 1000.0}秒"
        }
    }
    
    private fun saveSettingsFromDialog() {
        try {
            // 标记有自定义设置
            hasCustomSettings = true
            
            val configText = buildString {
                append("点赞${customLikeCount}次, 间隔${customLikeInterval/1000.0}秒")
                if (customSwipeEnabled) {
                    append(", 滑动${customSwipeCount}次, 间隔${customSwipeInterval/1000.0}秒")
                }
            }
            
            LogCollector.addLog("I", TAG, "⚙️ 自定义设置已保存: $configText")
            showToast("设置已保存: $configText")
            
            // 立即更新UI显示
            handler.post {
                updateUI()
            }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 保存设置失败: ${e.message}")
            showToast("保存失败: ${e.message}")
        }
    }
    
    private var settingsFloatingView: View? = null
    
    private fun removeSettingsWindow() {
        try {
            settingsFloatingView?.let { view ->
                windowManager?.removeView(view)
                settingsFloatingView = null
            }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 移除设置窗口失败: ${e.message}")
        }
    }
    
    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示操作类型选择对话框
     */
    private fun showOperationTypeSelection() {
        try {
            val operationTypes = arrayOf("👍 点赞", "💬 评论", "➕ 关注", "🔄 转发")
            val operationValues = arrayOf("点赞", "评论", "关注", "转发")
            
            // 创建选择对话框
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("📍 选择定位操作类型")
            builder.setItems(operationTypes) { _, which ->
                currentOperationType = operationValues[which]
                LogCollector.addLog("I", TAG, "🎯 用户选择定位操作类型: $currentOperationType")
                
                // 开始坐标记录
                startCoordinateRecording()
            }
            builder.setNegativeButton("取消", null)
            
            val dialog = builder.create()
            
            // 设置对话框为系统级别
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            
            dialog.show()
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 显示操作类型选择失败: ${e.message}")
            showToast("操作类型选择失败")
            
            // 如果对话框失败，直接使用默认类型开始记录
            currentOperationType = "点赞"
            startCoordinateRecording()
        }
    }
    
    /**
     * 切换坐标记录模式
     */
    private fun toggleCoordinateRecording() {
        if (isRecordingCoordinates) {
            stopCoordinateRecording()
        } else {
            showOperationTypeSelection()
        }
    }
    
    /**
     * 开始坐标记录
     */
    private fun startCoordinateRecording() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                showToast("需要悬浮窗权限才能记录坐标")
                return
            }
            
            isRecordingCoordinates = true
            createCoordinateOverlay()
            showToast("🎯 请精确定位「${currentOperationType}」按钮位置")
            LogCollector.addLog("I", TAG, "🎯 专业定位模式已启动，操作类型: $currentOperationType")
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 启动坐标记录失败: ${e.message}")
            showToast("启动坐标记录失败")
        }
    }
    
    /**
     * 停止坐标记录
     */
    private fun stopCoordinateRecording() {
        try {
            isRecordingCoordinates = false
            removeCoordinateOverlay()
            
            if (recordedX > 0 && recordedY > 0) {
                saveRecordedCoordinates()
                showToast("✅ ${currentOperationType}定位已保存: ($recordedX, $recordedY)")
                LogCollector.addLog("I", TAG, "✅ ${currentOperationType}定位记录完成: ($recordedX, $recordedY)")
            } else {
                showToast("❌ 未记录到有效定位")
                LogCollector.addLog("W", TAG, "⚠️ 定位记录取消")
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 停止坐标记录失败: ${e.message}")
        }
    }
    
    /**
     * 创建坐标记录覆盖层
     */
    private fun createCoordinateOverlay() {
        try {
            // 创建全屏遮罩层
            createFullScreenMask()
            // 创建靶心坐标指示器
            createTargetCrosshair()
            // 创建坐标显示器
            createCoordinateDisplay()
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 创建坐标覆盖层失败: ${e.message}")
        }
    }
    
    // 坐标记录相关视图
    private var maskOverlayView: View? = null
    private var targetCrosshairView: LinearLayout? = null
    private var coordinateDisplayView: TextView? = null
    private var currentTargetX = 0
    private var currentTargetY = 0
    
    /**
     * 创建全屏遮罩层
     */
    private fun createFullScreenMask() {
        try {
            val maskView = View(this).apply {
                setBackgroundColor(Color.parseColor("#99000000")) // 60% 透明黑色遮罩
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            // 全屏触摸监听
            maskView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        updateTargetPosition(event.rawX.toInt(), event.rawY.toInt())
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        confirmCoordinateSelection(event.rawX.toInt(), event.rawY.toInt())
                        true
                    }
                    else -> false
                }
            }
            
            windowManager?.addView(maskView, params)
            maskOverlayView = maskView
            coordinateOverlayView = maskView // 主要用于清理
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 创建遮罩层失败: ${e.message}")
        }
    }
    
    /**
     * 创建靶心坐标指示器
     */
    private fun createTargetCrosshair() {
        try {
            val crosshairContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            
            // 外圆环 (透明边界提示)
            val outerCircle = View(this).apply {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(2, Color.parseColor("#80FFFFFF"))
                    setColor(Color.TRANSPARENT)
                }
                background = drawable
                layoutParams = LinearLayout.LayoutParams(60, 60)
            }
            
            // 中环圈 (可拖拽范围)
            val middleCircle = View(this).apply {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(3, Color.parseColor("#FFFF0000"))
                    setColor(Color.TRANSPARENT)
                }
                background = drawable
                layoutParams = LinearLayout.LayoutParams(30, 30).apply {
                    setMargins(0, -45, 0, 0) // 重叠显示
                }
            }
            
            // 十字线容器
            val crossContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, -30, 0, 0) // 重叠显示
                }
            }
            
            // 垂直线上半部分
            val verticalTop = View(this).apply {
                setBackgroundColor(Color.parseColor("#FFFF0000"))
                layoutParams = LinearLayout.LayoutParams(1, 10)
            }
            
            // 水平线容器
            val horizontalContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            
            // 水平线左半部分
            val horizontalLeft = View(this).apply {
                setBackgroundColor(Color.parseColor("#FFFF0000"))
                layoutParams = LinearLayout.LayoutParams(10, 1)
            }
            
            // 中心点
            val centerPoint = View(this).apply {
                setBackgroundColor(Color.parseColor("#FFFFFF00")) // 黄色中心点
                layoutParams = LinearLayout.LayoutParams(2, 2)
            }
            
            // 水平线右半部分
            val horizontalRight = View(this).apply {
                setBackgroundColor(Color.parseColor("#FFFF0000"))
                layoutParams = LinearLayout.LayoutParams(10, 1)
            }
            
            horizontalContainer.addView(horizontalLeft)
            horizontalContainer.addView(centerPoint)
            horizontalContainer.addView(horizontalRight)
            
            // 垂直线下半部分
            val verticalBottom = View(this).apply {
                setBackgroundColor(Color.parseColor("#FFFF0000"))
                layoutParams = LinearLayout.LayoutParams(1, 10)
            }
            
            crossContainer.addView(verticalTop)
            crossContainer.addView(horizontalContainer)
            crossContainer.addView(verticalBottom)
            
            crosshairContainer.addView(outerCircle)
            crosshairContainer.addView(middleCircle)
            crosshairContainer.addView(crossContainer)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 200 // 初始位置
                y = 300
            }
            
            windowManager?.addView(crosshairContainer, params)
            targetCrosshairView = crosshairContainer
            
            // 初始化位置
            currentTargetX = 230 // 200 + 30 (靶心中心)
            currentTargetY = 330 // 300 + 30
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 创建靶心指示器失败: ${e.message}")
        }
    }
    
    /**
     * 创建坐标显示器
     */
    private fun createCoordinateDisplay() {
        try {
            val coordDisplay = TextView(this).apply {
                text = "X: 230, Y: 330"
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#CC000000"))
                setPadding(12, 8, 12, 8)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.MONOSPACE
                
                // 圆角背景
                val drawable = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC000000"))
                    cornerRadius = 8f
                    setStroke(1, Color.parseColor("#FFFFFF"))
                }
                background = drawable
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 50 // 顶部显示
            }
            
            windowManager?.addView(coordDisplay, params)
            coordinateDisplayView = coordDisplay
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 创建坐标显示器失败: ${e.message}")
        }
    }
    
    /**
     * 更新靶心位置
     */
    private fun updateTargetPosition(x: Int, y: Int) {
        try {
            // 获取屏幕尺寸
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // 限制在屏幕范围内，允许到达边缘
            val boundedX = x.coerceIn(1, screenWidth - 1)
            val boundedY = y.coerceIn(1, screenHeight - 1)
            
            currentTargetX = boundedX
            currentTargetY = boundedY
            
            // 更新靶心位置 (以靶心中心为锚点)
            targetCrosshairView?.let { crosshair ->
                val params = crosshair.layoutParams as WindowManager.LayoutParams
                params.x = boundedX - 30 // 靶心中心偏移
                params.y = boundedY - 30
                
                try {
                    windowManager?.updateViewLayout(crosshair, params)
                } catch (e: Exception) {
                    // 忽略更新失败
                }
            }
            
            // 更新坐标显示
            coordinateDisplayView?.text = "X: $boundedX, Y: $boundedY"
            
            // 边缘震动反馈
            if (boundedX <= 5 || boundedX >= screenWidth - 5 || 
                boundedY <= 5 || boundedY >= screenHeight - 5) {
                // 这里可以添加震动反馈
                // vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 更新靶心位置失败: ${e.message}")
        }
    }
    
    /**
     * 确认坐标选择
     */
    private fun confirmCoordinateSelection(x: Int, y: Int) {
        try {
            // 获取屏幕尺寸
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // 确保坐标在有效范围内
            recordedX = x.coerceIn(1, screenWidth - 1)
            recordedY = y.coerceIn(1, screenHeight - 1)
            
            LogCollector.addLog("I", TAG, "🎯 精确${currentOperationType}定位已记录: ($recordedX, $recordedY)")
            showToast("✅ ${currentOperationType}定位已记录: ($recordedX, $recordedY)")
            
            // 延迟停止记录，让用户看到确认信息
            handler.postDelayed({
                stopCoordinateRecording()
            }, 800)
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 确认坐标选择失败: ${e.message}")
        }
    }
    

    
    /**
     * 保存记录的坐标
     */
    private fun saveRecordedCoordinates() {
        try {
            // 通知AIAccessibilityService保存坐标（兼容旧系统）
            val accessibilityService = AIAccessibilityService.getInstance()
            accessibilityService?.saveCustomCoordinates(targetPackageName, recordedX, recordedY)
            
            // 保存到新的定位记录系统
            val appName = taskStats?.appName ?: currentTaskConfig?.appName ?: "未知应用"
            val type = if (currentTaskConfig?.isLiveMode == true) "直播" else "普通"
            
            // 获取或创建定位记录
            var locationRecord = LocationRecord.getLocationRecord(this, targetPackageName)
            if (locationRecord == null) {
                locationRecord = LocationRecord(
                    packageName = targetPackageName,
                    appName = appName,
                    type = type
                )
            }
            
            // 添加操作定位
            locationRecord = locationRecord.addOperation(currentOperationType, recordedX, recordedY)
            
            // 保存定位记录
            LocationRecord.saveLocationRecord(this, locationRecord)
            
            LogCollector.addLog("I", TAG, "📍 ${currentOperationType}定位已保存: $appName ($targetPackageName) - ($recordedX, $recordedY)")
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 保存定位失败: ${e.message}")
        }
    }
    
    /**
     * 移除坐标记录覆盖层
     */
    private fun removeCoordinateOverlay() {
        try {
            // 移除遮罩层
            maskOverlayView?.let { view ->
                windowManager?.removeView(view)
                maskOverlayView = null
            }
            
            // 移除靶心指示器
            targetCrosshairView?.let { view ->
                windowManager?.removeView(view)
                targetCrosshairView = null
            }
            
            // 移除坐标显示器
            coordinateDisplayView?.let { view ->
                windowManager?.removeView(view)
                coordinateDisplayView = null
            }
            
            coordinateOverlayView = null
            
            LogCollector.addLog("I", TAG, "🧹 定位记录界面已清理")
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 移除坐标覆盖层失败: ${e.message}")
        }
    }

    /**
     * 创建滑动设置区域
     */
    private fun createSwipeSettingsSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 8)
            
            // 滑动详细设置容器 - 先定义
            val swipeDetailsContainer = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 0, 0, 0)
                visibility = if (customSwipeEnabled) View.VISIBLE else View.GONE
            }
            
            // 滑动开关
            val swipeToggleContainer = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 8)
            }
            
            val swipeLabel = TextView(this@StatusFloatingService).apply {
                text = "🔄 启用滑动"
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val swipeSwitch = android.widget.Switch(this@StatusFloatingService).apply {
                isChecked = customSwipeEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    customSwipeEnabled = isChecked
                    swipeDetailsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
                }
            }
            
            swipeToggleContainer.addView(swipeLabel)
            swipeToggleContainer.addView(swipeSwitch)
            addView(swipeToggleContainer)
            
            // 滑动次数设置
            val swipeCountContainer = createSliderSection(
                "🔄 滑动次数",
                customSwipeCount,
                1, 100,
                "次"
            ) { newValue ->
                customSwipeCount = newValue
                LogCollector.addLog("I", TAG, "🔄 滑动次数设置为: $customSwipeCount")
            }
            swipeDetailsContainer.addView(swipeCountContainer)
            
            // 滑动间隔设置
            val swipeIntervalContainer = createIntervalSliderSection(
                "⏰ 滑动间隔",
                customSwipeInterval,
                1000L, 20000L
            ) { newValue ->
                customSwipeInterval = newValue
                LogCollector.addLog("I", TAG, "⏰ 滑动间隔设置为: ${customSwipeInterval}ms")
            }
            swipeDetailsContainer.addView(swipeIntervalContainer)
            
            addView(swipeDetailsContainer)
        }
    }

    /**
     * 创建滑动条设置区域
     */
    private fun createSliderSection(
        label: String,
        currentValue: Int,
        minValue: Int,
        maxValue: Int,
        unit: String,
        onValueChanged: (Int) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            
            // 标签和当前值显示
            val labelContainer = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 8)
            }
            
            val labelView = TextView(this@StatusFloatingService).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#555555"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val valueView = TextView(this@StatusFloatingService).apply {
                text = "$currentValue$unit"
                textSize = 16f
                setTextColor(Color.parseColor("#2196F3"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.END
            }
            
            labelContainer.addView(labelView)
            labelContainer.addView(valueView)
            addView(labelContainer)
            
            // 滑动条
            val seekBar = android.widget.SeekBar(this@StatusFloatingService).apply {
                max = maxValue - minValue
                progress = currentValue - minValue
                
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val newValue = progress + minValue
                            valueView.text = "$newValue$unit"
                            onValueChanged(newValue)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                })
            }
            
            addView(seekBar)
            
            // 范围提示
            val rangeHint = TextView(this@StatusFloatingService).apply {
                text = "范围: $minValue - $maxValue $unit"
                textSize = 11f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }
            addView(rangeHint)
        }
    }
    
    /**
     * 创建间隔滑动条设置区域
     */
    private fun createIntervalSliderSection(
        label: String,
        currentValue: Long,
        minValue: Long,
        maxValue: Long,
        onValueChanged: (Long) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            
            // 标签和当前值显示
            val labelContainer = LinearLayout(this@StatusFloatingService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 8)
            }
            
            val labelView = TextView(this@StatusFloatingService).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#555555"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val valueView = TextView(this@StatusFloatingService).apply {
                text = formatIntervalTime(currentValue)
                textSize = 16f
                setTextColor(Color.parseColor("#2196F3"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.END
            }
            
            labelContainer.addView(labelView)
            labelContainer.addView(valueView)
            addView(labelContainer)
            
            // 滑动条
            val seekBar = android.widget.SeekBar(this@StatusFloatingService).apply {
                max = ((maxValue - minValue) / 100).toInt() // 以100ms为步长
                progress = ((currentValue - minValue) / 100).toInt()
                
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val newValue = minValue + (progress * 100)
                            valueView.text = formatIntervalTime(newValue)
                            onValueChanged(newValue)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                })
            }
            
            addView(seekBar)
            
            // 范围提示
            val rangeHint = TextView(this@StatusFloatingService).apply {
                text = "范围: ${formatIntervalTime(minValue)} - ${formatIntervalTime(maxValue)}"
                textSize = 11f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }
            addView(rangeHint)
        }
    }
} 