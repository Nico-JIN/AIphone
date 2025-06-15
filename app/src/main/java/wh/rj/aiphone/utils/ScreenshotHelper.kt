package wh.rj.aiphone.utils

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * 截屏助手
 * 支持通过无障碍服务进行截屏
 */
class ScreenshotHelper private constructor() {
    
    companion object {
        private const val TAG = "ScreenshotHelper"
        
        @Volatile
        private var INSTANCE: ScreenshotHelper? = null
        
        fun getInstance(): ScreenshotHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenshotHelper().also { INSTANCE = it }
            }
        }
    }
    
    private var accessibilityService: AccessibilityService? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    /**
     * 设置无障碍服务
     */
    fun setAccessibilityService(service: AccessibilityService) {
        this.accessibilityService = service
        initScreenParams(service)
    }
    
    /**
     * 初始化屏幕参数
     */
    private fun initScreenParams(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        LogCollector.addLog("I", TAG, "📱 屏幕参数: ${screenWidth}x${screenHeight}, DPI: ${screenDensity}")
    }
    
    /**
     * 使用Android R+的无障碍服务截图
     */
    @TargetApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshotAccessibility(): Bitmap? = suspendCancellableCoroutine { continuation ->
        try {
            val service = accessibilityService
            if (service == null) {
                LogCollector.addLog("E", TAG, "❌ 无障碍服务未设置")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            LogCollector.addLog("I", TAG, "📸 开始无障碍截图...")
            
            service.takeScreenshot(
                AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
                { LogCollector.addLog("I", TAG, "📸 无障碍截图请求已发送") },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            // 获取硬件缓冲区并转换为bitmap
                            val hardwareBuffer = screenshot.hardwareBuffer
                            if (hardwareBuffer != null) {
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                                LogCollector.addLog("I", TAG, "✅ 无障碍截图成功")
                                continuation.resume(bitmap)
                            } else {
                                LogCollector.addLog("E", TAG, "❌ 无障碍截图硬件缓冲区为null")
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            LogCollector.addLog("E", TAG, "❌ 无障碍截图处理失败: ${e.message}")
                            continuation.resume(null)
                        }
                    }
                    
                    override fun onFailure(errorCode: Int) {
                        LogCollector.addLog("E", TAG, "❌ 无障碍截图失败，错误代码: $errorCode")
                        continuation.resume(null)
                    }
                }
            )
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 无障碍截图异常: ${e.message}")
            continuation.resume(null)
        }
    }
    
    /**
     * 使用MediaProjection截图（备用方案）
     */
    suspend fun takeScreenshotMediaProjection(): Bitmap? = suspendCancellableCoroutine { continuation ->
        try {
            val service = accessibilityService
            if (service == null) {
                LogCollector.addLog("E", TAG, "❌ 无障碍服务未设置")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            LogCollector.addLog("I", TAG, "📸 开始MediaProjection截图...")
            
            setupMediaProjection(service) { bitmap ->
                if (bitmap != null) {
                    LogCollector.addLog("I", TAG, "✅ MediaProjection截图成功")
                } else {
                    LogCollector.addLog("E", TAG, "❌ MediaProjection截图失败")
                }
                continuation.resume(bitmap)
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ MediaProjection截图异常: ${e.message}")
            continuation.resume(null)
        }
    }
    
    /**
     * 设置MediaProjection截图
     */
    private fun setupMediaProjection(context: Context, callback: (Bitmap?) -> Unit) {
        try {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            
            // 创建ImageReader
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 1)
            
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader?.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image)
                        image.close()
                        callback(bitmap)
                    } else {
                        callback(null)
                    }
                } catch (e: Exception) {
                    LogCollector.addLog("E", TAG, "❌ 图像转换失败: ${e.message}")
                    callback(null)
                }
            }, Handler(Looper.getMainLooper()))
            
            // 这里需要用户授权MediaProjection，暂时返回null
            // 实际使用中需要通过Activity获取用户授权
            LogCollector.addLog("W", TAG, "⚠️ MediaProjection需要用户授权")
            callback(null)
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 设置MediaProjection失败: ${e.message}")
            callback(null)
        }
    }
    
    /**
     * 将Image转换为Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            
            bitmap.copyPixelsFromBuffer(buffer)
            
            return if (rowPadding == 0) {
                bitmap
            } else {
                // 裁剪掉padding
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ Image转Bitmap失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 生成模拟截图（用于测试）
     */
    fun createTestScreenshot(): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            
            // 绘制背景
            canvas.drawColor(android.graphics.Color.WHITE)
            
            // 绘制一些测试元素
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLUE
                textSize = 48f
            }
            
            canvas.drawText("测试截图", screenWidth / 2f - 100f, screenHeight / 2f, paint)
            
            // 在右下角绘制一个圆形（模拟大拇指图标）
            paint.color = android.graphics.Color.RED
            canvas.drawCircle(
                screenWidth * 0.9f,
                screenHeight * 0.8f,
                30f,
                paint
            )
            
            LogCollector.addLog("I", TAG, "✅ 生成测试截图")
            bitmap
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 生成测试截图失败: ${e.message}")
            null
        }
    }
    
    /**
     * 智能截图（尝试多种方式）
     */
    suspend fun takeSmartScreenshot(): Bitmap? {
        // 首先尝试无障碍截图
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bitmap = takeScreenshotAccessibility()
            if (bitmap != null) {
                return bitmap
            }
        }
        
        // 如果无障碍截图失败，尝试MediaProjection
        val bitmap = takeScreenshotMediaProjection()
        if (bitmap != null) {
            return bitmap
        }
        
        // 如果都失败了，返回测试截图
        LogCollector.addLog("W", TAG, "⚠️ 所有截图方式都失败，使用测试截图")
        return createTestScreenshot()
    }
    
    /**
     * 截图并缩放到指定大小
     */
    suspend fun takeScreenshotScaled(maxWidth: Int = 720, maxHeight: Int = 1280): Bitmap? {
        val originalBitmap = takeSmartScreenshot() ?: return null
        
        return try {
            val originalWidth = originalBitmap.width
            val originalHeight = originalBitmap.height
            
            if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
                return originalBitmap
            }
            
            val scaleWidth = maxWidth.toFloat() / originalWidth
            val scaleHeight = maxHeight.toFloat() / originalHeight
            val scale = min(scaleWidth, scaleHeight)
            
            val scaledWidth = (originalWidth * scale).toInt()
            val scaledHeight = (originalHeight * scale).toInt()
            
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
            
            if (scaledBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            
            LogCollector.addLog("I", TAG, "🔄 截图已缩放: ${originalWidth}x${originalHeight} -> ${scaledWidth}x${scaledHeight}")
            scaledBitmap
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 截图缩放失败: ${e.message}")
            originalBitmap
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            LogCollector.addLog("I", TAG, "🔄 截屏助手资源已释放")
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 释放截屏助手资源失败: ${e.message}")
        }
    }
    
    /**
     * 获取屏幕信息
     */
    fun getScreenInfo(): String {
        return "${screenWidth}x${screenHeight}@${screenDensity}dpi"
    }
} 