package wh.rj.aiphone.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 定位记录数据模型
 * 用于存储应用程序的定位操作信息
 */
data class LocationRecord(
    val packageName: String,
    val appName: String,
    val type: String, // "直播" 或 "普通"
    val operations: MutableMap<String, LocationOperation> = mutableMapOf(),
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    
    /**
     * 定位操作信息
     */
    data class LocationOperation(
        val operationType: String, // "点赞", "评论", "关注", "转发"
        val x: Int,
        val y: Int,
        val recordTime: Long = System.currentTimeMillis()
    )
    
    companion object {
        private const val PREFS_NAME = "location_records"
        private const val KEY_RECORDS = "records"
        private val gson = Gson()
        
        /**
         * 保存定位记录
         */
        fun saveLocationRecord(context: Context, record: LocationRecord) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val records = getAllLocationRecords(context).toMutableMap()
                
                // 每个应用只保留最新的一次定位记录
                records[record.packageName] = record
                
                val json = gson.toJson(records)
                prefs.edit().putString(KEY_RECORDS, json).apply()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        /**
         * 获取所有定位记录
         */
        fun getAllLocationRecords(context: Context): Map<String, LocationRecord> {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val json = prefs.getString(KEY_RECORDS, null) ?: return emptyMap()
                
                val type = object : TypeToken<Map<String, LocationRecord>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
                
            } catch (e: Exception) {
                e.printStackTrace()
                emptyMap()
            }
        }
        
        /**
         * 获取指定应用的定位记录
         */
        fun getLocationRecord(context: Context, packageName: String): LocationRecord? {
            return getAllLocationRecords(context)[packageName]
        }
        
        /**
         * 删除定位记录
         */
        fun deleteLocationRecord(context: Context, packageName: String) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val records = getAllLocationRecords(context).toMutableMap()
                records.remove(packageName)
                
                val json = gson.toJson(records)
                prefs.edit().putString(KEY_RECORDS, json).apply()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        /**
         * 清空所有定位记录
         */
        fun clearAllLocationRecords(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 添加定位操作
     */
    fun addOperation(operationType: String, x: Int, y: Int): LocationRecord {
        operations[operationType] = LocationOperation(operationType, x, y)
        return this.copy(lastUpdateTime = System.currentTimeMillis())
    }
    
    /**
     * 获取操作摘要
     */
    fun getOperationSummary(): String {
        if (operations.isEmpty()) {
            return "暂无定位操作"
        }
        
        return operations.values.joinToString("、") { operation ->
            "${operation.operationType}(${operation.x}, ${operation.y})"
        }
    }
    
    /**
     * 获取格式化的时间
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(lastUpdateTime))
    }
} 