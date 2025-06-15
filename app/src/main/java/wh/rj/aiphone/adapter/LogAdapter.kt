package wh.rj.aiphone.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import wh.rj.aiphone.R
import wh.rj.aiphone.utils.LogCollector

class LogAdapter(private var logs: List<LogCollector.LogEntry>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val tvLevel: TextView = itemView.findViewById(R.id.tvLevel)
        val tvTag: TextView = itemView.findViewById(R.id.tvTag)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        
        holder.tvTimestamp.text = log.timestamp
        holder.tvLevel.text = log.level
        holder.tvTag.text = log.tag
        holder.tvMessage.text = log.message
        
        // 根据日志级别设置颜色
        val levelColor = when (log.level) {
            "D" -> Color.parseColor("#808080") // 灰色 - Debug
            "I" -> Color.parseColor("#0066CC") // 蓝色 - Info
            "W" -> Color.parseColor("#FF8800") // 橙色 - Warning
            "E" -> Color.parseColor("#CC0000") // 红色 - Error
            else -> Color.parseColor("#000000") // 黑色 - 其他
        }
        
        holder.tvLevel.setTextColor(levelColor)
        
        // 如果是Toast相关的日志，高亮显示
        if (log.message.contains("Toast") || log.message.contains("弹窗")) {
            holder.itemView.setBackgroundColor(Color.parseColor("#FFFFCC"))
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun getItemCount(): Int = logs.size

    fun updateLogs(newLogs: List<LogCollector.LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
} 