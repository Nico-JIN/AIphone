package wh.rj.aiphone.utils

import android.view.accessibility.AccessibilityNodeInfo
import wh.rj.aiphone.model.ElementInfo
import wh.rj.aiphone.utils.LogCollector
import java.util.concurrent.ConcurrentHashMap

/**
 * 页面元素检测器
 * 提供专业的无障碍元素检测和分析功能
 */
class ElementDetector {
    
    companion object {
        private const val TAG = "ElementDetector"
        
        /** 单例实例 */
        @Volatile
        private var INSTANCE: ElementDetector? = null
        
        fun getInstance(): ElementDetector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ElementDetector().also { INSTANCE = it }
            }
        }
    }
    
    /** 缓存检测到的元素，避免重复检测 */
    private val elementCache = ConcurrentHashMap<String, List<ElementInfo>>()
    
    /** 最后检测时间 */
    private var lastDetectionTime = 0L
    
    /** 缓存有效期 (毫秒) */
    private val cacheValidDuration = 2000L
    
    /**
     * 检测页面所有元素
     * @param rootNode 根节点
     * @param useCache 是否使用缓存
     * @return 元素列表，按重要性排序
     */
    fun detectAllElements(rootNode: AccessibilityNodeInfo?, useCache: Boolean = true): List<ElementInfo> {
        if (rootNode == null) {
            LogCollector.addLog("W", TAG, "❌ 根节点为空，无法检测元素")
            return emptyList()
        }
        
        val packageName = rootNode.packageName?.toString() ?: "unknown"
        val cacheKey = "${packageName}_${System.currentTimeMillis() / 1000}" // 按秒级缓存
        
        // 检查缓存
        if (useCache && isCacheValid()) {
            elementCache[cacheKey]?.let { cachedElements ->
                LogCollector.addLog("I", TAG, "📋 使用缓存的元素检测结果 (${cachedElements.size}个)")
                return cachedElements
            }
        }
        
        LogCollector.addLog("I", TAG, "🔍 开始检测页面元素...")
        val startTime = System.currentTimeMillis()
        
        val allElements = mutableListOf<ElementInfo>()
        
        try {
            // 遍历所有节点
            traverseNode(rootNode, allElements, 0, 0)
            
            // 按重要性排序
            val sortedElements = allElements
                .filter { it.importance > 0 } // 过滤无效元素
                .sortedByDescending { it.importance }
            
            val detectionTime = System.currentTimeMillis() - startTime
            LogCollector.addLog("I", TAG, "✅ 元素检测完成: ${sortedElements.size}个有效元素，耗时${detectionTime}ms")
            
            // 更新缓存
            if (useCache) {
                elementCache.clear() // 清除旧缓存
                elementCache[cacheKey] = sortedElements
                lastDetectionTime = System.currentTimeMillis()
            }
            
            // 记录统计信息
            logDetectionStatistics(sortedElements)
            
            return sortedElements
            
        } catch (e: Exception) {
            LogCollector.addLog("E", TAG, "❌ 元素检测异常: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * 搜索特定元素
     * @param elements 元素列表
     * @param keyword 搜索关键词
     * @param elementType 元素类型过滤
     * @return 匹配的元素列表
     */
    fun searchElements(
        elements: List<ElementInfo>,
        keyword: String,
        elementType: ElementInfo.ElementType? = null
    ): List<ElementInfo> {
        if (keyword.isBlank() && elementType == null) return elements
        
        val lowerKeyword = keyword.lowercase()
        
        return elements.filter { element ->
            // 类型过滤
            val typeMatch = elementType == null || element.elementType == elementType
            
            // 关键词匹配
            val keywordMatch = keyword.isBlank() || 
                element.text?.lowercase()?.contains(lowerKeyword) == true ||
                element.contentDescription?.lowercase()?.contains(lowerKeyword) == true ||
                element.viewId?.lowercase()?.contains(lowerKeyword) == true ||
                element.className?.lowercase()?.contains(lowerKeyword) == true
            
            typeMatch && keywordMatch
        }.sortedByDescending { it.importance }
    }
    
    /**
     * 获取重要元素 (评分 > 50)
     * @param elements 元素列表  
     * @return 重要元素列表
     */
    fun getImportantElements(elements: List<ElementInfo>): List<ElementInfo> {
        return elements.filter { it.isImportant() }
            .sortedByDescending { it.importance }
    }
    
    /**
     * 获取可操作元素
     * @param elements 元素列表
     * @return 可操作元素列表
     */
    fun getActionableElements(elements: List<ElementInfo>): List<ElementInfo> {
        return elements.filter { it.isActionable() }
            .sortedByDescending { it.importance }
    }
    
    /**
     * 按类型分组元素
     * @param elements 元素列表
     * @return 按类型分组的Map
     */
    fun groupElementsByType(elements: List<ElementInfo>): Map<ElementInfo.ElementType, List<ElementInfo>> {
        return elements.groupBy { it.elementType }
            .mapValues { (_, list) -> list.sortedByDescending { it.importance } }
    }
    
    /**
     * 查找最佳点赞按钮
     * @param elements 元素列表
     * @return 最佳点赞按钮，如果没有返回null
     */
    fun findBestLikeButton(elements: List<ElementInfo>): ElementInfo? {
        return elements
            .filter { it.elementType == ElementInfo.ElementType.LIKE_BUTTON && it.isClickable }
            .maxByOrNull { it.importance }
    }
    
    /**
     * 查找最佳关注按钮
     * @param elements 元素列表
     * @return 最佳关注按钮，如果没有返回null
     */
    fun findBestFollowButton(elements: List<ElementInfo>): ElementInfo? {
        return elements
            .filter { it.elementType == ElementInfo.ElementType.FOLLOW_BUTTON && it.isClickable }
            .maxByOrNull { it.importance }
    }
    
    /**
     * 查找最佳评论按钮
     * @param elements 元素列表
     * @return 最佳评论按钮，如果没有返回null
     */
    fun findBestCommentButton(elements: List<ElementInfo>): ElementInfo? {
        return elements
            .filter { it.elementType == ElementInfo.ElementType.COMMENT_BUTTON && it.isClickable }
            .maxByOrNull { it.importance }
    }
    
    /**
     * 获取元素统计信息
     * @param elements 元素列表
     * @return 统计信息字符串
     */
    fun getStatistics(elements: List<ElementInfo>): String {
        val typeStats = groupElementsByType(elements)
        val actionableCount = elements.count { it.isActionable() }
        val importantCount = elements.count { it.isImportant() }
        
        return buildString {
            appendLine("📊 元素检测统计")
            appendLine("━━━━━━━━━━━━━━")
            appendLine("总元素数: ${elements.size}")
            appendLine("重要元素: $importantCount")
            appendLine("可操作元素: $actionableCount")
            appendLine("")
            appendLine("📋 类型分布:")
            
            ElementInfo.ElementType.values().forEach { type ->
                val count = typeStats[type]?.size ?: 0
                if (count > 0) {
                    val typeDesc = getTypeDescription(type)
                    appendLine("  $typeDesc: $count")
                }
            }
        }
    }
    
    /**
     * 递归遍历节点树
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        elements: MutableList<ElementInfo>,
        depth: Int,
        index: Int
    ) {
        try {
            // 创建元素信息
            val elementInfo = ElementInfo.fromAccessibilityNode(node, depth, index)
            
            // 添加有意义的元素
            if (isUsefulElement(elementInfo)) {
                elements.add(elementInfo)
            }
            
            // 递归处理子节点
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { childNode ->
                    traverseNode(childNode, elements, depth + 1, i)
                }
            }
            
        } catch (e: Exception) {
            LogCollector.addLog("W", TAG, "⚠️ 遍历节点异常: ${e.message}")
        }
    }
    
    /**
     * 判断元素是否有用
     */
    private fun isUsefulElement(element: ElementInfo): Boolean {
        // 基本条件：必须有边界且可见
        if (element.bounds.isEmpty || element.bounds.width() <= 0 || element.bounds.height() <= 0) {
            return false
        }
        
        // 太小的元素通常无意义
        if (element.bounds.width() < 20 || element.bounds.height() < 20) {
            return false
        }
        
        // 有文本内容或者描述的元素
        val hasContent = !element.text.isNullOrBlank() || 
                        !element.contentDescription.isNullOrBlank() ||
                        !element.viewId.isNullOrBlank()
        
        // 可操作的元素
        val isActionable = element.isActionable()
        
        // 重要类型的元素
        val isImportantType = element.elementType != ElementInfo.ElementType.UNKNOWN
        
        // 高重要性评分的元素
        val hasHighImportance = element.importance > 30
        
        return (hasContent && isActionable) || isImportantType || hasHighImportance
    }
    
    /**
     * 检查缓存是否有效
     */
    private fun isCacheValid(): Boolean {
        return System.currentTimeMillis() - lastDetectionTime < cacheValidDuration
    }
    
    /**
     * 记录检测统计信息
     */
    private fun logDetectionStatistics(elements: List<ElementInfo>) {
        val typeStats = groupElementsByType(elements)
        val actionableCount = elements.count { it.isActionable() }
        val importantCount = elements.count { it.isImportant() }
        
        LogCollector.addLog("I", TAG, "📊 检测统计 - 总数:${elements.size} 重要:$importantCount 可操作:$actionableCount")
        
        // 记录各类型数量
        ElementInfo.ElementType.values().forEach { type ->
            val count = typeStats[type]?.size ?: 0
            if (count > 0) {
                LogCollector.addLog("D", TAG, "  ${getTypeDescription(type)}: $count")
            }
        }
    }
    
    /**
     * 获取类型描述
     */
    private fun getTypeDescription(type: ElementInfo.ElementType): String {
        return when (type) {
            ElementInfo.ElementType.LIKE_BUTTON -> "👍点赞"
            ElementInfo.ElementType.FOLLOW_BUTTON -> "➕关注"
            ElementInfo.ElementType.COMMENT_BUTTON -> "💬评论"
            ElementInfo.ElementType.SHARE_BUTTON -> "📤分享"
            ElementInfo.ElementType.PLAY_BUTTON -> "▶️播放"
            ElementInfo.ElementType.USER_AVATAR -> "👤头像"
            ElementInfo.ElementType.TEXT_VIEW -> "📝文本"
            ElementInfo.ElementType.EDIT_TEXT -> "✏️输入"
            ElementInfo.ElementType.IMAGE_VIEW -> "🖼️图片"
            ElementInfo.ElementType.BUTTON -> "🔘按钮"
            ElementInfo.ElementType.UNKNOWN -> "❓未知"
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        elementCache.clear()
        lastDetectionTime = 0L
        LogCollector.addLog("I", TAG, "🧹 已清理元素检测缓存")
    }
    
    /**
     * 获取缓存状态
     */
    fun getCacheStatus(): String {
        val cacheSize = elementCache.size
        val isValid = isCacheValid()
        val lastDetection = if (lastDetectionTime > 0) {
            "${(System.currentTimeMillis() - lastDetectionTime)/1000}秒前"
        } else {
            "从未检测"
        }
        
        return "缓存条目: $cacheSize | 有效性: ${if (isValid) "有效" else "已过期"} | 最后检测: $lastDetection"
    }
} 