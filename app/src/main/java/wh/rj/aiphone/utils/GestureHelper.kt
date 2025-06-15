package wh.rj.aiphone.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import wh.rj.aiphone.model.ElementInfo
import kotlin.random.Random

/**
 * 手势操作助手
 * 提供各种手势操作的封装和优化
 */
class GestureHelper(private val accessibilityService: AccessibilityService) {
    
    companion object {
        private const val TAG = "GestureHelper"
        
        /** 默认手势持续时间 */
        private const val DEFAULT_GESTURE_DURATION = 300L
        
        /** 默认滑动距离 */
        private const val DEFAULT_SWIPE_DISTANCE = 800
        
        /** 点击延迟时间 */
        private const val CLICK_DELAY = 100L
        
        /** 滑动延迟时间 */
        private const val SWIPE_DELAY = 500L
    }
    
    /**
     * 点击指定坐标
     * @param x X坐标
     * @param y Y坐标
     * @param duration 持续时间
     * @return 是否成功
     */
    fun click(x: Int, y: Int, duration: Long = CLICK_DELAY): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            LogCollector.addLog("W", TAG, "❌ Android版本过低，不支持手势操作")
            return false
        }
        
        return try {
            LogCollector.addLog("I", TAG, "👆 执行点击操作: ($x, $y)")
            
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)
            
            val success = accessibilityService.dispatchGesture(
                gestureBuilder.build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        LogCollector.addLog("I", TAG, "✅ 点击操作完成: ($x, $y)")
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        LogCollector.addLog("W", TAG, "⚠️ 点击操作被取消: ($x, $y)")
                    }
                },
                null
            )
            
            if (!success) {
                LogCollector.addLog("W", TAG, "❌ 点击操作分发失败: ($x, $y)")
            }
            
            success
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 点击操作异常: ($x, $y) - ${e.message}")
            false
        }
    }
    
    /**
     * 点击元素
     * @param element 元素信息
     * @return 是否成功
     */
    fun clickElement(element: ElementInfo): Boolean {
        if (!element.isClickable) {
            LogCollector.addLog("W", TAG, "⚠️ 元素不可点击: ${element.getSummary()}")
            return false
        }
        
        LogCollector.addLog("I", TAG, "👆 点击元素: ${element.getSummary()}")
        return click(element.centerX, element.centerY)
    }
    
    /**
     * 长按指定坐标
     * @param x X坐标
     * @param y Y坐标
     * @param duration 持续时间
     * @return 是否成功
     */
    fun longPress(x: Int, y: Int, duration: Long = 1000L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            LogCollector.addLog("W", TAG, "❌ Android版本过低，不支持手势操作")
            return false
        }
        
        return try {
            LogCollector.addLog("I", TAG, "👆 执行长按操作: ($x, $y) 持续${duration}ms")
            
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)
            
            val success = accessibilityService.dispatchGesture(
                gestureBuilder.build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        LogCollector.addLog("I", TAG, "✅ 长按操作完成: ($x, $y)")
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        LogCollector.addLog("W", TAG, "⚠️ 长按操作被取消: ($x, $y)")
                    }
                },
                null
            )
            
            success
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 长按操作异常: ($x, $y) - ${e.message}")
            false
        }
    }
    
    /**
     * 滑动操作
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param endX 结束X坐标
     * @param endY 结束Y坐标
     * @param duration 持续时间
     * @return 是否成功
     */
    fun swipe(
        startX: Int, 
        startY: Int, 
        endX: Int, 
        endY: Int, 
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            LogCollector.addLog("W", TAG, "❌ Android版本过低，不支持手势操作")
            return false
        }
        
        return try {
            LogCollector.addLog("I", TAG, "📱 执行滑动操作: ($startX,$startY) -> ($endX,$endY)")
            
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)
            
            val success = accessibilityService.dispatchGesture(
                gestureBuilder.build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        LogCollector.addLog("I", TAG, "✅ 滑动操作完成")
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        LogCollector.addLog("W", TAG, "⚠️ 滑动操作被取消")
                    }
                },
                null
            )
            
            success
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 滑动操作异常: ${e.message}")
            false
        }
    }
    
    /**
     * 向上滑动 (适用于短视频应用)
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param distance 滑动距离，默认为屏幕高度的80%
     * @param duration 持续时间
     * @return 是否成功
     */
    fun swipeUp(
        screenWidth: Int = 1080,
        screenHeight: Int = 1920,
        distance: Int = (screenHeight * 0.8).toInt(),
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        val centerX = screenWidth / 2
        val startY = screenHeight * 3 / 4  // 从屏幕下方3/4处开始
        val endY = (startY - distance).coerceAtLeast(50) // 确保结束Y坐标不小于50
        
        // 添加一些随机性，模拟真实用户操作
        val randomOffsetX = Random.nextInt(-50, 51)
        val randomOffsetY = Random.nextInt(-30, 31)
        
        // 确保所有坐标都在有效范围内
        val finalStartX = (centerX + randomOffsetX).coerceIn(50, screenWidth - 50)
        val finalStartY = (startY + randomOffsetY).coerceIn(50, screenHeight - 50)
        val finalEndX = (centerX + randomOffsetX / 2).coerceIn(50, screenWidth - 50)
        val finalEndY = (endY + randomOffsetY / 2).coerceIn(50, screenHeight - 50)
        
        LogCollector.addLog("D", TAG, "🔄 向上滑动坐标: ($finalStartX,$finalStartY) -> ($finalEndX,$finalEndY)")
        
        return swipe(
            startX = finalStartX,
            startY = finalStartY,
            endX = finalEndX,
            endY = finalEndY,
            duration = duration + Random.nextLong(-50, 51)
        )
    }
    
    /**
     * 向下滑动
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param distance 滑动距离
     * @param duration 持续时间
     * @return 是否成功
     */
    fun swipeDown(
        screenWidth: Int = 1080,
        screenHeight: Int = 1920,
        distance: Int = (screenHeight * 0.8).toInt(),
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        val centerX = screenWidth / 2
        val startY = screenHeight / 4  // 从屏幕上方1/4处开始
        val endY = (startY + distance).coerceAtMost(screenHeight - 50) // 确保结束Y坐标不超过屏幕
        
        val randomOffsetX = Random.nextInt(-50, 51)
        val randomOffsetY = Random.nextInt(-30, 31)
        
        // 确保所有坐标都在有效范围内
        val finalStartX = (centerX + randomOffsetX).coerceIn(50, screenWidth - 50)
        val finalStartY = (startY + randomOffsetY).coerceIn(50, screenHeight - 50)
        val finalEndX = (centerX + randomOffsetX / 2).coerceIn(50, screenWidth - 50)
        val finalEndY = (endY + randomOffsetY / 2).coerceIn(50, screenHeight - 50)
        
        LogCollector.addLog("D", TAG, "🔽 向下滑动坐标: ($finalStartX,$finalStartY) -> ($finalEndX,$finalEndY)")
        
        return swipe(
            startX = finalStartX,
            startY = finalStartY,
            endX = finalEndX,
            endY = finalEndY,
            duration = duration + Random.nextLong(-50, 51)
        )
    }
    
    /**
     * 向左滑动
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param distance 滑动距离
     * @param duration 持续时间
     * @return 是否成功
     */
    fun swipeLeft(
        screenWidth: Int = 1080,
        screenHeight: Int = 1920,
        distance: Int = (screenWidth * 0.8).toInt(),
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        val centerY = screenHeight / 2
        val startX = screenWidth * 3 / 4
        val endX = (startX - distance).coerceAtLeast(50) // 确保结束X坐标不小于50
        
        val randomOffsetX = Random.nextInt(-30, 31)
        val randomOffsetY = Random.nextInt(-50, 51)
        
        // 确保所有坐标都在有效范围内
        val finalStartX = (startX + randomOffsetX).coerceIn(50, screenWidth - 50)
        val finalStartY = (centerY + randomOffsetY).coerceIn(50, screenHeight - 50)
        val finalEndX = (endX + randomOffsetX / 2).coerceIn(50, screenWidth - 50)
        val finalEndY = (centerY + randomOffsetY / 2).coerceIn(50, screenHeight - 50)
        
        LogCollector.addLog("D", TAG, "◀️ 向左滑动坐标: ($finalStartX,$finalStartY) -> ($finalEndX,$finalEndY)")
        
        return swipe(
            startX = finalStartX,
            startY = finalStartY,
            endX = finalEndX,
            endY = finalEndY,
            duration = duration + Random.nextLong(-50, 51)
        )
    }
    
    /**
     * 向右滑动
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param distance 滑动距离
     * @param duration 持续时间
     * @return 是否成功
     */
    fun swipeRight(
        screenWidth: Int = 1080,
        screenHeight: Int = 1920,
        distance: Int = (screenWidth * 0.8).toInt(),
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        val centerY = screenHeight / 2
        val startX = screenWidth / 4
        val endX = (startX + distance).coerceAtMost(screenWidth - 50) // 确保结束X坐标不超过屏幕
        
        val randomOffsetX = Random.nextInt(-30, 31)
        val randomOffsetY = Random.nextInt(-50, 51)
        
        // 确保所有坐标都在有效范围内
        val finalStartX = (startX + randomOffsetX).coerceIn(50, screenWidth - 50)
        val finalStartY = (centerY + randomOffsetY).coerceIn(50, screenHeight - 50)
        val finalEndX = (endX + randomOffsetX / 2).coerceIn(50, screenWidth - 50)
        val finalEndY = (centerY + randomOffsetY / 2).coerceIn(50, screenHeight - 50)
        
        LogCollector.addLog("D", TAG, "▶️ 向右滑动坐标: ($finalStartX,$finalStartY) -> ($finalEndX,$finalEndY)")
        
        return swipe(
            startX = finalStartX,
            startY = finalStartY,
            endX = finalEndX,
            endY = finalEndY,
            duration = duration + Random.nextLong(-50, 51)
        )
    }
    
    /**
     * 直播模式特殊滑动：先上滑再下滑为一次完整操作
     * 用于滑出直播间再回到直播间
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param duration 单次滑动持续时间
     * @param pauseBetween 两次滑动之间的暂停时间
     * @return 是否成功
     */
    fun performLiveSwipeUpDown(
        screenWidth: Int = 1080,
        screenHeight: Int = 1920,
        duration: Long = DEFAULT_GESTURE_DURATION,
        pauseBetween: Long = 500L
    ): Boolean {
        try {
            LogCollector.addLog("I", TAG, "🔄 直播模式：执行上滑+下滑组合操作")
            
            // 第一步：向上滑动（滑出直播间）
            val upSuccess = swipeUp(screenWidth, screenHeight, (screenHeight * 0.8).toInt(), duration)
            if (!upSuccess) {
                LogCollector.addLog("W", TAG, "⚠️ 直播上滑失败")
                return false
            }
            
            LogCollector.addLog("I", TAG, "✅ 直播上滑完成，等待${pauseBetween}ms后下滑")
            
            // 等待一段时间
            Thread.sleep(pauseBetween)
            
            // 第二步：向下滑动（回到直播间）
            val downSuccess = swipeDown(screenWidth, screenHeight, (screenHeight * 0.8).toInt(), duration)
            if (!downSuccess) {
                LogCollector.addLog("W", TAG, "⚠️ 直播下滑失败")
                return false
            }
            
            LogCollector.addLog("I", TAG, "✅ 直播模式上下滑动组合操作完成")
            return true
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 直播模式滑动操作异常: ${e.message}")
            return false
        }
    }
    
    /**
     * 双击操作
     * @param x X坐标
     * @param y Y坐标
     * @param interval 两次点击间隔
     * @return 是否成功
     */
    fun doubleClick(x: Int, y: Int, interval: Long = 150L): Boolean {
        LogCollector.addLog("I", TAG, "👆👆 执行双击操作: ($x, $y)")
        
        val firstClick = click(x, y)
        if (!firstClick) {
            return false
        }
        
        try {
            Thread.sleep(interval)
        } catch (e: InterruptedException) {
            return false
        }
        
        return click(x, y)
    }
    
    /**
     * 缩放手势 (双指)
     * @param centerX 中心X坐标
     * @param centerY 中心Y坐标
     * @param startDistance 起始距离
     * @param endDistance 结束距离
     * @param duration 持续时间
     * @return 是否成功
     */
    fun zoom(
        centerX: Int,
        centerY: Int,
        startDistance: Int = 200,
        endDistance: Int = 400,
        duration: Long = 500L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            LogCollector.addLog("W", TAG, "❌ Android版本过低，不支持手势操作")
            return false
        }
        
        return try {
            LogCollector.addLog("I", TAG, "🔍 执行缩放操作: 中心($centerX,$centerY) ${startDistance}->${endDistance}")
            
            // 第一个手指路径
            val path1 = Path().apply {
                moveTo(centerX - startDistance / 2f, centerY.toFloat())
                lineTo(centerX - endDistance / 2f, centerY.toFloat())
            }
            
            // 第二个手指路径
            val path2 = Path().apply {
                moveTo(centerX + startDistance / 2f, centerY.toFloat())
                lineTo(centerX + endDistance / 2f, centerY.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
            
            val success = accessibilityService.dispatchGesture(
                gestureBuilder.build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        LogCollector.addLog("I", TAG, "✅ 缩放操作完成")
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        LogCollector.addLog("W", TAG, "⚠️ 缩放操作被取消")
                    }
                },
                null
            )
            
            success
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 缩放操作异常: ${e.message}")
            false
        }
    }
    
    /**
     * 获取屏幕尺寸 (从AccessibilityService中获取)
     * @return 屏幕尺寸Point
     */
    fun getScreenSize(): Point {
        val displayMetrics = accessibilityService.resources.displayMetrics
        return Point(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
    
    /**
     * 检查手势功能是否可用
     * @return 是否支持手势
     */
    fun isGestureSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    
    /**
     * 模拟人类操作的随机延迟
     * @param baseDelay 基础延迟时间
     * @param randomRange 随机范围
     */
    fun humanLikeDelay(baseDelay: Long = 1000L, randomRange: Long = 500L) {
        val delay = baseDelay + Random.nextLong(-randomRange, randomRange + 1)
        try {
            Thread.sleep(delay.coerceAtLeast(100L))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
    
    /**
     * 使用无障碍节点执行点击 (备用方案)
     * @param node 节点
     * @return 是否成功
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return try {
            LogCollector.addLog("I", TAG, "🎯 使用节点点击: ${node.className}")
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                LogCollector.addLog("I", TAG, "✅ 节点点击成功")
            } else {
                LogCollector.addLog("W", TAG, "⚠️ 节点点击失败")
            }
            success
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 节点点击异常: ${e.message}")
            false
        }
    }
} 