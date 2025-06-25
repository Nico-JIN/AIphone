# AIPhone - Android自动化助手

## 项目简介

AIPhone是一个基于Android无障碍服务的自动化助手，专门用于手机应用自动化操作。支持智能点赞、评论、滑动浏览等功能，提供简洁的UI界面和强大的元素检测能力。

## 核心功能

### 1. 无障碍服务自动化
- **智能元素检测**: 自动识别页面中的可操作元素
- **手势模拟**: 支持滑动、点击、长按等手势操作  
- **批量操作**: 可配置周期性任务，实现批量自动化
- **多应用支持**: 针对抖音、微信等主流应用优化

### 2. 任务配置系统
- **个性化设置**: 为每个应用配置专属任务参数
- **时间控制**: 设置滑动周期、频率、停留时间
- **智能暂停**: 支持任务暂停和恢复功能

### 3. 元素检测器  
- **实时检测**: 获取当前页面所有可操作元素
- **元素搜索**: 支持关键词搜索特定元素
- **坐标定位**: 精确获取元素位置信息
- **调试模式**: 类似开发者工具的元素检查器

### 4. 图像识别 (预留)
- **截图功能**: 自动截取当前屏幕
- **OpenCV集成**: 基于图像识别的精确定位
- **坐标点击**: 支持图像识别后的坐标点击

## 技术架构

```
├── service/
│   ├── AccessibilityService.kt     # 核心无障碍服务
│   ├── FloatingWindowService.kt    # 悬浮窗服务  
│   └── TaskConfigService.kt        # 任务配置服务
├── ui/
│   ├── MainActivity.kt             # 主界面
│   ├── TaskConfigActivity.kt       # 任务配置界面
│   └── ElementDetectorActivity.kt  # 元素检测界面
├── utils/
│   ├── ElementDetector.kt          # 元素检测工具
│   ├── GestureHelper.kt           # 手势操作助手
│   ├── ImageRecognition.kt        # 图像识别工具 (预留)
│   └── LogCollector.kt            # 日志收集器
└── model/
    ├── TaskConfig.kt              # 任务配置模型
    ├── ElementInfo.kt             # 元素信息模型
    └── AppInfo.kt                 # 应用信息模型
```

## 快速开始

### 1. 权限配置
- 开启无障碍服务权限
- 授予悬浮窗显示权限
- 确保应用可在后台运行

### 2. 使用流程
1. 选择目标应用 (如抖音)
2. 配置任务参数 (滑动频率、停留时间等)
3. 启动自动化服务
4. 使用元素检测器调试和优化

### 3. 核心接口

#### 无障碍服务接口
```kotlin
// 启动自动化任务
fun startAutomationTask(packageName: String, config: TaskConfig)

// 停止当前任务  
fun stopCurrentTask()

// 检测页面元素
fun detectPageElements(): List<ElementInfo>

// 执行点击操作
fun performClick(x: Int, y: Int): Boolean

// 执行手势滑动
fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int): Boolean
```

#### 元素检测接口
```kotlin
// 获取所有可操作元素
fun getAllClickableElements(): List<ElementInfo>

// 搜索特定元素  
fun searchElements(keyword: String): List<ElementInfo>

// 获取元素详细信息
fun getElementDetails(element: AccessibilityNodeInfo): ElementInfo
```

#### 任务配置接口
```kotlin
// 保存任务配置
fun saveTaskConfig(packageName: String, config: TaskConfig)

// 加载任务配置
fun loadTaskConfig(packageName: String): TaskConfig?

// 获取默认配置
fun getDefaultConfig(): TaskConfig
```

## 支持的应用

- **支持应用**: 某微直播、某音直播、某头条自动浏览等应用。
- *其他应用持续适配中...*

## 注意事项 

1. **合规使用**: 请遵守相关平台的使用规范
2. **权限管理**: 确保已授予必要的系统权限
3. **性能优化**: 避免过于频繁的操作影响设备性能
4. **隐私保护**: 本应用不收集用户隐私数据

## 更新日志

### v1.0.0 (开发中)
- 基础无障碍服务框架
- 元素检测和手势操作
- 悬浮窗控制界面
- 任务配置系统

---

**开发者**: 专注于Android自动化技术  
**许可证**: MIT License  
**联系方式**: 如有问题请提交Issue 
