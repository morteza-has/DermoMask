package com.example.dermomask

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.dermomask.databinding.ActivityMainBinding
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.MappedByteBuffer

class MainActivity : AppCompatActivity(), CameraFragment.OnImageCapturedListener {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private lateinit var binding: ActivityMainBinding
    private var tflite: Interpreter? = null
    private lateinit var tfliteModel: MappedByteBuffer
    private lateinit var imageProcessor: ImageProcessor
    private var imageSizeX: Int = 0
    private var imageSizeY: Int = 0
    private lateinit var probabilityBuffer: TensorBuffer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        // Use the binding.root for setContentView. Do NOT call setContentView twice.
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        try {
            // Initialize TFLite model
            tfliteModel = FileUtil.loadMappedFile(this, "model.tflite")
            val options = Interpreter.Options().addDelegate(GpuDelegate())
            tflite = Interpreter(tfliteModel, options)

            // Get model input and output details
            val imageTensor = tflite!!.getInputTensor(0)
            val imageShape = imageTensor.shape()
            imageSizeY = imageShape[1]
            imageSizeX = imageShape[2]
            val probabilityTensor = tflite!!.getOutputTensor(0)
            val probabilityShape = probabilityTensor.shape()
            val probabilityDataType = probabilityTensor.dataType()

            // Initialize image processor
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(imageSizeY, imageSizeX, ResizeOp.ResizeMethod.BILINEAR))
                .build()

            // Initialize probability buffer
            probabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType)

        } catch (e: IOException) {
            Toast.makeText(this, "Error loading model", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraFragment = CameraFragment().apply {
            setOnImageCapturedListener(this@MainActivity)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, cameraFragment) // This R reference is correct
            .commit()
    }

    override fun onImageCaptured(uri: Uri) {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        if (bitmap != null) {
            // Preprocess the image
            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            // Run inference
            tflite?.run(tensorImage.buffer, probabilityBuffer.buffer.rewind())

            // Get the results
            val results = probabilityBuffer.floatArray
            val acne = results[0]
            val eczema = results[1]
            val psoriasis = results[2]
            val rosacea = results[3]

            // Start ResultActivity and pass the results
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("imageUri", uri.toString())
                putExtra("acne", "%.2f".format(acne * 100))
                putExtra("eczema", "%.2f".format(eczema * 100))
                putExtra("psoriasis", "%.2f".format(psoriasis * 100))
                putExtra("rosacea", "%.2f".format(rosacea * 100))
            }
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        tflite?.close()
    }
}
