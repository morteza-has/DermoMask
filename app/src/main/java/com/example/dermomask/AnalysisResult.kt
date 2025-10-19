package com.example.dermomask

import java.util.Date

data class AnalysisResult(
    val condition: String,
    val confidence: Float,
    val imagePath: String,
    val timestamp: Long = Date().time
)
