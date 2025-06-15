package wh.rj.aiphone.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object LogCollector {
    private const val MAX_LOGS = 1000
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )
    
    fun addLog(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, level, tag, message)
        
        logs.offer(entry)
        
        // 限制日志数量
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }
        
        // 同时输出到系统日志
        when (level) {
            "D" -> Log.d(tag, message)
            "I" -> Log.i(tag, message)
            "W" -> Log.w(tag, message)
            "E" -> Log.e(tag, message)
            else -> Log.v(tag, message)
        }
    }
    
    fun getAllLogs(): List<LogEntry> {
        return logs.toList()
    }
    
    fun getRecentLogs(count: Int): List<LogEntry> {
        val allLogs = logs.toList()
        return if (allLogs.size <= count) {
            allLogs
        } else {
            allLogs.takeLast(count)
        }
    }
    
    fun clearLogs() {
        logs.clear()
    }
    
    fun getLogCount(): Int {
        return logs.size
    }
    
    fun isDebugMode(): Boolean {
        return true // 可以根据实际需要配置
    }
} 