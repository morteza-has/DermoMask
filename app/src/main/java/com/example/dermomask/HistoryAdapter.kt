package com.example.dermomask

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(private val historyList: List<AnalysisResult>) : 
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val conditionText: TextView = itemView.findViewById(R.id.tv_condition)
        val confidenceText: TextView = itemView.findViewById(R.id.tv_confidence)
        val dateText: TextView = itemView.findViewById(R.id.tv_date)
        val imageView: ImageView = itemView.findViewById(R.id.iv_analysis)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val result = historyList[position]
        
        holder.conditionText.text = result.condition
        holder.confidenceText.text = "Confidence: ${String.format("%.1f", result.confidence)}%"
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        holder.dateText.text = dateFormat.format(Date(result.timestamp))
        
        // Load image using Glide - removed placeholder to fix build error
        Glide.with(holder.itemView.context)
            .load(result.imagePath)
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = historyList.size
}
