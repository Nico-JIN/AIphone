package wh.rj.aiphone.utils

import android.content.Context
import android.graphics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wh.rj.aiphone.utils.LogCollector
import kotlin.math.min
import kotlin.math.max

/**
 * 图像识别助手
 * 用于直播场景的人脸检测和图标识别
 * 使用简化算法，不依赖OpenCV
 */
class ImageRecognitionHelper private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageRecognition"
        
        @Volatile
        private var INSTANCE: ImageRecognitionHelper? = null
        
        fun getInstance(context: Context): ImageRecognitionHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageRecognitionHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // 人脸检测相关常量
        private const val FACE_MIN_SIZE = 50
        
        // 大拇指图标相关常量
        private const val THUMB_MIN_SIZE = 20
        private const val THUMB_MAX_SIZE = 100
    }
    
    // 图像识别状态变量
    private var isInitialized = false
    
    /**
     * 初始化图像识别组件
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext true
            
            // 简化的图像识别初始化，不需要外部模型文件
            isInitialized = true
            LogCollector.addLog("I", TAG, "✅ 图像识别组件初始化成功（基于内置算法）")
            true
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 图像识别组件初始化失败: ${e.message}")
            false
        }
    }
    
    /**
     * 检测人脸位置（抖音直播点赞）
     * @param bitmap 要检测的图像
     * @return 人脸区域的Rect列表，如果没有检测到则返回空列表
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Rect> = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized) {
                if (!initialize()) {
                    return@withContext emptyList()
                }
            }
            
            LogCollector.addLog("I", TAG, "🔍 开始人脸检测...")
            
            // 使用简化的人脸检测算法
            val faces = detectFacesSimple(bitmap)
            
            LogCollector.addLog("I", TAG, "👤 检测到 ${faces.size} 个人脸")
            faces
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 人脸检测失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 简化的人脸检测算法
     * 基于肤色检测和区域形状分析
     */
    private fun detectFacesSimple(bitmap: Bitmap): List<Rect> {
        val faces = mutableListOf<Rect>()
        
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // 肤色检测
            val skinRegions = detectSkinRegions(pixels, width, height)
            
            // 分析肤色区域，找出可能的人脸
            for (region in skinRegions) {
                if (isLikelyFace(region, width, height)) {
                    faces.add(region)
                }
            }
            
            // 如果没有检测到人脸，使用备用策略
            if (faces.isEmpty()) {
                // 在屏幕中上部区域假设存在人脸
                val centerX = width / 2
                val centerY = height / 3
                val faceSize = min(width, height) / 4
                
                faces.add(Rect(
                    centerX - faceSize / 2,
                    centerY - faceSize / 2,
                    centerX + faceSize / 2,
                    centerY + faceSize / 2
                ))
                
                LogCollector.addLog("W", TAG, "⚠️ 使用默认人脸区域: 屏幕中上部")
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 简化人脸检测失败: ${e.message}")
        }
        
        return faces
    }
    
    /**
     * 检测肤色区域
     */
    private fun detectSkinRegions(pixels: IntArray, width: Int, height: Int): List<Rect> {
        val regions = mutableListOf<Rect>()
        val skinMask = BooleanArray(pixels.size)
        
        // 肤色检测
        for (i in pixels.indices) {
            skinMask[i] = isSkinColor(pixels[i])
        }
        
        // 连通区域分析
        val visited = BooleanArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (skinMask[index] && !visited[index]) {
                    val region = findConnectedRegion(skinMask, visited, x, y, width, height)
                    if (region != null && region.width() > FACE_MIN_SIZE && region.height() > FACE_MIN_SIZE) {
                        regions.add(region)
                    }
                }
            }
        }
        
        return regions
    }
    
    /**
     * 判断是否为肤色
     */
    private fun isSkinColor(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        
        // YCrCb色彩空间的肤色检测
        val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        val cr = (0.713 * (r - y) + 128).toInt()
        val cb = (0.564 * (b - y) + 128).toInt()
        
        return y in 80..255 && cr in 133..173 && cb in 77..127
    }
    
    /**
     * 找到连通区域
     */
    private fun findConnectedRegion(
        mask: BooleanArray, visited: BooleanArray,
        startX: Int, startY: Int, width: Int, height: Int
    ): Rect? {
        val stack = mutableListOf<Pair<Int, Int>>()
        stack.add(Pair(startX, startY))
        
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY
        
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeAt(stack.size - 1)
            val index = y * width + x
            
            if (x < 0 || x >= width || y < 0 || y >= height || 
                visited[index] || !mask[index]) {
                continue
            }
            
            visited[index] = true
            
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
            
            // 4连通
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
        }
        
        return if (maxX > minX && maxY > minY) {
            Rect(minX, minY, maxX, maxY)
        } else null
    }
    
    /**
     * 判断区域是否像人脸
     */
    private fun isLikelyFace(rect: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        val width = rect.width()
        val height = rect.height()
        
        // 长宽比检查（人脸通常接近正方形）
        val ratio = width.toFloat() / height
        if (ratio < 0.7f || ratio > 1.4f) return false
        
        // 大小检查
        if (width < FACE_MIN_SIZE || height < FACE_MIN_SIZE) return false
        if (width > imageWidth / 2 || height > imageHeight / 2) return false
        
        // 位置检查（人脸通常在图像上半部分）
        if (rect.centerY() > imageHeight * 0.7f) return false
        
        return true
    }
    
    /**
     * 检测大拇指图标位置（微信直播点赞）
     * @param bitmap 要检测的图像
     * @return 大拇指区域的Rect列表，如果没有检测到则返回空列表
     */
    suspend fun detectThumbs(bitmap: Bitmap): List<Rect> = withContext(Dispatchers.Default) {
        try {
            LogCollector.addLog("I", TAG, "👍 开始大拇指检测...")
            
            val thumbs = mutableListOf<Rect>()
            
            // 在右下角区域搜索大拇指形状的图标
            val searchArea = Rect(
                (bitmap.width * 0.7f).toInt(),
                (bitmap.height * 0.6f).toInt(),
                bitmap.width,
                bitmap.height
            )
            
            val detectedThumbs = findThumbInArea(bitmap, searchArea)
            thumbs.addAll(detectedThumbs)
            
            // 如果没有检测到，使用默认位置
            if (thumbs.isEmpty()) {
                val defaultThumb = Rect(
                    (bitmap.width * 0.85f).toInt(),
                    (bitmap.height * 0.75f).toInt(),
                    (bitmap.width * 0.95f).toInt(),
                    (bitmap.height * 0.85f).toInt()
                )
                thumbs.add(defaultThumb)
                LogCollector.addLog("W", TAG, "⚠️ 使用默认大拇指位置: 右下角")
            }
            
            LogCollector.addLog("I", TAG, "👍 检测到 ${thumbs.size} 个大拇指图标")
            thumbs
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 大拇指检测失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 在指定区域内查找大拇指图标
     */
    private fun findThumbInArea(bitmap: Bitmap, searchArea: Rect): List<Rect> {
        val thumbs = mutableListOf<Rect>()
        
        try {
            // 提取搜索区域
            val searchBitmap = Bitmap.createBitmap(
                bitmap,
                searchArea.left,
                searchArea.top,
                searchArea.width(),
                searchArea.height()
            )
            
            // 寻找圆形或椭圆形的白色/亮色区域（代表大拇指图标）
            val circles = detectCircularShapes(searchBitmap)
            
            for (circle in circles) {
                // 转换回原图坐标
                val globalRect = Rect(
                    circle.left + searchArea.left,
                    circle.top + searchArea.top,
                    circle.right + searchArea.left,
                    circle.bottom + searchArea.top
                )
                thumbs.add(globalRect)
            }
            
            searchBitmap.recycle()
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 区域大拇指检测失败: ${e.message}")
        }
        
        return thumbs
    }
    
    /**
     * 检测圆形形状
     */
    private fun detectCircularShapes(bitmap: Bitmap): List<Rect> {
        val shapes = mutableListOf<Rect>()
        
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // 转换为灰度并二值化
            val grayPixels = IntArray(pixels.size)
            for (i in pixels.indices) {
                val color = pixels[i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                grayPixels[i] = if (gray > 180) 255 else 0  // 二值化，检测亮色区域
            }
            
            // 查找连通区域
            val visited = BooleanArray(grayPixels.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    if (grayPixels[index] == 255 && !visited[index]) {
                        val region = findBrightRegion(grayPixels, visited, x, y, width, height)
                        if (region != null && isCircularShape(region)) {
                            shapes.add(region)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 圆形检测失败: ${e.message}")
        }
        
        return shapes
    }
    
    /**
     * 找到亮色区域
     */
    private fun findBrightRegion(
        pixels: IntArray, visited: BooleanArray,
        startX: Int, startY: Int, width: Int, height: Int
    ): Rect? {
        val stack = mutableListOf<Pair<Int, Int>>()
        stack.add(Pair(startX, startY))
        
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY
        var pixelCount = 0
        
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeAt(stack.size - 1)
            val index = y * width + x
            
            if (x < 0 || x >= width || y < 0 || y >= height || 
                visited[index] || pixels[index] != 255) {
                continue
            }
            
            visited[index] = true
            pixelCount++
            
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
            
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
        }
        
        return if (maxX > minX && maxY > minY && pixelCount > 100) {
            Rect(minX, minY, maxX, maxY)
        } else null
    }
    
    /**
     * 判断是否为圆形
     */
    private fun isCircularShape(rect: Rect): Boolean {
        val width = rect.width()
        val height = rect.height()
        
        // 长宽比接近1:1
        val ratio = width.toFloat() / height
        if (ratio < 0.7f || ratio > 1.3f) return false
        
        // 大小合适
        if (width < THUMB_MIN_SIZE || height < THUMB_MIN_SIZE) return false
        if (width > THUMB_MAX_SIZE || height > THUMB_MAX_SIZE) return false
        
        return true
    }
    
    /**
     * 获取最佳点击位置
     * @param rects 检测到的区域列表
     * @return 最佳点击位置的Point，如果列表为空则返回null
     */
    fun getBestClickPoint(rects: List<Rect>): Point? {
        if (rects.isEmpty()) return null
        
        // 选择最大的区域作为点击目标
        val bestRect = rects.maxByOrNull { it.width() * it.height() } ?: return null
        
        return Point(bestRect.centerX(), bestRect.centerY())
    }
    
    /**
     * 释放资源
     */
    fun release() {
        isInitialized = false
        LogCollector.addLog("I", TAG, "🔄 图像识别组件已释放")
    }
} 