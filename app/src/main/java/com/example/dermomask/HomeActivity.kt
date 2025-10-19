package com.example.dermomask

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HomeActivity : AppCompatActivity() {
    
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private val historyList = mutableListOf<AnalysisResult>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        setupViews()
        loadHistory()
    }
    
    private fun setupViews() {
        // Set up new analysis button
        findViewById<android.widget.Button>(R.id.btn_new_analysis).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        
        // Set up history RecyclerView
        historyRecyclerView = findViewById(R.id.history_recycler_view)
        historyAdapter = HistoryAdapter(historyList)
        historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = historyAdapter
        }
        
        // Set up clear history button
        findViewById<android.widget.Button>(R.id.btn_clear_history).setOnClickListener {
            clearHistory()
        }
    }
    
    private fun loadHistory() {
        val sharedPreferences = getSharedPreferences("DermoMask", MODE_PRIVATE)
        val historyJson = sharedPreferences.getString("analysis_history", "[]")
        
        val type = object : TypeToken<List<AnalysisResult>>() {}.type
        val savedHistory = Gson().fromJson<List<AnalysisResult>>(historyJson, type) ?: emptyList()
        
        historyList.clear()
        historyList.addAll(savedHistory)
        historyAdapter.notifyDataSetChanged()
    }
    
    private fun clearHistory() {
        val sharedPreferences = getSharedPreferences("DermoMask", MODE_PRIVATE)
        sharedPreferences.edit().putString("analysis_history", "[]").apply()
        
        historyList.clear()
        historyAdapter.notifyDataSetChanged()
    }
    
    override fun onResume() {
        super.onResume()
        loadHistory() // Reload history when returning from analysis
    }
}
