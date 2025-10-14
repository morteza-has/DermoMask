package com.example.dermomask

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraPreview: androidx.camera.view.PreviewView
    private lateinit var captureButton: Button
    private lateinit var resultText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var progressBar: android.widget.ProgressBar
    
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private val labels = listOf(
        "acne",
        "dark circle under eye", 
        "Dermo Mask-Black Head",
        "sun spot and damage",
        "Normal"
    )
    
    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupCamera()
        
        captureButton.setOnClickListener {
            takePhoto()
        }
    }
    
    private fun initViews() {
        cameraPreview = findViewById(R.id.camera_preview)
        captureButton = findViewById(R.id.capture_button)
        resultText = findViewById(R.id.result_text)
        confidenceText = findViewById(R.id.confidence_text)
        progressBar = findViewById(R.id.progress_bar)
    }
    
    private fun setupCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder().build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera failed to start: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        progressBar.visibility = android.view.View.VISIBLE
        captureButton.isEnabled = false
        
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                
                val bitmap = image.toBitmap()
                analyzeImage(bitmap)
                
                image.close()
                progressBar.visibility = android.view.View.GONE
                captureButton.isEnabled = true
            }
            
            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = android.view.View.GONE
                captureButton.isEnabled = true
            }
        })
    }
    
    private fun analyzeImage(bitmap: Bitmap) {
        try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            
            val model = ImageClassifier.createFromFile(this, "model_unquant.tflite")
            
            val image = org.tensorflow.lite.support.image.TensorImage.fromBitmap(resizedBitmap)
            val results = model.classify(image)
            
            displayResults(results)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun displayResults(results: List<org.tensorflow.lite.task.vision.classifier.Classifications>) {
        if (results.isNotEmpty()) {
            val topResult = results[0].categories[0]
            val labelIndex = topResult.label.toIntOrNull() ?: 0
            val confidence = topResult.score * 100
            
            val conditionName = if (labelIndex < labels.size) labels[labelIndex] else "Unknown"
            
            runOnUiThread {
                resultText.text = "Detected: $conditionName"
                confidenceText.text = "Confidence: ${"%.2f".format(confidence)}%"
                
                when {
                    confidence > 80 -> resultText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    confidence > 60 -> resultText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    else -> resultText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}