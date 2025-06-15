package wh.rj.aiphone.model

/**
 * 任务统计数据模型
 * 用于跟踪和显示任务执行的统计信息
 */
data class TaskStats(
    /** 任务包名 */
    val packageName: String = "",
    
    /** 应用名称 */
    val appName: String = "",
    
    /** 任务状态 */
    var status: TaskStatus = TaskStatus.IDLE,
    
    /** 点赞次数 */
    var likeCount: Int = 0,
    
    /** 关注次数 */
    var followCount: Int = 0,
    
    /** 评论次数 */
    var commentCount: Int = 0,
    
    /** 分享次数 */
    var shareCount: Int = 0,
    
    /** 滑动次数 */
    var swipeCount: Int = 0,
    
    /** 总操作次数 */
    var totalOperations: Int = 0,
    
    /** 最大操作次数 */
    var maxOperations: Int = 0,
    
    /** 任务开始时间 */
    var startTime: Long = 0L,
    
    /** 运行时长（毫秒） */
    var runDuration: Long = 0L,
    
    /** 上次更新时间 */
    var lastUpdateTime: Long = System.currentTimeMillis()
) {
    
    enum class TaskStatus {
        IDLE,        // 空闲
        RUNNING,     // 运行中
        PAUSED,      // 暂停
        COMPLETED,   // 完成
        ERROR        // 错误
    }
    
    /**
     * 获取任务进度百分比
     */
    fun getProgressPercentage(): Int {
        return if (maxOperations > 0) {
            ((swipeCount.toFloat() / maxOperations) * 100).toInt()
        } else 0
    }
    
    /**
     * 获取运行时长文本
     */
    fun getRunDurationText(): String {
        val seconds = runDuration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    
    /**
     * 获取状态文本
     */
    fun getStatusText(): String {
        return when (status) {
            TaskStatus.IDLE -> "空闲"
            TaskStatus.RUNNING -> "运行中"
            TaskStatus.PAUSED -> "暂停"
            TaskStatus.COMPLETED -> "已完成"
            TaskStatus.ERROR -> "错误"
        }
    }
    
    /**
     * 获取状态图标
     */
    fun getStatusIcon(): String {
        return when (status) {
            TaskStatus.IDLE -> "⏸️"
            TaskStatus.RUNNING -> "🚀"
            TaskStatus.PAUSED -> "⏸️"
            TaskStatus.COMPLETED -> "✅"
            TaskStatus.ERROR -> "❌"
        }
    }
    
    /**
     * 更新统计数据
     */
    fun updateStats(
        likes: Int = 0,
        follows: Int = 0,
        comments: Int = 0,
        shares: Int = 0,
        swipes: Int = 0
    ) {
        likeCount += likes
        followCount += follows
        commentCount += comments
        shareCount += shares
        swipeCount += swipes
        
        totalOperations = likeCount + followCount + commentCount + shareCount
        lastUpdateTime = System.currentTimeMillis()
        
        if (startTime > 0) {
            runDuration = System.currentTimeMillis() - startTime
        }
    }
    
    /**
     * 重置统计数据
     */
    fun reset() {
        likeCount = 0
        followCount = 0
        commentCount = 0
        shareCount = 0
        swipeCount = 0
        totalOperations = 0
        runDuration = 0L
        startTime = 0L
        status = TaskStatus.IDLE
        lastUpdateTime = System.currentTimeMillis()
    }
    
    /**
     * 开始任务
     */
    fun startTask(maxOps: Int) {
        startTime = System.currentTimeMillis()
        maxOperations = maxOps
        status = TaskStatus.RUNNING
        lastUpdateTime = System.currentTimeMillis()
    }
    
    /**
     * 停止任务
     */
    fun stopTask() {
        status = if (totalOperations >= maxOperations) {
            TaskStatus.COMPLETED
        } else {
            TaskStatus.IDLE
        }
        lastUpdateTime = System.currentTimeMillis()
    }
    
    /**
     * 获取简要统计信息
     */
    fun getSummary(): String {
        return "👍${likeCount} ➕${followCount} 💬${commentCount} 📊${totalOperations}/${maxOperations}"
    }
} 