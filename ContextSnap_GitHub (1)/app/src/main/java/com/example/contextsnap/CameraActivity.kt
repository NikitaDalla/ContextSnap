package com.example.contextsnap

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

// Takes STILL photos only, by opening the phone's own camera app.
// Back-camera and front-camera photos are saved as separate records (camera_id).
class CameraActivity : AppCompatActivity() {

    private lateinit var thumb: ImageView
    private lateinit var labelText: TextView
    private var pendingCamera = "back"
    private val bg = Executors.newSingleThreadExecutor()

    @Suppress("DEPRECATION")
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val bitmap = result.data?.extras?.get("data") as? Bitmap
        if (bitmap == null) {
            Toast.makeText(this, "No photo returned by the camera app", Toast.LENGTH_LONG).show()
        } else {
            handlePhoto(bitmap, pendingCamera)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        pendingCamera = savedInstanceState?.getString("cam") ?: "back"
        thumb = findViewById(R.id.imagePhoto)
        labelText = findViewById(R.id.textLabel)

        findViewById<Button>(R.id.buttonBack).setOnClickListener { takePhoto("back") }
        findViewById<Button>(R.id.buttonFront).setOnClickListener { takePhoto("front") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("cam", pendingCamera)
    }

    private fun takePhoto(cameraId: String) {
        pendingCamera = cameraId
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Best-effort hint for which camera to open (some camera apps ignore it).
        if (cameraId == "front") {
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
            intent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        } else {
            intent.putExtra("android.intent.extras.CAMERA_FACING", 0)
        }
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No camera app found on this phone", Toast.LENGTH_LONG).show()
        }
    }

    private fun handlePhoto(bitmap: Bitmap, cameraId: String) {
        thumb.setImageBitmap(bitmap)
        labelText.text = "Classifying..."
        val userId = SessionStore.userId(this)
        val sessionId = if (AppState.activeSessionId == -1L) null else AppState.activeSessionId
        val app = applicationContext

        bg.execute {
            val file = File(filesDir, "IMG_${cameraId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

            val db = Db.get(app)
            val imageId = db.insertImage(userId, sessionId, cameraId, file.absolutePath)

            val result: Pair<String, Float?> = try {
                Labeler.classify(app, bitmap)
            } catch (e: Throwable) {
                Pair("unclassified", null)
            }
            db.updateImageLabel(imageId, result.first, result.second)

            runOnUiThread {
                val conf = result.second
                labelText.text = if (conf != null)
                    "$cameraId camera: ${result.first} (${(conf * 100).toInt()}%)"
                else
                    "$cameraId camera: ${result.first}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bg.shutdown()
    }
}
