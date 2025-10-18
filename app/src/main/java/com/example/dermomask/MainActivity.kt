package com.example.dermomask

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize buttons
        val cameraButton: Button = findViewById(R.id.camera_button)
        val galleryButton: Button = findViewById(R.id.gallery_button)
        val infoButton: Button = findViewById(R.id.info_button)
        val historyButton: Button = findViewById(R.id.history_button)
        val settingsButton: Button = findViewById(R.id.settings_button)
        val helpButton: Button = findViewById(R.id.help_button)
        
        // Set click listeners
        cameraButton.setOnClickListener {
            openCamera()
        }
        
        galleryButton.setOnClickListener {
            openGallery()
        }
        
        infoButton.setOnClickListener {
            openInfo()
        }
        
        historyButton.setOnClickListener {
            openHistory()
        }
        
        settingsButton.setOnClickListener {
            openSettings()
        }
        
        helpButton.setOnClickListener {
            openHelp()
        }
    }
    
    private fun openCamera() {
        val cameraFragment = CameraFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, cameraFragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun openGallery() {
        // TODO: Implement gallery functionality
        val intent = Intent(this, ResultActivity::class.java)
        startActivity(intent)
    }
    
    private fun openInfo() {
        // TODO: Implement info functionality
    }
    
    private fun openHistory() {
        // TODO: Implement history functionality
    }
    
    private fun openSettings() {
        // TODO: Implement settings functionality
    }
    
    private fun openHelp() {
        // TODO: Implement help functionality
    }
}
