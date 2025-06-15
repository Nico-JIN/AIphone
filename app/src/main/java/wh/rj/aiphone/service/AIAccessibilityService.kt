package wh.rj.aiphone.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import wh.rj.aiphone.R
import wh.rj.aiphone.model.ElementInfo
import wh.rj.aiphone.model.TaskConfig
import wh.rj.aiphone.model.TaskStats
import wh.rj.aiphone.utils.ElementDetector
import wh.rj.aiphone.utils.GestureHelper
import wh.rj.aiphone.utils.ImageRecognitionHelper
import wh.rj.aiphone.utils.LogCollector
import wh.rj.aiphone.utils.ScreenshotHelper
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * 核心无障碍服务
 * 提供自动化操作的核心功能，包括元素检测、手势操作、任务执行等
 */
class AIAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityService"
        private const val CHANNEL_ID = "AutomationServiceChannel"
        private const val NOTIFICATION_ID = 1
        
        /** 服务实例 */
        @Volatile
        private var instance: AIAccessibilityService? = null
        
        /** 获取服务实例 */
        fun getInstance(): AIAccessibilityService? = instance
    }

    // ========== 核心组件 ==========
    /** 元素检测器 */
    private lateinit var elementDetector: ElementDetector
    
    /** 手势操作助手 */
    private lateinit var gestureHelper: GestureHelper
    
    /** 截图助手 */
    private lateinit var screenshotHelper: ScreenshotHelper
    
    /** 图像识别助手 */
    private lateinit var imageRecognitionHelper: ImageRecognitionHelper
    
    /** 协程作用域 */
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /** 主线程Handler */
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /** 通知管理器 */
    private var notificationManager: NotificationManager? = null

    // ========== 任务状态 ==========
    /** 当前任务配置 */
    private var currentTaskConfig: TaskConfig? = null
    
    /** 任务是否正在运行 */
    private var isTaskRunning = false
    
    /** 操作计数器 */
    private var operationCount = 0
    
    /** 最后操作时间 */
    private var lastOperationTime = 0L
    
    /** 任务开始时间 */
    private var taskStartTime = 0L
    
    /** 任务统计数据 */
    private var taskStats: TaskStats? = null

    // ========== 自定义坐标管理 ==========
    
    /** 自定义坐标存储 */
    private val customCoordinates = mutableMapOf<String, Pair<Int, Int>>()
    
    /**
     * 保存自定义坐标
     * @param packageName 应用包名
     * @param x X坐标
     * @param y Y坐标
     */
    fun saveCustomCoordinates(packageName: String, x: Int, y: Int) {
        try {
            customCoordinates[packageName] = Pair(x, y)
            
            // 保存到SharedPreferences
            val prefs = getSharedPreferences("custom_coordinates", MODE_PRIVATE)
            prefs.edit()
                .putInt("${packageName}_x", x)
                .putInt("${packageName}_y", y)
                .putBoolean("${packageName}_enabled", true)
                .apply()
            
            LogCollector.addLog("I", TAG, "📍 保存自定义坐标: $packageName ($x, $y)")
            
            // 更新当前任务配置
            if (currentTaskConfig?.packageName == packageName) {
                currentTaskConfig = currentTaskConfig?.copy(
                    customLikeX = x,
                    customLikeY = y,
                    useCustomCoordinates = true,
                    customCoordinatesName = "用户记录的点赞位置"
                )
                LogCollector.addLog("I", TAG, "📍 已更新当前任务配置使用自定义坐标")
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 保存自定义坐标失败: ${e.message}")
        }
    }
    
    /**
     * 获取自定义坐标
     * @param packageName 应用包名
     * @return 坐标对象，如果不存在返回null
     */
    fun getCustomCoordinates(packageName: String): Pair<Int, Int>? {
        try {
            // 先从内存中获取
            customCoordinates[packageName]?.let { return it }
            
            // 从SharedPreferences获取
            val prefs = getSharedPreferences("custom_coordinates", MODE_PRIVATE)
            val enabled = prefs.getBoolean("${packageName}_enabled", false)
            if (enabled) {
                val x = prefs.getInt("${packageName}_x", -1)
                val y = prefs.getInt("${packageName}_y", -1)
                if (x > 0 && y > 0) {
                    val coordinates = Pair(x, y)
                    customCoordinates[packageName] = coordinates
                    return coordinates
                }
            }
            
            return null
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 获取自定义坐标失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 清除自定义坐标
     * @param packageName 应用包名
     */
    fun clearCustomCoordinates(packageName: String) {
        try {
            customCoordinates.remove(packageName)
            
            val prefs = getSharedPreferences("custom_coordinates", MODE_PRIVATE)
            prefs.edit()
                .remove("${packageName}_x")
                .remove("${packageName}_y")
                .remove("${packageName}_enabled")
                .apply()
            
            LogCollector.addLog("I", TAG, "🗑️ 已清除自定义坐标: $packageName")
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 清除自定义坐标失败: ${e.message}")
        }
    }

    // ========== 服务生命周期 ==========
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化核心组件
        elementDetector = ElementDetector.getInstance()
        gestureHelper = GestureHelper(this)
        screenshotHelper = ScreenshotHelper.getInstance()
        imageRecognitionHelper = ImageRecognitionHelper.getInstance(this)
        
        // 设置无障碍服务给截图助手
        screenshotHelper.setAccessibilityService(this)
        
        // 初始化图像识别组件
        serviceScope.launch {
            try {
                imageRecognitionHelper.initialize()
                LogCollector.addLog("I", TAG, "✅ 图像识别组件初始化完成")
            } catch (e: Exception) {
                LogCollector.addLog("W", TAG, "⚠️ 图像识别组件初始化失败: ${e.message}")
            }
        }
        
        // 初始化通知
        initializeNotification()
        
        LogCollector.addLog("I", TAG, "🚀 无障碍服务已创建")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // 配置无障碍服务信息
        configureAccessibilityService()
        
        // 启动前台服务
        startForegroundService()
        
        LogCollector.addLog("I", TAG, "✅ 无障碍服务已连接并配置完成")
        LogCollector.addLog("I", TAG, "📱 支持手势: ${gestureHelper.isGestureSupported()}")
        LogCollector.addLog("I", TAG, "📏 屏幕尺寸: ${gestureHelper.getScreenSize()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 停止当前任务
        stopCurrentTask()
        
        // 清理资源
        elementDetector.clearCache()
        screenshotHelper.release()
        imageRecognitionHelper.release()
        serviceScope.cancel()
        instance = null
        
        LogCollector.addLog("I", TAG, "🛑 无障碍服务已销毁")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 过滤系统事件
        if (isSystemEvent(event)) {
            return
        }
        
        // 记录事件信息
        logAccessibilityEvent(event)
        
        // 处理任务相关事件
        if (isTaskRunning && currentTaskConfig != null) {
            handleTaskEvent(event)
        }
    }

    override fun onInterrupt() {
        LogCollector.addLog("W", TAG, "⚠️ 无障碍服务被中断")
        stopCurrentTask()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleServiceCommand(it) }
        return START_STICKY
    }

    // ========== 核心API接口 ==========
    
    /**
     * 启动自动化任务
     * @param packageName 目标应用包名
     * @param config 任务配置，如果为null则使用默认配置
     * @return 是否启动成功
     */
    fun startAutomationTask(packageName: String, config: TaskConfig? = null): Boolean {
        try {
            LogCollector.addLog("I", TAG, "🎯 启动自动化任务: $packageName")
            
            stopCurrentTask()
            
            currentTaskConfig = config ?: TaskConfig.getDefaultByPackage(packageName, getAppName(packageName))
            isTaskRunning = true
            operationCount = 0
            taskStartTime = System.currentTimeMillis()
            lastOperationTime = System.currentTimeMillis()
            
            // 初始化任务统计（失败不影响主功能）
            try {
                taskStats = TaskStats(
                    packageName = packageName,
                    appName = currentTaskConfig?.appName ?: getAppName(packageName)
                ).apply {
                    startTask(currentTaskConfig?.maxOperations ?: 50)
                }
            } catch (e: Exception) {
                LogCollector.addLog("W", TAG, "⚠️ 初始化统计功能失败，但不影响主功能: ${e.message}")
                taskStats = null
            }
            
            updateNotification("🎯 任务运行中: ${currentTaskConfig?.appName}")
            scheduleNextOperation()
            
            LogCollector.addLog("I", TAG, "✅ 任务启动成功: ${currentTaskConfig?.getSummary()}")
            return true
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 任务启动失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 停止当前任务
     * @return 是否停止成功
     */
    fun stopCurrentTask(): Boolean {
        if (!isTaskRunning) {
            LogCollector.addLog("W", TAG, "⚠️ 没有正在运行的任务")
            return false
        }
        
        try {
            isTaskRunning = false
            
            val taskDuration = System.currentTimeMillis() - taskStartTime
            val summary = "操作次数: $operationCount | 运行时长: ${taskDuration/1000}秒"
            
            LogCollector.addLog("I", TAG, "🛑 任务已停止 - $summary")
            updateNotification("⏸️ 任务已停止")
            
            // 停止任务统计（安全操作）
            try {
                taskStats?.stopTask()
            } catch (e: Exception) {
                LogCollector.addLog("W", TAG, "⚠️ 停止统计功能失败: ${e.message}")
            }
            
            currentTaskConfig = null
            operationCount = 0
            taskStartTime = 0L
            taskStats = null
            
            return true
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 停止任务失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 检测页面元素
     * @param useCache 是否使用缓存
     * @return 检测到的元素列表
     */
    fun detectPageElements(useCache: Boolean = true): List<ElementInfo> {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            LogCollector.addLog("W", TAG, "❌ 无法获取页面根节点")
            return emptyList()
        }
        
        LogCollector.addLog("I", TAG, "🔍 开始检测页面元素...")
        return elementDetector.detectAllElements(rootNode, useCache)
    }
    
    /**
     * 搜索特定元素
     * @param keyword 搜索关键词
     * @param elementType 元素类型过滤
     * @return 匹配的元素列表
     */
    fun searchElements(keyword: String, elementType: ElementInfo.ElementType? = null): List<ElementInfo> {
        val allElements = detectPageElements()
        return elementDetector.searchElements(allElements, keyword, elementType)
    }

    /**
     * 执行手势滑动
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param endX 结束X坐标
     * @param endY 结束Y坐标
     * @param duration 持续时间
     * @return 是否成功
     */
    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long = 300L): Boolean {
        LogCollector.addLog("I", TAG, "📱 执行滑动操作: ($startX,$startY) -> ($endX,$endY)")
        return gestureHelper.swipe(startX, startY, endX, endY, duration)
    }
    
    /**
     * 执行向上滑动 (适用于短视频)
     * @return 是否成功
     */
    fun performSwipeUp(): Boolean {
        val screenSize = gestureHelper.getScreenSize()
        val success = gestureHelper.swipeUp(screenSize.x, screenSize.y)
        if (success) {
            // 安全地更新统计信息，不影响主功能
            try {
                taskStats?.updateStats(swipes = 1)
                taskStats?.let { StatusFloatingService.updateStats(it) }
            } catch (e: Exception) {
                LogCollector.addLog("W", TAG, "⚠️ 更新统计失败，但不影响主功能: ${e.message}")
            }
        }
        return success
    }
    
    /**
     * 执行直播模式特殊滑动 (上滑+下滑为一次)
     * @return 是否成功
     */
    fun performLiveSwipeUpDown(): Boolean {
        val screenSize = gestureHelper.getScreenSize()
        val success = gestureHelper.performLiveSwipeUpDown(screenSize.x, screenSize.y)
        if (success) {
            // 安全地更新统计信息，不影响主功能
            try {
                taskStats?.updateStats(swipes = 1)
                taskStats?.let { StatusFloatingService.updateStats(it) }
            } catch (e: Exception) {
                LogCollector.addLog("W", TAG, "⚠️ 更新统计失败，但不影响主功能: ${e.message}")
            }
        }
        return success
    }
    
    /**
     * 执行点赞操作
     * @return 是否成功
     */
    fun performLike(): Boolean {
        LogCollector.addLog("I", TAG, "🎯 开始执行点赞操作")
        
        // 优先检查是否有自定义坐标
        val customCoords = currentTaskConfig?.let { config ->
            if (config.useCustomCoordinates && config.customLikeX > 0 && config.customLikeY > 0) {
                Pair(config.customLikeX, config.customLikeY)
            } else {
                getCustomCoordinates(config.packageName)
            }
        }
        
        if (customCoords != null) {
            LogCollector.addLog("I", TAG, "📍 使用自定义坐标点赞: (${customCoords.first}, ${customCoords.second})")
            val success = gestureHelper.click(customCoords.first, customCoords.second)
            if (success) {
                updateLikeStats(true)
                LogCollector.addLog("I", TAG, "✅ 自定义坐标点赞成功")
            } else {
                LogCollector.addLog("W", TAG, "⚠️ 自定义坐标点赞失败")
            }
            return success
        }
        
        // 如果没有自定义坐标，根据模式执行不同逻辑
        return if (currentTaskConfig?.isLiveMode == true) {
            // 直播模式：使用坐标点击
            performLiveLike()
        } else {
            // 普通模式：使用元素检测
            performNormalLike()
        }
    }
    
    /**
     * 执行直播模式点赞 - 只使用坐标点击
     * @return 是否成功
     */
    private fun performLiveLike(): Boolean {
        LogCollector.addLog("I", TAG, "📺 直播模式：开始坐标点击点赞")
        
        // 首先尝试获取自定义坐标
        val customCoords = currentTaskConfig?.let { config ->
            getCustomCoordinates(config.packageName)
        }
        
        if (customCoords != null) {
            LogCollector.addLog("I", TAG, "📍 使用已保存的自定义坐标: (${customCoords.first}, ${customCoords.second})")
            val success = gestureHelper.click(customCoords.first, customCoords.second)
            if (success) {
                updateLikeStats(true)
                LogCollector.addLog("I", TAG, "✅ 自定义坐标点赞成功")
            } else {
                LogCollector.addLog("W", TAG, "⚠️ 自定义坐标点赞失败")
            }
            return success
        }
        
        // 如果没有自定义坐标，提醒用户先记录坐标
        LogCollector.addLog("W", TAG, "⚠️ 直播模式未找到自定义坐标，建议先使用悬浮窗记录点赞位置以获得最佳效果")
        LogCollector.addLog("I", TAG, "🔄 使用默认位置进行点赞")
        
        // 获取屏幕尺寸，使用默认点击位置
        val screenSize = gestureHelper.getScreenSize()
        val clickX = (screenSize.x * 0.9).toInt() // 右侧90%位置
        val clickY = (screenSize.y * 0.65).toInt() // 下方65%位置
        
        val success = gestureHelper.click(clickX, clickY)
        if (success) {
            LogCollector.addLog("I", TAG, "✅ 默认位置点击成功: ($clickX, $clickY)")
            updateLikeStats(true)
        } else {
            LogCollector.addLog("W", TAG, "⚠️ 默认位置点击失败")
        }
        return success
    }
    
    /**
     * 执行普通模式点赞
     * @return 是否成功
     */
    private fun performNormalLike(): Boolean {
        val elements = detectPageElements()
        val likeButton = elementDetector.findBestLikeButton(elements)
        
        return if (likeButton != null) {
            LogCollector.addLog("I", TAG, "👍 执行点赞操作")
            val success = gestureHelper.clickElement(likeButton)
            updateLikeStats(success)
            success
        } else {
            LogCollector.addLog("W", TAG, "⚠️ 未找到点赞按钮")
            false
        }
    }
    
    /**
     * 更新点赞统计
     * @param success 是否成功
     */
    private fun updateLikeStats(success: Boolean) {
        if (success) {
            // 安全地更新统计信息，不影响主功能
            try {
                taskStats?.updateStats(likes = 1)
                taskStats?.let { StatusFloatingService.updateStats(it) }
            } catch (e: Exception) {
                LogCollector.addLog("W", TAG, "⚠️ 更新统计失败，但不影响主功能: ${e.message}")
            }
        }
    }
    
    /**
     * 点击最佳关注按钮  
     * @return 是否成功
     */
    fun performFollow(): Boolean {
        val elements = detectPageElements()
        val followButton = elementDetector.findBestFollowButton(elements)
        
        return if (followButton != null) {
            LogCollector.addLog("I", TAG, "➕ 执行关注操作")
            val success = gestureHelper.clickElement(followButton)
            if (success) {
                // 安全地更新统计信息，不影响主功能
                try {
                    taskStats?.updateStats(follows = 1)
                    taskStats?.let { StatusFloatingService.updateStats(it) }
                } catch (e: Exception) {
                    LogCollector.addLog("W", TAG, "⚠️ 更新统计失败，但不影响主功能: ${e.message}")
                }
            }
            success
        } else {
            LogCollector.addLog("W", TAG, "⚠️ 未找到关注按钮")
            false
        }
    }
    
    /**
     * 点击最佳评论按钮
     * @return 是否成功
     */
    fun performComment(): Boolean {
        val elements = detectPageElements()
        val commentButton = elementDetector.findBestCommentButton(elements)
        
        return if (commentButton != null) {
            LogCollector.addLog("I", TAG, "💬 执行评论操作")
            val success = gestureHelper.clickElement(commentButton)
            if (success) {
                // 安全地更新统计信息，不影响主功能
                try {
                    taskStats?.updateStats(comments = 1)
                    taskStats?.let { StatusFloatingService.updateStats(it) }
                } catch (e: Exception) {
                    LogCollector.addLog("W", TAG, "⚠️ 更新统计失败，但不影响主功能: ${e.message}")
                }
            }
            success
        } else {
            LogCollector.addLog("W", TAG, "⚠️ 未找到评论按钮")
            false
        }
    }
    
    /**
     * 获取当前任务统计数据
     * @return 当前任务统计信息，如果没有任务则返回null
     */
    fun getCurrentTaskStats(): TaskStats? {
        return taskStats
    }
    
    /**
     * 获取当前任务配置
     * @return 当前任务配置信息，如果没有任务则返回null
     */
    fun getCurrentTaskConfig(): TaskConfig? {
        return currentTaskConfig
    }

    // ========== 私有方法 ==========
    
    /**
     * 配置无障碍服务
     */
    private fun configureAccessibilityService() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100L
        }
        serviceInfo = info
    }
    
    /**
     * 初始化通知
     */
    private fun initializeNotification() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "自动化服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示自动化服务的运行状态"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🤖 AI自动化助手")
            .setContentText("服务已就绪，等待任务指令")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * 更新通知内容
     */
    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🤖 AI自动化助手")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 处理服务命令
     */
    private fun handleServiceCommand(intent: Intent) {
        try {
            when {
                // 启动任务
                intent.hasExtra("target_package") -> {
                    val packageName = intent.getStringExtra("target_package") ?: return
                    val appName = getAppName(packageName)
                    val config = TaskConfig.getDefaultByPackage(packageName, appName)
                    startAutomationTask(packageName, config)
                }
                
                // 检测元素
                intent.getBooleanExtra("detect_elements", false) -> {
                    val elements = detectPageElements(false)
                    LogCollector.addLog("I", TAG, "🔍 检测结果: ${elements.size}个元素")
                    LogCollector.addLog("I", TAG, elementDetector.getStatistics(elements))
                }
                
                // 执行滑动
                intent.getBooleanExtra("force_swipe", false) -> {
                    performSwipeUp()
                }
                
                // 自动点击
                intent.getBooleanExtra("auto_click", false) -> {
                    performLike()
                }
                
                // 停止任务
                intent.getBooleanExtra("stop_task", false) -> {
                    stopCurrentTask()
                }
            }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 处理服务命令失败: ${e.message}")
        }
    }
    
    /**
     * 处理任务事件
     */
    private fun handleTaskEvent(event: AccessibilityEvent) {
        val config = currentTaskConfig ?: return
        val packageName = event.packageName?.toString() ?: return
        
        // 只处理目标应用的事件
        if (packageName != config.packageName) {
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        // 根据模式检查不同的间隔和最大次数
        val interval = if (config.isLiveMode) config.liveLikeInterval else config.swipeInterval
        val maxOps = if (config.isLiveMode) config.liveLikeMaxCount else config.maxOperations
        
        // 检查是否到达操作间隔
        if (currentTime - lastOperationTime < interval) {
            return
        }
        
        // 检查是否到达最大操作次数
        if (operationCount >= maxOps) {
            val mode = if (config.isLiveMode) "直播模式" else "普通模式"
            LogCollector.addLog("I", TAG, "✅ ${mode}已完成设定的操作次数 ($operationCount/$maxOps)，任务结束")
            stopCurrentTask()
            return
        }
        
        // 执行自动操作
        performAutomaticActions(config)
        
        // 更新状态
        operationCount++
        lastOperationTime = currentTime
        
        // 更新通知
        val mode = if (config.isLiveMode) "直播" else "普通"
        updateNotification("🎯 ${config.appName}($mode) - 操作: $operationCount/$maxOps")
    }
    
    /**
     * 执行自动操作
     */
    private fun performAutomaticActions(config: TaskConfig) {
        try {
            if (config.isLiveMode) {
                // 直播模式：只执行点赞操作
                performLiveModeActions(config)
            } else {
                // 普通模式：执行所有配置的操作
                performNormalModeActions(config)
            }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 执行自动操作失败: ${e.message}")
        }
    }
    
    /**
     * 执行直播模式操作
     */
    private fun performLiveModeActions(config: TaskConfig) {
        LogCollector.addLog("I", TAG, "📺 直播模式：开始执行操作 (间隔: ${config.liveLikeInterval}ms)")
        
        try {
            val enableSwipe = config.enableSwipeTask && config.enableGesture
            val enableLike = config.enableLikeOperation || (!enableSwipe) // 如果没有滑动，默认启用点赞
            
            // 如果同时启用滑动和点赞，按顺序执行：先滑动，后点赞
            if (enableSwipe && enableLike) {
                LogCollector.addLog("I", TAG, "🔄➕👍 直播模式：先执行滑动，后执行点赞")
                
                // 第一步：执行直播特殊滑动（上滑+下滑为一次）
                LogCollector.addLog("I", TAG, "🔄 直播模式：执行上下滑动组合操作")
                val swipeSuccess = performLiveSwipeUpDown()
                if (swipeSuccess) {
                    LogCollector.addLog("I", TAG, "✅ 直播滑动组合操作成功")
                } else {
                    LogCollector.addLog("W", TAG, "⚠️ 直播滑动组合操作失败")
                }
                
                // 等待一段时间再执行点赞
                Thread.sleep(800)
                
                // 第二步：执行点赞操作
                LogCollector.addLog("I", TAG, "👍 直播模式：执行点赞操作")
                val likeSuccess = performLike()
                if (likeSuccess) {
                    LogCollector.addLog("I", TAG, "✅ 直播点赞操作成功")
                } else {
                    LogCollector.addLog("W", TAG, "⚠️ 直播点赞操作失败")
                }
                
            } else if (enableSwipe) {
                // 只执行滑动操作
                LogCollector.addLog("I", TAG, "🔄 直播模式：仅执行上下滑动组合操作")
                val success = performLiveSwipeUpDown()
                if (success) {
                    LogCollector.addLog("I", TAG, "✅ 直播滑动组合操作成功，操作计数+1")
                } else {
                    LogCollector.addLog("W", TAG, "⚠️ 直播滑动组合操作失败")
                }
                
            } else {
                // 只执行点赞操作
                LogCollector.addLog("I", TAG, "👍 直播模式：仅执行点赞操作")
                val success = performLike()
                if (success) {
                    LogCollector.addLog("I", TAG, "✅ 直播点赞执行成功，操作计数+1")
                } else {
                    LogCollector.addLog("W", TAG, "⚠️ 直播点赞执行失败")
                }
            }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 直播模式操作异常: ${e.message}")
        }
    }
    
    /**
     * 执行普通模式操作
     */
    private fun performNormalModeActions(config: TaskConfig) {
        LogCollector.addLog("I", TAG, "📱 普通模式：开始执行操作")
        
        // 随机延迟
        if (config.enableRandomDelay) {
            val delay = Random.nextLong(0, config.randomDelayRange)
            Thread.sleep(delay)
        }
        
        // 随机点赞
        if (config.enableLikeOperation && Random.nextInt(100) < config.likeChance) {
            LogCollector.addLog("I", TAG, "👍 普通模式：执行点赞操作")
            performLike()
            // 减少点赞后的延迟时间
            gestureHelper.humanLikeDelay(200L, 100L)
        }
        
        // 随机关注
        if (config.enableFollowOperation && Random.nextInt(100) < config.followChance) {
            LogCollector.addLog("I", TAG, "➕ 普通模式：执行关注操作")
            performFollow()
            // 减少关注后的延迟时间
            gestureHelper.humanLikeDelay(300L, 150L)
        }
        
        // 随机评论
        if (config.enableCommentOperation && Random.nextInt(100) < config.commentChance) {
            LogCollector.addLog("I", TAG, "💬 普通模式：执行评论操作")
            performComment()
            // 减少评论后的延迟时间
            gestureHelper.humanLikeDelay(400L, 200L)
        }
        
        // 滑动到下一个内容 - 修复滑动逻辑
        if (config.enableSwipeTask && config.enableGesture) {
            LogCollector.addLog("I", TAG, "🔄 普通模式：执行滑动操作")
            // 减少滑动前的等待时间
            gestureHelper.humanLikeDelay(300L, 100L)
            val swipeSuccess = performSwipeUp()
            if (swipeSuccess) {
                LogCollector.addLog("I", TAG, "✅ 普通模式滑动成功")
            } else {
                LogCollector.addLog("W", TAG, "⚠️ 普通模式滑动失败")
            }
        } else {
            LogCollector.addLog("I", TAG, "⚠️ 普通模式滑动未启用: enableSwipeTask=${config.enableSwipeTask}, enableGesture=${config.enableGesture}")
        }
    }
    
    /**
     * 调度下一次操作
     */
    private fun scheduleNextOperation() {
        if (!isTaskRunning) return
        
        val config = currentTaskConfig ?: return
        
        val interval = if (config.isLiveMode) {
            config.liveLikeInterval
        } else {
            config.swipeInterval
        }
        
        val maxOps = if (config.isLiveMode) {
            config.liveLikeMaxCount
        } else {
            config.maxOperations
        }
        
        mainHandler.postDelayed({
            if (isTaskRunning && operationCount < maxOps) {
                scheduleNextOperation()
            }
        }, interval)
    }
    
    /**
     * 判断是否为系统事件
     */
    private fun isSystemEvent(event: AccessibilityEvent): Boolean {
        val packageName = event.packageName?.toString() ?: return true
        
        return packageName.contains("systemui") ||
               packageName.contains("talkback") ||
               packageName.contains("com.android.system") ||
               packageName == "android"
    }
    
    /**
     * 记录无障碍事件
     */
    private fun logAccessibilityEvent(event: AccessibilityEvent) {
        if (LogCollector.isDebugMode()) {
            val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
            LogCollector.addLog("D", TAG, "📱 事件: $eventType | 包名: ${event.packageName}")
        }
    }
    
    /**
     * 获取应用名称
     */
    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
} 