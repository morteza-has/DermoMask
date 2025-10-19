package com.example.dermomask

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // Find views
        val resultImageView: ImageView = findViewById(R.id.result_image)
        val conditionTextView: TextView = findViewById(R.id.result_condition_text)
        val confidenceTextView: TextView = findViewById(R.id.result_confidence_text)
        val titleTextView: TextView = findViewById(R.id.result_title_text)
        val backButton: Button = findViewById(R.id.btn_back_home)

        // Get data from Intent
        val conditionName = intent.getStringExtra("CONDITION_NAME") ?: "Unknown"
        val confidence = intent.getFloatExtra("CONFIDENCE", 0f)
        val imageUriString = intent.getStringExtra("IMAGE_URI")

        // Display data
        conditionTextView.text = "Detected: $conditionName"
        confidenceTextView.text = "Confidence: ${"%.2f".format(confidence)}%"
        titleTextView.text = "Analysis Result"

        // Display the image from the URI
        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            resultImageView.setImageURI(imageUri)
            
            // Save to history after displaying the result
            saveToHistory(conditionName, confidence, imageUriString)
        }

        // Set text color based on confidence
        val color = when {
            confidence > 80 -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            confidence > 60 -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            else -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
        }
        conditionTextView.setTextColor(color)
        confidenceTextView.setTextColor(color)

        // Back to home button
        backButton.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun saveToHistory(condition: String, confidence: Float, imageUri: String) {
        val result = AnalysisResult(condition, confidence, imageUri)
        
        val sharedPreferences = getSharedPreferences("DermoMask", MODE_PRIVATE)
        val historyJson = sharedPreferences.getString("analysis_history", "[]")
        
        val type = object : TypeToken<MutableList<AnalysisResult>>() {}.type
        val historyList = Gson().fromJson<MutableList<AnalysisResult>>(historyJson, type) ?: mutableListOf()
        
        historyList.add(0, result) // Add to beginning (most recent first)
        
        // Keep only last 20 results to prevent storage issues
        if (historyList.size > 20) {
            historyList.removeAt(historyList.size - 1)
        }
        
        val updatedJson = Gson().toJson(historyList)
        sharedPreferences.edit().putString("analysis_history", updatedJson).apply()
    }

    // Override back button to go to home
    override fun onBackPressed() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
