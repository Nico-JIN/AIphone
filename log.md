# AIPhone 普通模式操作逻辑与流程详解

## 概述

本文档详细记录了AIPhone在普通模式下执行点赞、评论、关注操作的完整逻辑和调用流程，包括接口调用、操作依据、实现细节等。

## 架构总览

```
用户配置任务 -> AIAccessibilityService -> ElementDetector -> GestureHelper -> 系统无障碍服务
                     ↓
               TaskStats更新 -> StatusFloatingService显示
```

---

## 一、普通模式任务执行入口

### 1.1 任务启动流程

**入口接口：** `AIAccessibilityService.startAutomationTask(packageName: String, config: TaskConfig)`

**调用流程：**
1. 用户在TaskConfigActivity配置任务参数
2. 点击"启动任务"按钮
3. 调用`AIAccessibilityService.startAutomationTask()`
4. 服务开始监听无障碍事件

**关键配置参数：**
- `config.enableLikeOperation`: 是否启用点赞操作
- `config.enableFollowOperation`: 是否启用关注操作  
- `config.enableCommentOperation`: 是否启用评论操作
- `config.likeChance`: 点赞概率 (0-100%)
- `config.followChance`: 关注概率 (0-50%)
- `config.commentChance`: 评论概率 (0-50%)

### 1.2 事件处理机制

**核心接口：** `AIAccessibilityService.handleTaskEvent(event: AccessibilityEvent)`

**触发条件：**
- 接收到目标应用的无障碍事件
- 达到设定的操作间隔时间
- 未达到最大操作次数限制

**执行逻辑：**
```kotlin
// 检查操作间隔
if (currentTime - lastOperationTime < config.swipeInterval) return

// 检查最大操作次数
if (operationCount >= config.maxOperations) {
    stopCurrentTask()
    return
}

// 执行自动操作
performAutomaticActions(config)
```

---

## 二、普通模式操作执行

### 2.1 操作调度入口

**核心接口：** `AIAccessibilityService.performNormalModeActions(config: TaskConfig)`

**执行顺序：**
1. 随机延迟（如果启用）
2. 点赞操作（按概率执行）
3. 关注操作（按概率执行）
4. 评论操作（按概率执行）
5. 滑动到下一个内容

**概率判断机制：**
```kotlin
// 随机点赞
if (config.enableLikeOperation && Random.nextInt(100) < config.likeChance) {
    performLike()
    gestureHelper.humanLikeDelay(500L, 300L)
}
```

---

## 三、点赞操作详细流程

### 3.1 点赞操作入口

**主接口：** `AIAccessibilityService.performLike(): Boolean`

**内部调用：** `AIAccessibilityService.performNormalLike(): Boolean`

### 3.2 点赞执行流程

#### 第一步：页面元素检测
**接口：** `AIAccessibilityService.detectPageElements(useCache: Boolean = true)`

**实现细节：**
```kotlin
val rootNode = rootInActiveWindow  // 获取当前页面根节点
return elementDetector.detectAllElements(rootNode, useCache)
```

**调用链：** 
`detectPageElements()` → `ElementDetector.detectAllElements()` → `ElementDetector.traverseNode()`

#### 第二步：点赞按钮识别
**接口：** `ElementDetector.findBestLikeButton(elements: List<ElementInfo>)`

**识别依据：**
1. **文本匹配：** 包含"点赞"或"like"关键词
2. **描述匹配：** contentDescription包含相关词汇
3. **View ID匹配：** viewId包含"like"字符串
4. **元素类型：** 被标识为`ElementType.LIKE_BUTTON`
5. **可点击性：** 必须具有`isClickable = true`属性

**重要性评分算法：**
```kotlin
fun calculateImportance(elementType: ElementType, node: AccessibilityNodeInfo, text: String?, desc: String?): Int {
    var score = 0
    
    // 基础类型分数
    score += when (elementType) {
        ElementType.LIKE_BUTTON -> 90
        ElementType.FOLLOW_BUTTON -> 85
        ElementType.COMMENT_BUTTON -> 80
        // ...其他类型
    }
    
    // 可点击性加分
    if (node.isClickable) score += 20
    if (node.isLongClickable) score += 10
    
    // 文本内容加分
    if (!text.isNullOrBlank() && text.length > 1) score += 15
    if (!desc.isNullOrBlank() && desc.length > 1) score += 10
    
    // 位置和大小因素
    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    val width = bounds.width()
    val height = bounds.height()
    
    // 合理大小的元素加分
    if (width in 50..500 && height in 30..200) score += 10
    
    return score.coerceIn(0, 100)
}
```

**选择算法：**
```kotlin
return elements
    .filter { it.elementType == ElementInfo.ElementType.LIKE_BUTTON && it.isClickable }
    .maxByOrNull { it.importance }  // 选择重要性评分最高的
```

