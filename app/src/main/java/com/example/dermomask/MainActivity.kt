package com.example.dermomask

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraPreview: androidx.camera.view.PreviewView
    private lateinit var captureButton: Button
    private lateinit var switchCameraButton: Button
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
        private const val MODEL_INPUT_SIZE = 224
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
        progressBar = findViewById(R.id.progress_bar)
        // Removed resultText and confidenceText as they are now in ResultActivity
    }

    private fun setupTensorFlowLite() {
        try {
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
                // IMPORTANT: Convert to bitmap *before* closing the image proxy
                val bitmap = image.toBitmap()
                image.close()
                analyzeImage(bitmap)
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
        if (tflite != null) {
            runModelInference(bitmap)
        } else {
            Toast.makeText(this, "Model is not loaded yet.", Toast.LENGTH_SHORT).show()
            progressBar.visibility = android.view.View.GONE
            captureButton.isEnabled = true
            switchCameraButton.isEnabled = true
        }
    }

    private fun runModelInference(bitmap: Bitmap) {
        Thread {
            try {
                // Preprocess the image
                var tensorImage = TensorImage(DataType.FLOAT32)
                tensorImage.load(bitmap)
                val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeOp(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                    .build()
                tensorImage = imageProcessor.process(tensorImage)

                // Define the output buffer with the correct shape: [1, 5]
                val output = Array(1) { FloatArray(labels.size) }

                // Run inference
                tflite?.run(tensorImage.tensorBuffer.buffer, output)

                // Process results
                val probabilities = output[0]
                val (maxIndex, maxConfidence) = findMaxProbability(probabilities)

                // Save the original bitmap and get its URI
                val imageUri = saveBitmapToCache(bitmap)

                runOnUiThread {
                    // Reset UI before launching new activity
                    progressBar.visibility = android.view.View.GONE
                    captureButton.isEnabled = true
                    switchCameraButton.isEnabled = true

                    if (imageUri != null) {
                        // Launch ResultActivity
                        val intent = Intent(this, ResultActivity::class.java).apply {
                            putExtra("CONDITION_NAME", labels[maxIndex])
                            putExtra("CONFIDENCE", maxConfidence * 100)
                            putExtra("IMAGE_URI", imageUri.toString())
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Failed to save image.", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Model inference error: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = android.view.View.GONE
                    captureButton.isEnabled = true
                    switchCameraButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val file = File(cacheDir, "captured_image.png")
            val fOut = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut)
            fOut.flush()
            fOut.close()
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
