package com.example.contextsnap

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

// Takes STILL photos only, through the phone's own camera app (full size).
// Back and front photos are saved as separate records (camera_id), the picture is stored in the
// database, and MobileNet labels every photo automatically.
class CameraActivity : AppCompatActivity() {

    private lateinit var thumb: ImageView
    private lateinit var labelText: TextView
    private var pendingCamera = "back"
    private var pendingPath: String? = null
    private val bg = Executors.newSingleThreadExecutor()

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val path = pendingPath
        val file = if (path == null) null else File(path)
        if (result.resultCode != RESULT_OK || file == null || !file.exists() || file.length() == 0L) {
            Toast.makeText(this, "No photo was taken", Toast.LENGTH_SHORT).show()
        } else {
            handlePhoto(file, pendingCamera)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        pendingCamera = savedInstanceState?.getString("cam") ?: "back"
        pendingPath = savedInstanceState?.getString("path")
        thumb = findViewById(R.id.imagePhoto)
        labelText = findViewById(R.id.textLabel)

        findViewById<Button>(R.id.buttonBack).setOnClickListener { takePhoto("back") }
        findViewById<Button>(R.id.buttonFront).setOnClickListener { takePhoto("front") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("cam", pendingCamera)
        outState.putString("path", pendingPath)
    }

    private fun takePhoto(cameraId: String) {
        pendingCamera = cameraId
        val dir = File(filesDir, "photos")
        dir.mkdirs()
        val file = File(dir, "IMG_${cameraId}_${System.currentTimeMillis()}.jpg")
        pendingPath = file.absolutePath

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    private fun handlePhoto(file: File, cameraId: String) {
        labelText.text = "Processing photo..."
        val userId = SessionStore.userId(this)
        val sessionId = if (AppState.activeSessionId == -1L) null else AppState.activeSessionId
        val app = applicationContext

        bg.execute {
            val bitmap = loadScaledBitmap(file.absolutePath, 800)
            if (bitmap == null) {
                runOnUiThread { labelText.text = "Could not read the photo file" }
                return@execute
            }
            runOnUiThread {
                thumb.setImageBitmap(bitmap)
                labelText.text = "Classifying with MobileNet..."
            }

            // The picture itself goes into the database (a compressed copy), the original stays as a file.
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val bytes = stream.toByteArray()

            val db = Db.get(app)
            val imageId = db.insertImage(userId, sessionId, cameraId, file.absolutePath, bytes)

            val result: LabelResult = try {
                Labeler.classify(app, bitmap)
            } catch (e: Throwable) {
                LabelResult("unclassified", null, "Classification failed: ${e.message}")
            }
            db.updateImageLabel(imageId, result.label, result.confidence)

            runOnUiThread {
                labelText.text = "$cameraId camera - saved as image #$imageId\n${result.summary}"
            }
        }
    }

    // Loads the photo at a sensible size and turns it upright using its EXIF orientation.
    private fun loadScaledBitmap(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null

        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            orientation = ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            // no orientation info: keep the picture as it is
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return decoded
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        bg.shutdown()
    }
}
