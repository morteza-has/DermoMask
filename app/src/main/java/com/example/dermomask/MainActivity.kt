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
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraPreview: androidx.camera.view.PreviewView
    private lateinit var captureButton: Button
    private lateinit var switchCameraButton: Button
    private lateinit var resultText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var progressBar: android.widget.ProgressBar
    
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null
    private var tflite: Interpreter? = null
    
    private val labels = listOf(
        "acne",
        "dark circle under eye", 
        "Dermo Mask-Black Head",
        "sun spot and damage",
        "Normal"
    )
    
    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 100
        private const val MODEL_INPUT_SIZE = 224 // Common size for mobile models, adjust if your model is different
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupTensorFlowLite()
        setupCamera()
        
        captureButton.setOnClickListener {
            takePhoto()
        }
        
        switchCameraButton.setOnClickListener {
            switchCamera()
        }
    }
    
    private fun initViews() {
        cameraPreview = findViewById(R.id.camera_preview)
        captureButton = findViewById(R.id.capture_button)
        switchCameraButton = findViewById(R.id.switch_camera_button)
        resultText = findViewById(R.id.result_text)
        confidenceText = findViewById(R.id.confidence_text)
        progressBar = findViewById(R.id.progress_bar)
    }
    
    private fun setupTensorFlowLite() {
        try {
            // Load the TensorFlow Lite model
            val model = FileUtil.loadMappedFile(this, "model_unquant.tflite")
            tflite = Interpreter(model)
            Toast.makeText(this, "Model loaded successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        // Unbind all use cases before rebinding
        cameraProvider.unbindAll()
        
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }
        
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
        } catch (exc: Exception) {
            Toast.makeText(this, "Camera failed to start: ${exc.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCameraUseCases()
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        progressBar.visibility = android.view.View.VISIBLE
        captureButton.isEnabled = false
        switchCameraButton.isEnabled = false
        
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                
                val bitmap = image.toBitmap()
                analyzeImage(bitmap)
                
                image.close()
            }
            
            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = android.view.View.GONE
                    captureButton.isEnabled = true
                    switchCameraButton.isEnabled = true
                }
            }
        })
    }
    
    private fun analyzeImage(bitmap: Bitmap) {
        try {
            // Preprocess the image
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
            
            if (tflite != null) {
                // Run real model inference
                runModelInference(resizedBitmap)
            } else {
                // Fallback to simulation if model isn't loaded
                runSimulatedAnalysis()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
                progressBar.visibility = android.view.View.GONE
                captureButton.isEnabled = true
                switchCameraButton.isEnabled = true
            }
        }
    }
    
    private fun runModelInference(bitmap: Bitmap) {
        Thread {
            try {
                // Convert bitmap to TensorImage
                var image = TensorImage(DataType.FLOAT32)
                image.load(bitmap)
                
                // Create image processor (adjust based on your model's requirements)
                val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeOp(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                    // Add more preprocessing steps if your model requires them
                    // .add(NormalizeOp(0f, 255f)) // Example normalization
                    .build()
                
                image = imageProcessor.process(image)
                
                // Prepare input and output buffers
                val input = arrayOf(image.tensorBuffer.buffer)
                val output = Array(1) { FloatArray(labels.size) } // Assuming your model outputs probabilities for each class
                
                // Run inference
                tflite?.runForMultipleInputsOutputs(input, mapOf(0 to output[0]))
                
                // Process results
                val probabilities = output[0]
                val (maxIndex, maxConfidence) = findMaxProbability(probabilities)
                
                runOnUiThread {
                    displayResults(labels[maxIndex], maxConfidence * 100)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Model inference error: ${e.message}", Toast.LENGTH_LONG).show()
                    // Fallback to simulation
                    runSimulatedAnalysis()
                }
            }
        }.start()
    }
    
    private fun findMaxProbability(probabilities: FloatArray): Pair<Int, Float> {
        var maxIndex = 0
        var maxProbability = probabilities[0]
        
        for (i in 1 until probabilities.size) {
            if (probabilities[i] > maxProbability) {
                maxProbability = probabilities[i]
                maxIndex = i
            }
        }
        
        return Pair(maxIndex, maxProbability)
    }
    
    private fun runSimulatedAnalysis() {
        Thread {
            try {
                // Simulate processing time
                Thread.sleep(2000)
                
                // Simulate a classification result
                val randomIndex = (0 until labels.size).random()
                val confidence = (70..95).random().toFloat()
                val conditionName = labels[randomIndex]
                
                runOnUiThread {
                    displayResults(conditionName, confidence, isSimulated = true)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Analysis error", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = android.view.View.GONE
                    captureButton.isEnabled = true
                    switchCameraButton.isEnabled = true
                }
            }
        }.start()
    }
    
    private fun displayResults(conditionName: String, confidence: Float, isSimulated: Boolean = false) {
        val resultMessage = if (isSimulated) {
            "Simulated: $conditionName"
        } else {
            "Detected: $conditionName"
        }
        
        resultText.text = resultMessage
        confidenceText.text = "Confidence: ${"%.2f".format(confidence)}%"
        
        // Color code based on confidence
        when {
            confidence > 80 -> {
                resultText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                confidenceText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            confidence > 60 -> {
                resultText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                confidenceText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }
            else -> {
                resultText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                confidenceText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        }
        
        progressBar.visibility = android.view.View.GONE
        captureButton.isEnabled = true
        switchCameraButton.isEnabled = true
        
        val toastMessage = if (isSimulated) {
            "Simulated analysis complete!"
        } else {
            "Model analysis complete!"
        }
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
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
        tflite?.close()
        cameraExecutor.shutdown()
    }
}
