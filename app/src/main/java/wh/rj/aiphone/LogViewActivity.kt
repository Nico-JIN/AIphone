package wh.rj.aiphone

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import wh.rj.aiphone.adapter.LogAdapter
import wh.rj.aiphone.utils.LogCollector

class LogViewActivity : AppCompatActivity() {
    
    private lateinit var tvLogCount: TextView
    private lateinit var btnClearLogs: Button
    private lateinit var btnRefresh: Button
    private lateinit var rvLogs: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var autoRefreshRunnable: Runnable? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_view)
        
        initViews()
        setupRecyclerView()
        updateLogDisplay()
        startAutoRefresh()
        
        btnClearLogs.setOnClickListener {
            LogCollector.clearLogs()
            updateLogDisplay()
        }
        
        btnRefresh.setOnClickListener {
            updateLogDisplay()
        }
    }
    
    private fun initViews() {
        tvLogCount = findViewById(R.id.tvLogCount)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        btnRefresh = findViewById(R.id.btnRefresh)
        rvLogs = findViewById(R.id.rvLogs)
    }
    
    private fun setupRecyclerView() {
        logAdapter = LogAdapter(emptyList())
        rvLogs.apply {
            layoutManager = LinearLayoutManager(this@LogViewActivity)
            adapter = logAdapter
        }
    }
    
    private fun updateLogDisplay() {
        val logs = LogCollector.getAllLogs()
        tvLogCount.text = "日志条数: ${logs.size}"
        logAdapter.updateLogs(logs)
        
        // 滚动到最新的日志
        if (logs.isNotEmpty()) {
            rvLogs.scrollToPosition(logs.size - 1)
        }
    }
    
    private fun startAutoRefresh() {
        autoRefreshRunnable = object : Runnable {
            override fun run() {
                updateLogDisplay()
                handler.postDelayed(this, 1000) // 每秒刷新一次
            }
        }
        handler.post(autoRefreshRunnable!!)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        autoRefreshRunnable?.let { handler.removeCallbacks(it) }
    }
} 