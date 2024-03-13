package com.teslaluo.playerdemo

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.teslaluo.playerdemo.decoder.AudioDecoder
import com.teslaluo.playerdemo.decoder.VideoDecoder
import java.util.concurrent.Executors



class MainActivity : AppCompatActivity() {
    private val PERMISSION_CODE = 1001
    var permissionGranted = PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestMyPermissions()
    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkingPermissions(): Boolean {

        // if both permissions are granted, return true
        return (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == permissionGranted)

        // if one permission or both were not granted, then return false
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestMyPermissions() {
        if (checkingPermissions()) {
            //Permission were granted
            Toast.makeText(this, "Permissions Granted. Thank you!!", Toast.LENGTH_SHORT).show()
            initPlayer()
        } else {

            // Request the permissions
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_VIDEO)) {
                // show a dialog
                AlertDialog.Builder(this)
                    .setMessage("This app needs these permissions..")
                    .setCancelable(true)
                    .setPositiveButton("Ok") { dialogInterface, i -> requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.CAMERA), PERMISSION_CODE) }
                    .show()
            } else {

                // I show this request again and again
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_VIDEO), PERMISSION_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Checking our PERMISSION_CODE
        when (requestCode) {
            PERMISSION_CODE -> if (grantResults.isNotEmpty() && grantResults[0] == permissionGranted) {
                // Permission granted
                Toast.makeText(this, "Permissions Granted. Thank you!!", Toast.LENGTH_SHORT).show()
                initPlayer()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_VIDEO)) {
                    // Permission is not granted (Permanently)
                    AlertDialog.Builder(this)
                        .setMessage("You have denied permanently these permissions, please go to setting to enable these permissions.")
                        .setCancelable(true)
                        .setPositiveButton("Go to Settings") { _, i -> goToApplicationSettings() }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    private fun goToApplicationSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun initPlayer() {

        // TODO: Add gallery explore here
        // Get the path of your local video file here
        val path = "/sdcard/DCIM/Camera/20240312_192641.mp4"
        // Create the thread pool
        val threadPool = Executors.newFixedThreadPool(2)

        val textureView: TextureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                // Surface is available, initialize MediaCodec here
                val videoDecoder = VideoDecoder(path, null, Surface(surface))
                threadPool.execute(videoDecoder)

//                val audioDecoder = AudioDecoder(path)
//                threadPool.execute(audioDecoder)

                videoDecoder.goOn()
                //audioDecoder.goOn()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                // Handle size changes if necessary
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                // Invoked every time the TextureView is updated
            }
        }
    }
}