package your.package.name

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvExplanation: TextView
    private lateinit var btnOpenCamera: Button
    private lateinit var btnChooseGallery: Button
    private lateinit var cameraContainer: FrameLayout
    private lateinit var cameraPreviewHolder: FrameLayout
    private lateinit var btnCloseCamera: ImageButton

    // Request code fallbacks (not used if Activity Result APIs below are preferred)
    private val CAMERA_PERMISSION = Manifest.permission.CAMERA

    // Gallery chooser using Activity Result API
    private val pickImageFromGallery = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageFromUri(it) }
    }

    // Permission launcher for camera
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCameraPreview()
        } else {
            // Permission denied: keep showing explanation and buttons
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvExplanation = findViewById(R.id.tvExplanation)
        btnOpenCamera = findViewById(R.id.btnOpenCamera)
        btnChooseGallery = findViewById(R.id.btnChooseGallery)
        cameraContainer = findViewById(R.id.cameraContainer)
        cameraPreviewHolder = findViewById(R.id.cameraPreviewHolder)
        btnCloseCamera = findViewById(R.id.btnCloseCamera)

        btnOpenCamera.setOnClickListener {
            when (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)) {
                PackageManager.PERMISSION_GRANTED -> openCameraPreview()
                else -> requestCameraPermission.launch(CAMERA_PERMISSION)
            }
        }

        btnChooseGallery.setOnClickListener {
            // mime type image/*
            pickImageFromGallery.launch("image/*")
        }

        btnCloseCamera.setOnClickListener {
            closeCameraPreview()
        }
    }

    private fun openCameraPreview() {
        // Show camera UI
        cameraContainer.visibility = View.VISIBLE
        tvExplanation.visibility = View.GONE
        btnOpenCamera.visibility = View.GONE
        btnChooseGallery.visibility = View.GONE

        // Replace/insert your camera UI into cameraPreviewHolder.
        // If your project already uses a CameraFragment (or similar), instantiate and attach it here.
        // Example using a CameraFragment class already present in the project:
        try {
            val fragment = CameraFragment() // change class name if needed
            supportFragmentManager.beginTransaction()
                .replace(R.id.cameraPreviewHolder, fragment, "camera_fragment")
                .commitAllowingStateLoss()
        } catch (e: Exception) {
            // If CameraFragment is not available or you want CameraX directly, implement CameraX preview inflation here.
            // Minimal fallback: show a placeholder view or implement CameraX preview programmatically.
        }
    }

    private fun closeCameraPreview() {
        // Remove camera fragment or teardown CameraX here
        val frag = supportFragmentManager.findFragmentByTag("camera_fragment")
        if (frag != null) {
            supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        }
        // Clear preview holder views in case something was added programmatically
        cameraPreviewHolder.removeAllViews()

        // Restore initial UI
        cameraContainer.visibility = View.GONE
        tvExplanation.visibility = View.VISIBLE
        btnOpenCamera.visibility = View.VISIBLE
        btnChooseGallery.visibility = View.VISIBLE
    }

    private fun handleImageFromUri(uri: Uri) {
        // Integrate with your existing image processing flow (e.g., send to segmentation/classifier)
        // Example: start existing activity that handles the selected image
        val intent = Intent(this, ResultActivity::class.java)
        intent.data = uri
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (cameraContainer.visibility == View.VISIBLE) {
            closeCameraPreview()
            return
        }
        super.onBackPressed()
    }
}
