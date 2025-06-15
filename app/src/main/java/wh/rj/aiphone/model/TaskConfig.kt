package wh.rj.aiphone.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 直播点赞模式枚举
 */
enum class LiveLikeMode {
    /** 默认模式 - 使用坐标点击 */
    DEFAULT
}

/**
 * 任务配置数据模型
 * 用于保存每个应用的自动化任务配置参数
 */
@Parcelize
data class TaskConfig(
    /** 目标应用包名 */
    val packageName: String,
    
    /** 应用名称 */
    val appName: String,
    
    /** 滑动周期 (毫秒) - 每次滑动间隔时间 */
    val swipeInterval: Long = 3000L,
    
    /** 视频停留时间 (毫秒) - 每个视频观看时长 */
    val stayDuration: Long = 5000L,
    
    /** 最大操作次数 - 连续操作的最大次数 */
    val maxOperations: Int = 50,
    
    /** 点赞概率 (0-100) - 随机点赞的概率 */
    val likeChance: Int = 30,
    
    /** 关注概率 (0-100) - 随机关注的概率 */
    val followChance: Int = 5,
    
    /** 评论概率 (0-100) - 随机评论的概率 */
    val commentChance: Int = 10,
    
    /** 分享概率 (0-100) - 随机分享的概率 */
    val shareChance: Int = 2,
    
    /** 是否启用智能检测 - 自动识别页面元素 */
    val enableSmartDetection: Boolean = true,
    
    /** 是否启用手势操作 - 使用手势API滑动 */
    val enableGesture: Boolean = true,
    
    /** 是否启用随机延迟 - 增加操作的随机性 */
    val enableRandomDelay: Boolean = true,
    
    /** 随机延迟范围 (毫秒) - 在基础延迟上增加的随机时间 */
    val randomDelayRange: Long = 2000L,
    
    /** 任务开始时间 (时间戳) - 任务开始执行的时间 */
    var startTime: Long = 0L,
    
    /** 任务结束时间 (时间戳) - 任务结束的时间 */
    var endTime: Long = 0L,
    
    /** 是否启用任务 */
    var isEnabled: Boolean = true,
    
    /** 任务创建时间 */
    val createTime: Long = System.currentTimeMillis(),
    
    /** 任务备注信息 */
    val remark: String = "",
    
    /** 是否启用基础滑动任务 */
    val enableSwipeTask: Boolean = true,
    
    /** 是否启用点赞操作 */
    val enableLikeOperation: Boolean = true,
    
    /** 是否启用关注操作 */
    val enableFollowOperation: Boolean = true,
    
    /** 是否启用评论操作 */
    val enableCommentOperation: Boolean = true,
    
    /** 是否启用分享操作 */
    val enableShareOperation: Boolean = false,
    
    /** 是否自动启动任务（如果为false，则只启动应用不启动任务） */
    val autoStartTask: Boolean = true,
    
    /** 是否为直播模式 */
    val isLiveMode: Boolean = false,
    
    /** 直播点赞模式 */
    val liveLikeMode: LiveLikeMode = LiveLikeMode.DEFAULT,
    
    /** 直播点赞间隔时间 (毫秒) */
    val liveLikeInterval: Long = 2000L,
    
    /** 直播点赞次数上限 */
    val liveLikeMaxCount: Int = 50,
    
    /** 自定义点赞坐标X */
    val customLikeX: Int = -1,
    
    /** 自定义点赞坐标Y */
    val customLikeY: Int = -1,
    
    /** 是否使用自定义坐标 */
    val useCustomCoordinates: Boolean = false,
    
    /** 自定义坐标名称/描述 */
    val customCoordinatesName: String = ""
) : Parcelable {

    companion object {
        /**
         * 获取抖音默认配置
         */
        fun getDouyinDefault(): TaskConfig {
            return TaskConfig(
                packageName = "com.ss.android.ugc.aweme",
                appName = "抖音",
                swipeInterval = 4000L,
                stayDuration = 6000L,
                maxOperations = 100,
                likeChance = 25,
                followChance = 3,
                commentChance = 8
            )
        }
        
        /**
         * 获取抖音直播默认配置
         */
        fun getDouyinLiveDefault(): TaskConfig {
            return TaskConfig(
                packageName = "com.ss.android.ugc.aweme",
                appName = "抖音直播",
                swipeInterval = 0L, // 直播模式不需要滑动
                stayDuration = 0L,
                maxOperations = 50,
                likeChance = 100, // 直播模式100%点赞
                followChance = 0,
                commentChance = 0,
                enableSwipeTask = false,
                enableLikeOperation = true,
                enableFollowOperation = false,
                enableCommentOperation = false,
                isLiveMode = true,
                liveLikeMode = LiveLikeMode.DEFAULT,
                liveLikeInterval = 2000L,
                liveLikeMaxCount = 50
            )
        }
        
        /**
         * 获取快手默认配置
         */
        fun getKuaishouDefault(): TaskConfig {
            return TaskConfig(
                packageName = "com.smile.gifmaker",
                appName = "快手",
                swipeInterval = 3500L,
                stayDuration = 5500L,
                maxOperations = 80,
                likeChance = 30,
                followChance = 4,
                commentChance = 10
            )
        }
        
        /**
         * 获取微信默认配置
         */
        fun getWechatDefault(): TaskConfig {
            return TaskConfig(
                packageName = "com.tencent.mm",
                appName = "微信",
                swipeInterval = 2000L,
                stayDuration = 3000L,
                maxOperations = 50,
                likeChance = 40,
                followChance = 0,
                commentChance = 5
            )
        }
        
        /**
         * 获取微信直播默认配置
         */
        fun getWechatLiveDefault(): TaskConfig {
            return TaskConfig(
                packageName = "com.tencent.mm",
                appName = "微信直播",
                swipeInterval = 0L, // 直播模式不需要滑动
                stayDuration = 0L,
                maxOperations = 30,
                likeChance = 100, // 直播模式100%点赞
                followChance = 0,
                commentChance = 0,
                enableSwipeTask = false,
                enableLikeOperation = true,
                enableFollowOperation = false,
                enableCommentOperation = false,
                isLiveMode = true,
                liveLikeMode = LiveLikeMode.DEFAULT,
                liveLikeInterval = 3000L,
                liveLikeMaxCount = 30
            )
        }
        
        /**
         * 获取小红书默认配置
         */
        fun getXiaohongshuDefault(): TaskConfig {
            return TaskConfig(
                packageName = "com.xingin.xhs",
                appName = "小红书",
                swipeInterval = 3000L,
                stayDuration = 4000L,
                maxOperations = 60,
                likeChance = 35,
                followChance = 5,
                commentChance = 8
            )
        }
        
        /**
         * 根据包名获取默认配置
         */
        fun getDefaultByPackage(packageName: String, appName: String): TaskConfig {
            return when (packageName) {
                "com.ss.android.ugc.aweme" -> {
                    if (appName.contains("直播", ignoreCase = true)) {
                        getDouyinLiveDefault()
                    } else {
                        getDouyinDefault()
                    }
                }
                "com.smile.gifmaker" -> getKuaishouDefault()
                "com.tencent.mm" -> {
                    if (appName.contains("直播", ignoreCase = true)) {
                        getWechatLiveDefault()
                    } else {
                        getWechatDefault()
                    }
                }
                "com.xingin.xhs" -> getXiaohongshuDefault()
                else -> TaskConfig(
                    packageName = packageName,
                    appName = appName
                )
            }
        }
    }
    
    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return packageName.isNotBlank() && 
               swipeInterval >= 0 && 
               stayDuration >= 0 && 
               maxOperations > 0 &&
               likeChance in 0..100 &&
               followChance in 0..100 &&
               commentChance in 0..100 &&
               shareChance in 0..100 &&
               (if (isLiveMode) liveLikeInterval > 0 && liveLikeMaxCount > 0 else true)
    }
    
    /**
     * 获取配置摘要信息
     */
    fun getSummary(): String {
        return if (isLiveMode) {
            "直播模式 | 点赞间隔: ${liveLikeInterval/1000}s | 最大次数: $liveLikeMaxCount"
        } else {
            "滑动间隔: ${swipeInterval/1000}s | 停留: ${stayDuration/1000}s | 最大次数: $maxOperations | 点赞率: $likeChance%"
        }
    }
} 