#### 第三步：执行点击操作
**接口：** `GestureHelper.clickElement(element: ElementInfo)`

**调用链：** 
`clickElement()` → `GestureHelper.click(x, y)` → `AccessibilityService.dispatchGesture()`

**手势实现：**
```kotlin
fun click(x: Int, y: Int, duration: Long = CLICK_DELAY): Boolean {
    val path = Path().apply {
        moveTo(x.toFloat(), y.toFloat())
    }
    
    val gestureBuilder = GestureDescription.Builder()
    val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
    gestureBuilder.addStroke(strokeDescription)
    
    return accessibilityService.dispatchGesture(gestureBuilder.build(), callback, null)
}
```

#### 第四步：统计更新
**接口：** `AIAccessibilityService.updateLikeStats(success: Boolean)`

**更新流程：**
```kotlin
if (success) {
    taskStats?.updateStats(likes = 1)  // 更新内部统计
    taskStats?.let { StatusFloatingService.updateStats(it) }  // 更新浮动窗口显示
}
```

### 3.3 点赞操作日志记录

**成功日志：**
```
[I] AIAccessibilityService: 👍 执行点赞操作
[I] GestureHelper: 👆 执行点击操作: (540, 960)
[I] GestureHelper: ✅ 点击操作完成: (540, 960)
[I] AIAccessibilityService: ✅ 点赞执行成功，操作计数+1
```

**失败日志：**
```
[W] AIAccessibilityService: ⚠️ 未找到点赞按钮
[W] ElementDetector: ❌ 无法获取页面根节点
```

---

## 四、关注操作详细流程

### 4.1 关注操作入口

**主接口：** `AIAccessibilityService.performFollow(): Boolean`

### 4.2 关注执行流程

#### 第一步：页面元素检测
**复用点赞流程的元素检测逻辑**

#### 第二步：关注按钮识别
**接口：** `ElementDetector.findBestFollowButton(elements: List<ElementInfo>)`

**识别依据：**
1. **文本匹配：** 包含"关注"或"follow"关键词
2. **描述匹配：** contentDescription包含相关词汇
3. **View ID匹配：** viewId包含"follow"字符串
4. **元素类型：** 被标识为`ElementType.FOLLOW_BUTTON`
5. **可点击性：** 必须具有`isClickable = true`属性

**重要性评分：** `ElementType.FOLLOW_BUTTON -> 85分`

#### 第三步：执行点击操作
**复用点赞流程的点击逻辑**

#### 第四步：统计更新
```kotlin
if (success) {
    taskStats?.updateStats(follows = 1)
    taskStats?.let { StatusFloatingService.updateStats(it) }
}
```

### 4.3 关注操作日志记录

**成功日志：**
```
[I] AIAccessibilityService: ➕ 执行关注操作
[I] GestureHelper: 👆 执行点击操作: (720, 1200)
[I] GestureHelper: ✅ 点击操作完成: (720, 1200)
```

---

## 五、评论操作详细流程

### 5.1 评论操作入口

**主接口：** `AIAccessibilityService.performComment(): Boolean`

### 5.2 评论执行流程

#### 第一步：页面元素检测
**复用点赞流程的元素检测逻辑**

#### 第二步：评论按钮识别
**接口：** `ElementDetector.findBestCommentButton(elements: List<ElementInfo>)`

**识别依据：**
1. **文本匹配：** 包含"评论"或"comment"关键词
2. **描述匹配：** contentDescription包含相关词汇
3. **View ID匹配：** viewId包含"comment"字符串
4. **元素类型：** 被标识为`ElementType.COMMENT_BUTTON`
5. **可点击性：** 必须具有`isClickable = true`属性

**重要性评分：** `ElementType.COMMENT_BUTTON -> 80分`

#### 第三步：执行点击操作
**复用点赞流程的点击逻辑**

#### 第四步：统计更新
```kotlin
if (success) {
    taskStats?.updateStats(comments = 1)
    taskStats?.let { StatusFloatingService.updateStats(it) }
}
```

### 5.3 评论操作日志记录

**成功日志：**
```
[I] AIAccessibilityService: 💬 执行评论操作
[I] GestureHelper: 👆 执行点击操作: (480, 1100)
[I] GestureHelper: ✅ 点击操作完成: (480, 1100)
```

---

## 六、操作延迟与人性化机制

### 6.1 延迟策略

**接口：** `GestureHelper.humanLikeDelay(baseDelay: Long, randomRange: Long)`

**延迟时间：**
- **点赞操作：** 500ms ± 300ms随机
- **关注操作：** 800ms ± 400ms随机  
- **评论操作：** 1000ms ± 500ms随机

