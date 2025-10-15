package com.example.dermomask

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        }

        // Set text color based on confidence
        val color = when {
            confidence > 80 -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            confidence > 60 -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            else -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
        }
        conditionTextView.setTextColor(color)
        confidenceTextView.setTextColor(color)
    }
}
