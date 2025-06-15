package wh.rj.aiphone.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import wh.rj.aiphone.R
import wh.rj.aiphone.model.TaskConfig
import wh.rj.aiphone.service.AIAccessibilityService
import wh.rj.aiphone.utils.LogCollector

/**
 * 任务配置界面
 * 允许用户为特定应用配置自动化任务参数
 */
class TaskConfigActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TaskConfigActivity"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
    }

    // UI组件
    private lateinit var tvAppInfo: TextView
    private lateinit var sbSwipeInterval: SeekBar
    private lateinit var tvSwipeInterval: TextView
    private lateinit var sbStayDuration: SeekBar
    private lateinit var tvStayDuration: TextView
    private lateinit var sbMaxOperations: SeekBar
    private lateinit var tvMaxOperations: TextView
    private lateinit var sbLikeChance: SeekBar
    private lateinit var tvLikeChance: TextView
    private lateinit var sbFollowChance: SeekBar
    private lateinit var tvFollowChance: TextView
    private lateinit var sbCommentChance: SeekBar
    private lateinit var tvCommentChance: TextView
    private lateinit var switchSmartDetection: Switch
    private lateinit var switchGesture: Switch
    private lateinit var switchRandomDelay: Switch
    private lateinit var btnSaveConfig: Button
    private lateinit var btnStartTask: Button
    private lateinit var btnResetDefault: Button

    
    // 新增的开关控件
    private lateinit var switchSwipeTask: Switch
    private lateinit var switchLikeOperation: Switch
    private lateinit var switchFollowOperation: Switch
    private lateinit var switchCommentOperation: Switch
    private lateinit var switchAutoStartTask: Switch
    private lateinit var switchLiveMode: Switch


    // 任务相关
    private lateinit var packageName: String
    private lateinit var appName: String
    private lateinit var currentConfig: TaskConfig
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_config)

        // 获取传递的参数
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""

        if (packageName.isEmpty()) {
            Toast.makeText(this, "❌ 无效的应用信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadConfiguration()
        setupListeners()

        LogCollector.addLog("I", TAG, "🎯 打开任务配置界面: $appName")
    }

    private fun initViews() {
        tvAppInfo = findViewById(R.id.tvAppInfo)
        
        sbSwipeInterval = findViewById(R.id.sbSwipeInterval)
        tvSwipeInterval = findViewById(R.id.tvSwipeInterval)
        
        sbStayDuration = findViewById(R.id.sbStayDuration)
        tvStayDuration = findViewById(R.id.tvStayDuration)
        
        sbMaxOperations = findViewById(R.id.sbMaxOperations)
        tvMaxOperations = findViewById(R.id.tvMaxOperations)
        
        sbLikeChance = findViewById(R.id.sbLikeChance)
        tvLikeChance = findViewById(R.id.tvLikeChance)
        
        sbFollowChance = findViewById(R.id.sbFollowChance)
        tvFollowChance = findViewById(R.id.tvFollowChance)
        
        sbCommentChance = findViewById(R.id.sbCommentChance)
        tvCommentChance = findViewById(R.id.tvCommentChance)
        
        switchSmartDetection = findViewById(R.id.switchSmartDetection)
        switchGesture = findViewById(R.id.switchGesture)
        switchRandomDelay = findViewById(R.id.switchRandomDelay)
        
        // 新增的开关控件
        switchSwipeTask = findViewById(R.id.switchSwipeTask)
        switchLikeOperation = findViewById(R.id.switchLikeOperation)
        switchFollowOperation = findViewById(R.id.switchFollowOperation)
        switchCommentOperation = findViewById(R.id.switchCommentOperation)
        switchAutoStartTask = findViewById(R.id.switchAutoStartTask)
        switchLiveMode = findViewById(R.id.switchLiveMode)
        

        
        btnSaveConfig = findViewById(R.id.btnSaveConfig)
        btnStartTask = findViewById(R.id.btnStartTask)
        btnResetDefault = findViewById(R.id.btnResetDefault)

        // 设置应用信息
        tvAppInfo.text = "📱 配置应用: $appName\n📦 包名: $packageName"
    }

    private fun loadConfiguration() {
        // 加载默认配置或已保存的配置
        currentConfig = TaskConfig.getDefaultByPackage(packageName, appName)
        
        // 应用配置到UI
        applyConfigToUI(currentConfig)
        
        LogCollector.addLog("I", TAG, "📋 加载配置: ${currentConfig.getSummary()}")
    }

    private fun applyConfigToUI(config: TaskConfig) {
        // 滑动间隔 (1-10秒)
        sbSwipeInterval.max = 9000
        sbSwipeInterval.progress = (config.swipeInterval - 1000).toInt()
        updateSwipeIntervalText(config.swipeInterval)
        
        // 停留时间 (1-15秒)
        sbStayDuration.max = 14000
        sbStayDuration.progress = (config.stayDuration - 1000).toInt()
        updateStayDurationText(config.stayDuration)
        
        // 最大操作次数 (10-200)
        sbMaxOperations.max = 190
        sbMaxOperations.progress = config.maxOperations - 10
        updateMaxOperationsText(config.maxOperations)
        
        // 点赞概率 (0-100%)
        sbLikeChance.max = 100
        sbLikeChance.progress = config.likeChance
        updateLikeChanceText(config.likeChance)
        
        // 关注概率 (0-50%)
        sbFollowChance.max = 50
        sbFollowChance.progress = config.followChance
        updateFollowChanceText(config.followChance)
        
        // 评论概率 (0-50%)
        sbCommentChance.max = 50
        sbCommentChance.progress = config.commentChance
        updateCommentChanceText(config.commentChance)
        
        // 原有开关设置
        switchSmartDetection.isChecked = config.enableSmartDetection
        switchGesture.isChecked = config.enableGesture
        switchRandomDelay.isChecked = config.enableRandomDelay
        
        // 新增开关设置
        switchSwipeTask.isChecked = config.enableSwipeTask
        switchLikeOperation.isChecked = config.enableLikeOperation
        switchFollowOperation.isChecked = config.enableFollowOperation
        switchCommentOperation.isChecked = config.enableCommentOperation
        switchAutoStartTask.isChecked = config.autoStartTask
        switchLiveMode.isChecked = config.isLiveMode
        

        
        // 触发开关联动，确保UI状态正确
        handler.post {
            toggleSwipeTaskControls(config.enableSwipeTask)
            toggleLikeControls(config.enableLikeOperation)
            toggleFollowControls(config.enableFollowOperation)
            toggleCommentControls(config.enableCommentOperation)
            toggleLiveModeControls(config.isLiveMode)
        }
    }

    private fun setupListeners() {
        // 滑动间隔
        sbSwipeInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val interval = progress + 1000L
                    updateSwipeIntervalText(interval)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 停留时间
        sbStayDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = progress + 1000L
                    updateStayDurationText(duration)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 最大操作次数
        sbMaxOperations.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val operations = progress + 10
                    updateMaxOperationsText(operations)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 点赞概率
        sbLikeChance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateLikeChanceText(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 关注概率
        sbFollowChance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateFollowChanceText(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 评论概率
        sbCommentChance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateCommentChanceText(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        


        // 按钮事件
        btnSaveConfig.setOnClickListener { saveConfiguration() }
        btnStartTask.setOnClickListener { startAutomationTask() }
        btnResetDefault.setOnClickListener { resetToDefault() }
        
        // 开关联动监听器
        setupSwitchListeners()
    }

    private fun updateSwipeIntervalText(interval: Long) {
        tvSwipeInterval.text = if (interval > 0) "滑动间隔: ${interval/1000}秒" else "滑动间隔: 未设置"
    }

    private fun updateStayDurationText(duration: Long) {
        tvStayDuration.text = if (duration > 0) "停留时间: ${duration/1000}秒" else "停留时间: 未设置"
    }

    private fun updateMaxOperationsText(operations: Int) {
        tvMaxOperations.text = if (operations > 0) "最大次数: $operations 次" else "最大次数: 未设置"
    }

    private fun updateLikeChanceText(chance: Int) {
        tvLikeChance.text = if (chance > 0) "点赞概率: $chance%" else "点赞概率: 未启用"
    }

    private fun updateFollowChanceText(chance: Int) {
        tvFollowChance.text = if (chance > 0) "关注概率: $chance%" else "关注概率: 未启用"
    }

    private fun updateCommentChanceText(chance: Int) {
        tvCommentChance.text = if (chance > 0) "评论概率: $chance%" else "评论概率: 未启用"
    }
    

    
    private fun setupSwitchListeners() {
        // 基础滑动任务开关监听
        switchSwipeTask.setOnCheckedChangeListener { _, isChecked ->
            toggleSwipeTaskControls(isChecked)
        }
        
        // 点赞操作开关监听
        switchLikeOperation.setOnCheckedChangeListener { _, isChecked ->
            toggleLikeControls(isChecked)
        }
        
        // 关注操作开关监听
        switchFollowOperation.setOnCheckedChangeListener { _, isChecked ->
            toggleFollowControls(isChecked)
        }
        
        // 评论操作开关监听
        switchCommentOperation.setOnCheckedChangeListener { _, isChecked ->
            toggleCommentControls(isChecked)
        }
        
        // 直播模式开关监听
        switchLiveMode.setOnCheckedChangeListener { _, isChecked ->
            toggleLiveModeControls(isChecked)
        }
        
        // 初始化控件状态
        toggleSwipeTaskControls(switchSwipeTask.isChecked)
        toggleLikeControls(switchLikeOperation.isChecked)
        toggleFollowControls(switchFollowOperation.isChecked)
        toggleCommentControls(switchCommentOperation.isChecked)
        toggleLiveModeControls(switchLiveMode.isChecked)
    }
    
    private fun toggleSwipeTaskControls(enabled: Boolean) {
        // 滑动间隔控件
        sbSwipeInterval.isEnabled = enabled
        tvSwipeInterval.alpha = if (enabled) 1.0f else 0.4f
        
        // 停留时间控件
        sbStayDuration.isEnabled = enabled
        tvStayDuration.alpha = if (enabled) 1.0f else 0.4f
        
        // 最大操作次数控件
        sbMaxOperations.isEnabled = enabled
        tvMaxOperations.alpha = if (enabled) 1.0f else 0.4f
        
        if (!enabled) {
            sbSwipeInterval.progress = 0
            sbStayDuration.progress = 0
            sbMaxOperations.progress = 0
            updateSwipeIntervalText(0)
            updateStayDurationText(0)
            updateMaxOperationsText(0)
        }
    }
    
    private fun toggleLikeControls(enabled: Boolean) {
        sbLikeChance.isEnabled = enabled
        tvLikeChance.alpha = if (enabled) 1.0f else 0.4f
        
        if (!enabled) {
            sbLikeChance.progress = 0
            updateLikeChanceText(0)
        }
    }
    
    private fun toggleFollowControls(enabled: Boolean) {
        sbFollowChance.isEnabled = enabled
        tvFollowChance.alpha = if (enabled) 1.0f else 0.4f
        
        if (!enabled) {
            sbFollowChance.progress = 0
            updateFollowChanceText(0)
        }
    }
    
    private fun toggleCommentControls(enabled: Boolean) {
        sbCommentChance.isEnabled = enabled
        tvCommentChance.alpha = if (enabled) 1.0f else 0.4f
        
        if (!enabled) {
            sbCommentChance.progress = 0
            updateCommentChanceText(0)
        }
    }
    
    private fun toggleLiveModeControls(enabled: Boolean) {
        // 直播模式下，禁用滑动和某些设置
        if (enabled) {
            // 关闭滑动任务
            switchSwipeTask.isChecked = false
            switchSwipeTask.isEnabled = false
            
            // 关闭关注和评论
            switchFollowOperation.isChecked = false
            switchFollowOperation.isEnabled = false
            switchCommentOperation.isChecked = false
            switchCommentOperation.isEnabled = false
            
            // 关闭点赞操作（直播模式不用这个概率控制）
            switchLikeOperation.isChecked = false
            switchLikeOperation.isEnabled = false
            
            // 关闭随机延迟（直播模式需要精确控制）
            switchRandomDelay.isChecked = false
            switchRandomDelay.isEnabled = false
            
            // 关闭手势模式和智能检测（直播模式使用坐标点击）
            switchGesture.isChecked = true
            switchGesture.isEnabled = true
            switchSmartDetection.isChecked = true
            switchSmartDetection.isEnabled = true
            
            Toast.makeText(this, "📺 直播模式已启用\n专注坐标点赞，请先记录点赞位置", Toast.LENGTH_SHORT).show()
        } else {
            // 恢复正常模式，重新启用所有控件
            switchSwipeTask.isEnabled = true
            switchFollowOperation.isEnabled = true
            switchCommentOperation.isEnabled = true
            switchLikeOperation.isEnabled = true
            switchRandomDelay.isEnabled = true
            switchGesture.isEnabled = true
            switchSmartDetection.isEnabled = true
            
            // 恢复一些默认状态
            switchLikeOperation.isChecked = true
            switchRandomDelay.isChecked = true
            switchGesture.isChecked = true
            switchSmartDetection.isChecked = true
            
            Toast.makeText(this, "📱 已切换到普通模式", Toast.LENGTH_SHORT).show()
        }
        
        // 更新控件状态
        toggleSwipeTaskControls(switchSwipeTask.isChecked)
        toggleFollowControls(switchFollowOperation.isChecked)
        toggleCommentControls(switchCommentOperation.isChecked)
        toggleLikeControls(switchLikeOperation.isChecked)
    }

    private fun saveConfiguration() {
        try {
            // 从UI获取当前配置
            val config = getCurrentConfigFromUI()
            
            // 验证配置
            if (!config.isValid()) {
                Toast.makeText(this, "❌ 配置参数无效", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 保存配置 (这里可以存储到SharedPreferences或数据库)
            currentConfig = config
            
            Toast.makeText(this, "✅ 配置已保存", Toast.LENGTH_SHORT).show()
            LogCollector.addLog("I", TAG, "💾 保存配置: ${config.getSummary()}")
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ 保存配置失败: ${e.message}", Toast.LENGTH_SHORT).show()
            LogCollector.addLog("E", TAG, "❌ 保存配置失败: ${e.message}")
        }
    }

    private fun startAutomationTask() {
        try {
            // 获取当前配置
            val config = getCurrentConfigFromUI()
            if (!config.isValid()) {
                Toast.makeText(this, "❌ 配置参数无效，请检查设置", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 检查是否启用了自动启动任务
            if (config.autoStartTask) {
                // 检查无障碍服务是否可用
                val accessibilityService = wh.rj.aiphone.service.AIAccessibilityService.getInstance()
                if (accessibilityService == null) {
                    showServiceNotAvailableDialog()
                    return
                }
                
                // 显示确认对话框
                showStartTaskConfirmDialog(config)
            } else {
                // 只启动应用，不启动任务
                showStartAppOnlyDialog(config)
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ 启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            LogCollector.addLog("E", TAG, "❌ 启动失败: ${e.message}")
        }
    }

    private fun resetToDefault() {
        AlertDialog.Builder(this)
            .setTitle("🔄 重置配置")
            .setMessage("确定要重置为默认配置吗？\n\n这将恢复所有设置到初始状态。")
            .setPositiveButton("重置") { _, _ ->
                val defaultConfig = TaskConfig.getDefaultByPackage(packageName, appName)
                applyConfigToUI(defaultConfig)
                currentConfig = defaultConfig
                Toast.makeText(this, "🔄 已重置为默认配置", Toast.LENGTH_SHORT).show()
                LogCollector.addLog("I", TAG, "🔄 重置为默认配置")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getCurrentConfigFromUI(): TaskConfig {
        val isLiveMode = switchLiveMode.isChecked
        
        return TaskConfig(
            packageName = packageName,
            appName = appName,
            swipeInterval = if (isLiveMode) 0L else sbSwipeInterval.progress + 1000L,
            stayDuration = if (isLiveMode) 0L else sbStayDuration.progress + 1000L,
            maxOperations = sbMaxOperations.progress + 10,
            likeChance = sbLikeChance.progress,
            followChance = sbFollowChance.progress,
            commentChance = sbCommentChance.progress,
            enableSmartDetection = switchSmartDetection.isChecked,
            enableGesture = switchGesture.isChecked,
            enableRandomDelay = switchRandomDelay.isChecked,
            enableSwipeTask = switchSwipeTask.isChecked,
            enableLikeOperation = switchLikeOperation.isChecked,
            enableFollowOperation = switchFollowOperation.isChecked,
            enableCommentOperation = switchCommentOperation.isChecked,
            autoStartTask = switchAutoStartTask.isChecked,
            isLiveMode = isLiveMode,
            liveLikeMode = wh.rj.aiphone.model.LiveLikeMode.DEFAULT,
            liveLikeInterval = 2000L,
            liveLikeMaxCount = sbMaxOperations.progress + 10
        )
    }

    private fun showServiceNotAvailableDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 服务未可用")
            .setMessage("无障碍服务未启动或未连接。\n\n请确保：\n1. 已开启无障碍服务权限\n2. 服务正在正常运行")
            .setPositiveButton("去设置") { _, _ ->
                // 可以跳转到设置页面
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showStartTaskConfirmDialog(config: TaskConfig) {
        val message = """
            🎯 即将启动自动化任务
            
            📱 应用: $appName
            ⏱️ 配置摘要:
            ${config.getSummary()}
            
            ⚠️ 注意事项:
            • 请确保目标应用已打开
            • 任务启动后请勿频繁切换应用
            • 可随时通过通知栏停止任务
            
            确定要启动任务吗？
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("🚀 启动确认")
            .setMessage(message)
            .setPositiveButton("启动任务") { _, _ ->
                executeStartTask(config)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showStartAppOnlyDialog(config: TaskConfig) {
        val message = """
            📱 即将启动应用
            
            应用: $appName
            
            ℹ️ 说明:
            • 将只启动应用，不自动开始任务
            • 您可以在应用中手动操作
            • 也可以使用悬浮按钮来启动任务
            
            确定要启动应用吗？
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("📱 启动应用")
            .setMessage(message)
            .setPositiveButton("启动应用") { _, _ ->
                executeStartAppOnly(config)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeStartTask(config: TaskConfig) {
        try {
            // 先自动打开目标应用
            val appOpened = openTargetApp(packageName)
            if (appOpened) {
                LogCollector.addLog("I", TAG, "📱 已打开目标应用: $appName")
            } else {
                LogCollector.addLog("W", TAG, "⚠️ 无法自动打开目标应用，请手动打开")
                Toast.makeText(this, "⚠️ 请手动打开目标应用", Toast.LENGTH_SHORT).show()
            }
            
            // 无论应用是否成功打开，都尝试启动任务（保持原有功能）
            val delay = if (appOpened) 2000L else 500L // 如果打开了应用就等2秒，否则等0.5秒
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startAutomationTaskDirectly(config)
            }, delay)
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 启动异常: ${e.message}")
            // 即使出现异常，也尝试直接启动任务
            startAutomationTaskDirectly(config)
        }
    }
    
    private fun executeStartAppOnly(@Suppress("UNUSED_PARAMETER") config: TaskConfig) {
        try {
            // 启动应用
            val appOpened = openTargetApp(packageName)
            if (appOpened) {
                LogCollector.addLog("I", TAG, "📱 已启动应用: $appName")
                Toast.makeText(this, "✅ 应用已启动，正在启动悬浮按钮...", Toast.LENGTH_SHORT).show()
                
                // 延迟启动悬浮按钮，确保应用完全启动
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    tryStartStatusFloatingButton()
                    LogCollector.addLog("I", TAG, "✅ 悬浮按钮已启动，用户可以在应用中操作")
                    
                    // 直接关闭配置界面，让用户留在启动的应用中
                    finish()
                }, 1500L) // 等待1.5秒让应用完全启动
                
            } else {
                LogCollector.addLog("W", TAG, "⚠️ 无法启动目标应用")
                Toast.makeText(this, "❌ 无法启动目标应用，请手动打开", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 启动应用异常: ${e.message}")
            Toast.makeText(this, "❌ 启动应用失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openTargetApp(packageName: String): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                LogCollector.addLog("I", TAG, "✅ 成功启动应用: $packageName")
                true
            } else {
                LogCollector.addLog("E", TAG, "❌ 无法获取应用启动Intent: $packageName")
                false
            }
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 启动应用失败: ${e.message}")
            false
        }
    }
    
    private fun startAutomationTaskDirectly(config: TaskConfig) {
        try {
            val accessibilityService = wh.rj.aiphone.service.AIAccessibilityService.getInstance()
            val success = accessibilityService?.startAutomationTask(packageName, config) ?: false
            
            if (success) {
                Toast.makeText(this, "🚀 任务已启动", Toast.LENGTH_SHORT).show()
                LogCollector.addLog("I", TAG, "🚀 启动自动化任务: $appName")
                
                // 尝试启动状态悬浮按钮（可选功能，失败不影响主要任务）
                tryStartStatusFloatingButton()
                
                // 返回主界面
                val intent = Intent(this, wh.rj.aiphone.MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
                
            } else {
                Toast.makeText(this, "❌ 任务启动失败，请检查无障碍服务", Toast.LENGTH_SHORT).show()
                LogCollector.addLog("E", TAG, "❌ 任务启动失败")
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ 启动异常: ${e.message}", Toast.LENGTH_SHORT).show()
            LogCollector.addLog("E", TAG, "❌ 启动异常: ${e.message}")
        }
    }
    
    private fun tryStartStatusFloatingButton() {
        try {
            // 检查悬浮窗权限
            if (!android.provider.Settings.canDrawOverlays(this)) {
                LogCollector.addLog("W", TAG, "⚠️ 没有悬浮窗权限，跳过状态显示功能")
                return
            }
            
            val intent = Intent(this, wh.rj.aiphone.service.StatusFloatingService::class.java)
            intent.putExtra("target_package", packageName)
            intent.putExtra("app_name", appName)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            LogCollector.addLog("I", TAG, "✅ 状态悬浮按钮已启动")
        } catch (e: Exception) {
            // 悬浮按钮启动失败不影响主要功能
            LogCollector.addLog("W", TAG, "⚠️ 启动状态悬浮按钮失败，但不影响主要功能: ${e.message}")
        }
    }
    

} 