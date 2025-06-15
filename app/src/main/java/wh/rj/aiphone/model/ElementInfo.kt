package wh.rj.aiphone.model

import android.graphics.Rect
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 页面元素信息数据模型
 * 用于存储从无障碍服务获取的元素详细信息
 */
@Parcelize
data class ElementInfo(
    /** 元素文本内容 */
    val text: String?,
    
    /** 元素内容描述 */
    val contentDescription: String?,
    
    /** 元素类名 */
    val className: String?,
    
    /** 元素View ID */
    val viewId: String?,
    
    /** 元素在屏幕上的位置边界 */
    val bounds: Rect,
    
    /** 元素中心点X坐标 */
    val centerX: Int = bounds.centerX(),
    
    /** 元素中心点Y坐标 */
    val centerY: Int = bounds.centerY(),
    
    /** 是否可点击 */
    val isClickable: Boolean,
    
    /** 是否可长按 */
    val isLongClickable: Boolean,
    
    /** 是否可滚动 */
    val isScrollable: Boolean,
    
    /** 是否可编辑 */
    val isEditable: Boolean,
    
    /** 是否可选中 */
    val isCheckable: Boolean,
    
    /** 是否已选中 */
    val isChecked: Boolean,
    
    /** 是否启用 */
    val isEnabled: Boolean,
    
    /** 是否获得焦点 */
    val isFocused: Boolean,
    
    /** 元素深度 (在视图树中的层级) */
    val depth: Int,
    
    /** 元素索引 (在父容器中的位置) */
    val index: Int,
    
    /** 元素类型 (根据内容和属性推断) */
    val elementType: ElementType,
    
    /** 重要性评分 (0-100，用于排序和筛选) */
    val importance: Int,
    
    /** 检测时间戳 */
    val detectTime: Long = System.currentTimeMillis()
) : Parcelable {

    /**
     * 元素类型枚举
     */
    enum class ElementType {
        LIKE_BUTTON,     // 点赞按钮
        FOLLOW_BUTTON,   // 关注按钮
        COMMENT_BUTTON,  // 评论按钮
        SHARE_BUTTON,    // 分享按钮
        PLAY_BUTTON,     // 播放按钮
        USER_AVATAR,     // 用户头像
        TEXT_VIEW,       // 文本显示
        EDIT_TEXT,       // 文本输入
        IMAGE_VIEW,      // 图片显示
        BUTTON,          // 普通按钮
        UNKNOWN          // 未知类型
    }

    companion object {
        /**
         * 从AccessibilityNodeInfo创建ElementInfo
         */
        fun fromAccessibilityNode(
            node: android.view.accessibility.AccessibilityNodeInfo,
            depth: Int = 0,
            index: Int = 0
        ): ElementInfo {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            val className = node.className?.toString()
            val viewId = node.viewIdResourceName
            
            // 推断元素类型
            val elementType = inferElementType(text, desc, className, viewId)
            
            // 计算重要性评分
            val importance = calculateImportance(elementType, node, text, desc)
            
            return ElementInfo(
                text = text,
                contentDescription = desc,
                className = className,
                viewId = viewId,
                bounds = bounds,
                isClickable = node.isClickable,
                isLongClickable = node.isLongClickable,
                isScrollable = node.isScrollable,
                isEditable = node.isEditable,
                isCheckable = node.isCheckable,
                isChecked = node.isChecked,
                isEnabled = node.isEnabled,
                isFocused = node.isFocused,
                depth = depth,
                index = index,
                elementType = elementType,
                importance = importance
            )
        }
        
        /**
         * 推断元素类型
         */
        private fun inferElementType(
            text: String?,
            desc: String?,
            className: String?,
            viewId: String?
        ): ElementType {
            val lowerText = text?.lowercase() ?: ""
            val lowerDesc = desc?.lowercase() ?: ""
            val lowerViewId = viewId?.lowercase() ?: ""
            
            return when {
                // 点赞相关
                lowerText.contains("点赞") || lowerText.contains("like") ||
                lowerDesc.contains("点赞") || lowerDesc.contains("like") ||
                lowerViewId.contains("like") -> ElementType.LIKE_BUTTON
                
                // 关注相关
                lowerText.contains("关注") || lowerText.contains("follow") ||
                lowerDesc.contains("关注") || lowerDesc.contains("follow") ||
                lowerViewId.contains("follow") -> ElementType.FOLLOW_BUTTON
                
                // 评论相关
                lowerText.contains("评论") || lowerText.contains("comment") ||
                lowerDesc.contains("评论") || lowerDesc.contains("comment") ||
                lowerViewId.contains("comment") -> ElementType.COMMENT_BUTTON
                
                // 分享相关
                lowerText.contains("分享") || lowerText.contains("share") ||
                lowerDesc.contains("分享") || lowerDesc.contains("share") ||
                lowerViewId.contains("share") -> ElementType.SHARE_BUTTON
                
                // 播放相关
                lowerText.contains("播放") || lowerText.contains("play") ||
                lowerDesc.contains("播放") || lowerDesc.contains("play") ||
                lowerViewId.contains("play") -> ElementType.PLAY_BUTTON
                
                // 头像相关
                lowerDesc.contains("头像") || lowerDesc.contains("avatar") ||
                lowerViewId.contains("avatar") -> ElementType.USER_AVATAR
                
                // 根据类名判断
                className?.contains("EditText") == true -> ElementType.EDIT_TEXT
                className?.contains("TextView") == true -> ElementType.TEXT_VIEW
                className?.contains("ImageView") == true -> ElementType.IMAGE_VIEW
                className?.contains("Button") == true -> ElementType.BUTTON
                
                else -> ElementType.UNKNOWN
            }
        }
        
        /**
         * 计算元素重要性评分
         */
        private fun calculateImportance(
            elementType: ElementType,
            node: android.view.accessibility.AccessibilityNodeInfo,
            text: String?,
            desc: String?
        ): Int {
            var score = 0
            
            // 基础类型分数
            score += when (elementType) {
                ElementType.LIKE_BUTTON -> 90
                ElementType.FOLLOW_BUTTON -> 85
                ElementType.COMMENT_BUTTON -> 80
                ElementType.SHARE_BUTTON -> 75
                ElementType.PLAY_BUTTON -> 70
                ElementType.USER_AVATAR -> 60
                ElementType.BUTTON -> 50
                ElementType.EDIT_TEXT -> 40
                ElementType.TEXT_VIEW -> 30
                ElementType.IMAGE_VIEW -> 20
                ElementType.UNKNOWN -> 10
            }
            
            // 可点击性加分
            if (node.isClickable) score += 20
            if (node.isLongClickable) score += 10
            
            // 是否有意义的文本
            if (!text.isNullOrBlank() && text.length > 1) score += 15
            if (!desc.isNullOrBlank() && desc.length > 1) score += 10
            
            // 位置和大小因素
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val width = bounds.width()
            val height = bounds.height()
            
            // 合理大小的元素加分
            if (width in 50..500 && height in 30..200) score += 10
            
            // 屏幕可见区域内加分
            if (bounds.top >= 0 && bounds.left >= 0) score += 5
            
            return score.coerceIn(0, 100)
        }
    }
    
    /**
     * 获取元素描述信息
     */
    fun getDescription(): String {
        return buildString {
            append("类型: ${getTypeDescription()}")
            if (!text.isNullOrBlank()) append("\n文本: $text")
            if (!contentDescription.isNullOrBlank()) append("\n描述: $contentDescription")
            append("\n位置: (${centerX}, ${centerY})")
            append("\n大小: ${bounds.width()} × ${bounds.height()}")
            append("\n属性: ${getAttributesDescription()}")
        }
    }
    
    /**
     * 获取类型描述
     */
    private fun getTypeDescription(): String {
        return when (elementType) {
            ElementType.LIKE_BUTTON -> "👍 点赞按钮"
            ElementType.FOLLOW_BUTTON -> "➕ 关注按钮"
            ElementType.COMMENT_BUTTON -> "💬 评论按钮"
            ElementType.SHARE_BUTTON -> "📤 分享按钮"
            ElementType.PLAY_BUTTON -> "▶️ 播放按钮"
            ElementType.USER_AVATAR -> "👤 用户头像"
            ElementType.TEXT_VIEW -> "📝 文本显示"
            ElementType.EDIT_TEXT -> "✏️ 文本输入"
            ElementType.IMAGE_VIEW -> "🖼️ 图片显示"
            ElementType.BUTTON -> "🔘 普通按钮"
            ElementType.UNKNOWN -> "❓ 未知类型"
        }
    }
    
    /**
     * 获取属性描述
     */
    private fun getAttributesDescription(): String {
        val attributes = mutableListOf<String>()
        if (isClickable) attributes.add("可点击")
        if (isLongClickable) attributes.add("可长按")
        if (isScrollable) attributes.add("可滚动")
        if (isEditable) attributes.add("可编辑")
        if (isCheckable) attributes.add("可选中")
        if (isChecked) attributes.add("已选中")
        if (!isEnabled) attributes.add("已禁用")
        if (isFocused) attributes.add("已获焦点")
        
        return if (attributes.isEmpty()) "无特殊属性" else attributes.joinToString(", ")
    }
    
    /**
     * 是否为重要元素 (评分大于50)
     */
    fun isImportant(): Boolean = importance > 50
    
    /**
     * 是否为可操作元素
     */
    fun isActionable(): Boolean = isClickable || isLongClickable || isScrollable || isEditable
    
    /**
     * 获取简短摘要
     */
    fun getSummary(): String {
        val typeDesc = getTypeDescription()
        val position = "(${centerX}, ${centerY})"
        val mainText = text?.take(10) ?: contentDescription?.take(10) ?: "无文本"
        return "$typeDesc $position - $mainText"
    }
} 