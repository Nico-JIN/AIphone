package wh.rj.aiphone.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import wh.rj.aiphone.R
import wh.rj.aiphone.utils.LogCollector

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var targetPackage: String = ""
    private val CHANNEL_ID = "FloatingWindowChannel"
    private var isExpanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var resultTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var elementCountTextView: TextView? = null
    
    companion object {
        private var instance: FloatingWindowService? = null
        private const val TAG = "FloatingWindow"
        
        fun getInstance(): FloatingWindowService? = instance
        
        fun stopExistingService() {
            instance?.stopSelf()
            instance = null
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        // 停止之前的实例
        stopExistingService()
        instance = this
        
        createNotificationChannel()
        LogCollector.addLog("I", TAG, "🚀 悬浮窗服务重新启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogCollector.addLog("I", TAG, "🎯 悬浮窗服务启动命令")
        
        targetPackage = intent?.getStringExtra("target_package") ?: ""
        LogCollector.addLog("I", TAG, "📱 目标应用：$targetPackage")
        
        if (Settings.canDrawOverlays(this)) {
            createNewFloatingWindow()
            startForegroundNotification()
            LogCollector.addLog("I", TAG, "✅ 悬浮窗启动成功")
        } else {
            LogCollector.addLog("E", TAG, "❌ 缺少悬浮窗权限")
            Toast.makeText(this, "❌ 缺少悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        
        return START_STICKY
    }

    private fun createNewFloatingWindow() {
        try {
            LogCollector.addLog("I", TAG, "🔧 开始创建新的 Console 悬浮窗")
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 创建主容器 - Console 风格
            val mainContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                setPadding(0, 0, 0, 0)
                elevation = 12f
            }
            
            // Console 标题栏
            val titleBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#2D2D30"))
                setPadding(12, 8, 8, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            val titleText = TextView(this).apply {
                text = "🔍 Element Inspector Console"
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val closeButton = Button(this).apply {
                text = "×"
                setTextColor(Color.parseColor("#CCCCCC"))
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(8, 4, 8, 4)
                textSize = 16f
                setOnClickListener {
                    LogCollector.addLog("I", TAG, "❌ 关闭 Console")
                    stopSelf()
                }
            }
            
            titleBar.addView(titleText)
            titleBar.addView(closeButton)
            
            // Console 工具栏
            val toolBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#2D2D30"))
                setPadding(8, 8, 8, 8)
            }
            
            val scanButton = Button(this).apply {
                text = "Scan"
                setTextColor(Color.parseColor("#00FF00"))
                setBackgroundColor(Color.parseColor("#3C3C3C"))
                setPadding(12, 6, 12, 6)
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener {
                    addConsoleLog("[SCAN] Starting element detection...")
                    performDetection()
                }
            }
            
            val clickButton = Button(this).apply {
                text = "Click"
                setTextColor(Color.parseColor("#00BFFF"))
                setBackgroundColor(Color.parseColor("#3C3C3C"))
                setPadding(12, 6, 12, 6)
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener {
                    addConsoleLog("[CLICK] Testing click function...")
                    performTestLike()
                }
            }
            
            val swipeButton = Button(this).apply {
                text = "Swipe"
                setTextColor(Color.parseColor("#FFD700"))
                setBackgroundColor(Color.parseColor("#3C3C3C"))
                setPadding(12, 6, 12, 6)
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener {
                    addConsoleLog("[SWIPE] Testing swipe function...")
                    performSwipe()
                }
            }
            
            toolBar.addView(scanButton)
            toolBar.addView(clickButton)
            toolBar.addView(swipeButton)
            
            // Console 搜索栏
            val searchBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#252526"))
                setPadding(8, 8, 8, 8)
            }
            
            val filterLabel = TextView(this).apply {
                text = "Filter:"
                setTextColor(Color.parseColor("#569CD6"))
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 0, 8, 0)
            }
            
            val searchInput = android.widget.EditText(this).apply {
                hint = "Search elements..."
                setTextColor(Color.parseColor("#CCCCCC"))
                setHintTextColor(Color.parseColor("#666666"))
                setBackgroundColor(Color.parseColor("#3C3C3C"))
                setPadding(6, 4, 6, 4)
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val goButton = Button(this).apply {
                text = "Go"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#007ACC"))
                setPadding(8, 4, 8, 4)
                textSize = 9f
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener {
                    val keyword = searchInput.text.toString()
                    addConsoleLog("[SEARCH] Filtering elements: $keyword")
                    performSearch(keyword)
                }
            }
            
            searchBar.addView(filterLabel)
            searchBar.addView(searchInput)
            searchBar.addView(goButton)
            
            // Console 输出区域
            val outputScrollView = ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(240)
                )
            }
            
            val resultText = TextView(this).apply {
                text = "[INFO] Element Inspector Console initialized\n[DEBUG] Target: ${getAppName(targetPackage)}\n[DEBUG] Waiting for scan command..."
                setTextColor(Color.parseColor("#00FF00"))
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(8, 8, 8, 8)
                setLineSpacing(1f, 1f)
                setTextIsSelectable(true)
            }
            
            outputScrollView.addView(resultText)
            
            // Console 状态栏
            val statusBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#007ACC"))
                setPadding(8, 4, 8, 4)
            }
            
            val statusText = TextView(this).apply {
                text = "Ready"
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val elementCountText = TextView(this).apply {
                text = "Elements: 0"
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            
            statusBar.addView(statusText)
            statusBar.addView(elementCountText)
            
            // 组装界面
            mainContainer.addView(titleBar)
            mainContainer.addView(toolBar)
            mainContainer.addView(searchBar)
            mainContainer.addView(outputScrollView)
            mainContainer.addView(statusBar)
            
            // 保存引用以便后续更新
            resultTextView = resultText
            statusTextView = statusText
            elementCountTextView = elementCountText
            
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
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 100
            }

            windowManager?.addView(floatingView, params)
            
            // 设置拖拽功能
            setupDragListener(mainContainer, titleText)
            
            LogCollector.addLog("I", TAG, "✅ Console 悬浮窗创建成功")
            showToast("✅ Element Inspector Console 已启动")
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ Console 悬浮窗创建失败：${e.message}")
            showToast("❌ Console 悬浮窗创建失败")
        }
    }
    
    private fun addConsoleLog(message: String) {
        handler.post {
            resultTextView?.let { textView ->
                val currentText = textView.text.toString()
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val newText = "$currentText\n[$timestamp] $message"
                textView.text = newText
                
                // 滚动到底部
                (textView.parent as? ScrollView)?.post {
                    (textView.parent as ScrollView).fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }
    
    private fun updateStatus(status: String) {
        handler.post {
            statusTextView?.text = status
        }
    }
    
    private fun updateElementCount(count: Int) {
        handler.post {
            elementCountTextView?.text = "Elements: $count"
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    private fun performDetection() {
        LogCollector.addLog("I", TAG, "🔍 开始执行页面元素检测")
        updateStatus("Scanning...")
        addConsoleLog("[SCAN] Starting element detection for ${getAppName(targetPackage)}")
        
        try {
            // 获取无障碍服务实例
            val accessibilityService = wh.rj.aiphone.service.AIAccessibilityService.getInstance()
            if (accessibilityService == null) {
                addConsoleLog("[ERROR] Accessibility service not available")
                updateStatus("Service Error")
                showToast("❌ 无障碍服务未启动")
                return
            }
            
            addConsoleLog("[DEBUG] Accessibility service connected")
            
            // 执行元素检测
            val elements = accessibilityService.detectPageElements(false)
            
            if (elements.isEmpty()) {
                addConsoleLog("[WARN] No elements detected - page may still be loading")
                updateStatus("No Elements")
                updateElementCount(0)
                return
            }
            
            // 显示检测结果
            addConsoleLog("[SUCCESS] Found ${elements.size} elements")
            updateStatus("Ready")
            updateElementCount(elements.size)
            showDetectionResults(elements)
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 检测执行失败：${e.message}")
            addConsoleLog("[ERROR] Detection failed: ${e.message}")
            updateStatus("Error")
            showToast("❌ 检测失败：${e.message}")
        }
    }
    
    private fun performSearch(keyword: String) {
        if (keyword.isBlank()) {
            showToast("⚠️ 请输入搜索关键词")
            return
        }
        
        LogCollector.addLog("I", TAG, "🔎 开始搜索元素: $keyword")
        addConsoleLog("[SEARCH] Searching for: $keyword")
        updateStatus("Searching...")
        
        try {
            val accessibilityService = wh.rj.aiphone.service.AIAccessibilityService.getInstance()
            if (accessibilityService == null) {
                addConsoleLog("[ERROR] Accessibility service not available")
                updateStatus("Service Error")
                showToast("❌ 无障碍服务未启动")
                return
            }
            
            // 执行搜索
            val searchResults = accessibilityService.searchElements(keyword)
            
            if (searchResults.isEmpty()) {
                addConsoleLog("[WARN] No matching elements found for: $keyword")
                updateStatus("No Matches")
                updateElementCount(0)
                showToast("❌ 未找到匹配元素")
                return
            }
            
            // 显示搜索结果
            addConsoleLog("[SUCCESS] Found ${searchResults.size} matching elements")
            updateStatus("Ready")
            updateElementCount(searchResults.size)
            showSearchResults(keyword, searchResults)
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 搜索失败：${e.message}")
            addConsoleLog("[ERROR] Search failed: ${e.message}")
            updateStatus("Error")
            showToast("❌ 搜索失败：${e.message}")
        }
    }
    
    private fun performTestLike() {
        LogCollector.addLog("I", TAG, "👍 测试点赞功能")
        addConsoleLog("[CLICK] Testing like button click...")
        updateStatus("Clicking...")
        
        try {
            val accessibilityService = wh.rj.aiphone.service.AIAccessibilityService.getInstance()
            if (accessibilityService == null) {
                addConsoleLog("[ERROR] Accessibility service not available")
                updateStatus("Service Error")
                showToast("❌ 无障碍服务未启动")
                return
            }
            
            val success = accessibilityService.performLike()
            if (success) {
                addConsoleLog("[SUCCESS] Like button clicked successfully")
                updateStatus("Ready")
                showToast("✅ 点赞成功")
            } else {
                addConsoleLog("[WARN] Like button not found or click failed")
                updateStatus("Click Failed")
                showToast("⚠️ 未找到点赞按钮")
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 点赞测试失败：${e.message}")
            addConsoleLog("[ERROR] Like test failed: ${e.message}")
            updateStatus("Error")
            showToast("❌ 点赞测试失败")
        }
    }
    
    private fun performSwipe() {
        LogCollector.addLog("I", TAG, "📱 开始执行滑动操作")
        addConsoleLog("[SWIPE] Executing swipe gesture...")
        updateStatus("Swiping...")
        
        try {
            val intent = Intent(this, wh.rj.aiphone.service.AIAccessibilityService::class.java)
            intent.putExtra("force_swipe", true)
            intent.putExtra("target_package", targetPackage)
            startService(intent)
            
            LogCollector.addLog("I", TAG, "✅ 滑动命令已发送到无障碍服务")
            addConsoleLog("[DEBUG] Swipe command sent to accessibility service")
            
            // 延迟更新状态
            handler.postDelayed({
                addConsoleLog("[SUCCESS] Swipe gesture completed")
                updateStatus("Ready")
                showToast("✅ 滑动操作完成")
            }, 2000)
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 滑动执行失败：${e.message}")
            addConsoleLog("[ERROR] Swipe failed: ${e.message}")
            updateStatus("Error")
            showToast("❌ 滑动失败：${e.message}")
        }
    }
    
    private fun performClick() {
        LogCollector.addLog("I", TAG, "👆 开始执行点击操作")
        showToast("👆 正在执行点击...")
        updateResultText("👆 正在执行点击操作...\n🎯 自动查找可点击元素")
        
        try {
            val intent = Intent(this, wh.rj.aiphone.service.AIAccessibilityService::class.java)
            intent.putExtra("auto_click", true)
            intent.putExtra("target_package", targetPackage)
            startService(intent)
            
            LogCollector.addLog("I", TAG, "✅ 点击命令已发送到无障碍服务")
            
            // 延迟更新状态
            handler.postDelayed({
                updateResultText("✅ 点击操作已完成\n🎯 目标: ${getAppName(targetPackage)}")
                showToast("✅ 点击操作完成")
            }, 2000)
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 点击执行失败：${e.message}")
            showToast("❌ 点击失败：${e.message}")
            updateResultText("❌ 点击失败：${e.message}")
        }
    }
    
    private fun showSettings() {
        LogCollector.addLog("I", TAG, "⚙️ 显示设置面板")
        addConsoleLog("========== SYSTEM INFO ==========")
        addConsoleLog("Target app: ${getAppName(targetPackage)}")
        addConsoleLog("Package: $targetPackage")
        addConsoleLog("Console status: Active")
        addConsoleLog("Functions: All available")
        addConsoleLog("========== INFO COMPLETE ==========")
        showToast("⚙️ 设置面板")
    }
    
    private fun showDetectionResults(elements: List<wh.rj.aiphone.model.ElementInfo>) {
        LogCollector.addLog("I", TAG, "📋 显示检测结果: ${elements.size}个元素")
        
        // 按重要性显示前8个重要元素
        val importantElements = elements.filter { it.isImportant() }.take(8)
        
        addConsoleLog("========== ELEMENT SCAN RESULTS ==========")
        addConsoleLog("Total elements found: ${elements.size}")
        addConsoleLog("Important elements: ${importantElements.size}")
        
        if (importantElements.isNotEmpty()) {
            addConsoleLog("---------- TOP ELEMENTS ----------")
            importantElements.forEach { element ->
                val typeStr = when (element.elementType) {
                    wh.rj.aiphone.model.ElementInfo.ElementType.LIKE_BUTTON -> "LIKE"
                    wh.rj.aiphone.model.ElementInfo.ElementType.FOLLOW_BUTTON -> "FOLLOW"
                    wh.rj.aiphone.model.ElementInfo.ElementType.COMMENT_BUTTON -> "COMMENT"
                    wh.rj.aiphone.model.ElementInfo.ElementType.SHARE_BUTTON -> "SHARE"
                    wh.rj.aiphone.model.ElementInfo.ElementType.PLAY_BUTTON -> "PLAY"
                    wh.rj.aiphone.model.ElementInfo.ElementType.USER_AVATAR -> "AVATAR"
                    else -> "OTHER"
                }
                
                val displayText = element.text?.take(20) ?: element.contentDescription?.take(20) ?: "no_text"
                addConsoleLog("[$typeStr] \"$displayText\" at (${element.centerX},${element.centerY})")
            }
            
            // 显示统计信息
            val typeStats = elements.groupBy { it.elementType }
            addConsoleLog("---------- ELEMENT STATS ----------")
            typeStats.forEach { (type, list) ->
                if (list.isNotEmpty()) {
                    val typeStr = when (type) {
                        wh.rj.aiphone.model.ElementInfo.ElementType.LIKE_BUTTON -> "LIKE"
                        wh.rj.aiphone.model.ElementInfo.ElementType.FOLLOW_BUTTON -> "FOLLOW"
                        wh.rj.aiphone.model.ElementInfo.ElementType.COMMENT_BUTTON -> "COMMENT"
                        wh.rj.aiphone.model.ElementInfo.ElementType.SHARE_BUTTON -> "SHARE"
                        else -> "OTHER"
                    }
                    addConsoleLog("$typeStr: ${list.size} elements")
                }
            }
            
        } else {
            addConsoleLog("[WARN] No important elements detected")
            addConsoleLog("Total elements: ${elements.size}")
            addConsoleLog("Suggestion: Try using search filters")
        }
        
        addConsoleLog("========== SCAN COMPLETE ==========")
        showToast("📋 检测完成: ${elements.size}个元素")
    }
    
    private fun showSearchResults(keyword: String, results: List<wh.rj.aiphone.model.ElementInfo>) {
        LogCollector.addLog("I", TAG, "📋 显示搜索结果: ${results.size}个匹配")
        
        addConsoleLog("========== SEARCH RESULTS ==========")
        addConsoleLog("Query: \"$keyword\"")
        addConsoleLog("Matches found: ${results.size}")
        
        // 显示前6个搜索结果
        results.take(6).forEachIndexed { index, element ->
            val typeStr = when (element.elementType) {
                wh.rj.aiphone.model.ElementInfo.ElementType.LIKE_BUTTON -> "LIKE"
                wh.rj.aiphone.model.ElementInfo.ElementType.FOLLOW_BUTTON -> "FOLLOW"
                wh.rj.aiphone.model.ElementInfo.ElementType.COMMENT_BUTTON -> "COMMENT"
                wh.rj.aiphone.model.ElementInfo.ElementType.SHARE_BUTTON -> "SHARE"
                wh.rj.aiphone.model.ElementInfo.ElementType.PLAY_BUTTON -> "PLAY"
                wh.rj.aiphone.model.ElementInfo.ElementType.USER_AVATAR -> "AVATAR"
                else -> "OTHER"
            }
            
            val displayText = element.text?.take(20) ?: element.contentDescription?.take(20) ?: "no_text"
            val clickable = if (element.isClickable) "clickable" else "not_clickable"
            
            addConsoleLog("${index + 1}. [$typeStr] \"$displayText\" at (${element.centerX},${element.centerY}) - $clickable")
        }
        
        if (results.size > 6) {
            addConsoleLog("... and ${results.size - 6} more results")
        }
        
        addConsoleLog("========== SEARCH COMPLETE ==========")
        showToast("🔎 找到 ${results.size} 个匹配元素")
    }
    
    private fun updateResultText(text: String) {
        floatingView?.let { view ->
            if (view is LinearLayout && view.childCount >= 3) {
                val textView = view.getChildAt(2) as? TextView
                textView?.text = text
            }
        }
    }
    
    private fun showToast(message: String) {
        try {
            handler.post {
                Toast.makeText(this@FloatingWindowService, message, Toast.LENGTH_SHORT).show()
            }
            LogCollector.addLog("I", TAG, "Toast: $message")
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "Toast失败: $message - ${e.message}")
        }
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.takeIf { it.isNotEmpty() } ?: "未知应用"
        }
    }

    private fun setupDragListener(view: View, dragHandle: View) {
        var lastX = 0
        var lastY = 0
        
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val nowX = event.rawX.toInt()
                    val nowY = event.rawY.toInt()
                    val movedX = nowX - lastX
                    val movedY = nowY - lastY
                    lastX = nowX
                    lastY = nowY
                    
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x += movedX
                    params.y += movedY
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI助手悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI自动化助手悬浮窗服务"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🤖 AI助手运行中")
            .setContentText("目标应用: ${getAppName(targetPackage)}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "移除悬浮窗失败: ${e.message}")
        }
        instance = null
        LogCollector.addLog("I", TAG, "🛑 悬浮窗服务已销毁")
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 