**实现逻辑：**
```kotlin
fun humanLikeDelay(baseDelay: Long, randomRange: Long) {
    val actualDelay = baseDelay + Random.nextLong(0, randomRange)
    Thread.sleep(actualDelay)
}
```

### 6.2 随机化机制

**随机延迟：**
```kotlin
if (config.enableRandomDelay) {
    val delay = Random.nextLong(0, config.randomDelayRange)
    Thread.sleep(delay)
}
```

**概率执行：**
```kotlin
if (Random.nextInt(100) < config.likeChance) {
    // 执行点赞操作
}
```

---

## 七、错误处理与容错机制

### 7.1 常见错误场景

1. **页面元素检测失败**
   - 原因：页面未完全加载
   - 处理：返回空列表，跳过本次操作

2. **按钮识别失败**
   - 原因：页面布局变化，关键词不匹配
   - 处理：记录警告日志，操作失败

3. **手势执行失败**
   - 原因：系统权限不足，坐标无效
   - 处理：记录错误日志，统计不更新

### 7.2 容错策略

**统计更新安全机制：**
```kotlin
try {
    taskStats?.updateStats(likes = 1)
    taskStats?.let { StatusFloatingService.updateStats(it) }
} catch (e: Exception) {
    LogCollector.addLog("W", TAG, "⚠️ 更新统计失败，但不影响主功能: ${e.message}")
}
```

**元素检测缓存机制：**
- 缓存有效期：2秒
- 缓存失效时重新检测
- 避免重复检测提高性能

---

## 八、性能优化策略

### 8.1 元素检测优化

**缓存机制：**
```kotlin
private val elementCache = ConcurrentHashMap<String, List<ElementInfo>>()
private val cacheValidDuration = 2000L  // 2秒缓存
```

**有效元素筛选：**
```kotlin
private fun isUsefulElement(element: ElementInfo): Boolean {
    // 基本条件：必须有边界且可见
    if (element.bounds.isEmpty || element.bounds.width() <= 0 || element.bounds.height() <= 0) {
        return false
    }
    
    // 太小的元素通常无意义
    if (element.bounds.width() < 20 || element.bounds.height() < 20) {
        return false
    }
    
    // 其他筛选条件...
}
```

### 8.2 操作频率控制

**间隔检查：**
```kotlin
if (currentTime - lastOperationTime < config.swipeInterval) {
    return  // 跳过本次操作
}
```

**最大次数限制：**
```kotlin
if (operationCount >= config.maxOperations) {
    stopCurrentTask()  // 任务完成
    return
}
```

---

## 九、数据统计与状态更新

### 9.1 统计数据结构

**TaskStats模型：**
```kotlin
data class TaskStats(
    var likeCount: Int = 0,      // 点赞次数
    var followCount: Int = 0,    // 关注次数
    var commentCount: Int = 0,   // 评论次数
    var swipeCount: Int = 0,     // 滑动次数
    var totalOperations: Int = 0, // 总操作次数
    val startTime: Long = System.currentTimeMillis()  // 开始时间
)
```

### 9.2 状态更新流程

**更新触发点：**
1. 每次成功执行操作后
2. StatusFloatingService定时更新（1秒/3秒频率）

**更新调用链：**
`操作成功` → `updateXXXStats()` → `TaskStats.updateStats()` → `StatusFloatingService.updateStats()`

---

## 十、调试与日志

### 10.1 日志级别

- **I (Info)：** 正常操作流程
- **W (Warning)：** 操作失败但不影响整体
- **E (Error)：** 严重错误，可能影响功能
- **D (Debug)：** 调试信息，详细执行过程

### 10.2 关键日志示例

**任务启动：**
```
[I] AIAccessibilityService: 🚀 启动自动化任务: com.ss.android.ugc.aweme
[I] AIAccessibilityService: 📋 任务配置: 点赞60% 关注10% 评论5%
```

**操作执行：**
```
[I] AIAccessibilityService: 🔍 开始检测页面元素...
[I] ElementDetector: 📊 检测统计 - 总数:156 重要:23 可操作:18
[I] AIAccessibilityService: 👍 执行点赞操作
[I] GestureHelper: 👆 执行点击操作: (540, 960)
[I] GestureHelper: ✅ 点击操作完成: (540, 960)
```

**任务完成：**
```
[I] AIAccessibilityService: ✅ 普通模式已完成设定的操作次数 (50/50)，任务结束
[I] AIAccessibilityService: 📊 任务统计 - 点赞:30 关注:5 评论:2 滑动:50
```

---

## 总结

AIPhone的普通模式操作基于智能元素检测和概率执行机制，通过无障碍服务获取页面信息，使用机器学习般的重要性评分算法识别目标按钮，最终通过手势模拟实现自动化操作。整个流程具有良好的容错性、人性化延迟和性能优化，确保操作的稳定性和自然性。 