package com.example.contextsnap

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

// What the classifier tells us about one photo.
data class LabelResult(val label: String, val confidence: Float?, val summary: String)

// Deep learning part: MobileNet (a lightweight convolutional neural network) running on the phone
// through TensorFlow Lite. The model file is app/src/main/assets/mobilenet.tflite
// (the GitHub build downloads it automatically).
object Labeler {
    private const val MODEL_FILE = "mobilenet.tflite"
    private var classifier: ImageClassifier? = null
    private var loadError: String? = null

    @Synchronized
    private fun getClassifier(context: Context): ImageClassifier? {
        if (classifier != null) return classifier
        try {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(5)
                .build()
            classifier = ImageClassifier.createFromFileAndOptions(context.applicationContext, MODEL_FILE, options)
            loadError = null
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
        }
        return classifier
    }

    @Synchronized
    fun classify(context: Context, bitmap: Bitmap): LabelResult {
        val c = getClassifier(context)
        if (c == null) {
            return LabelResult("model not loaded", null, "MobileNet model could not be loaded: ${loadError ?: "unknown error"}")
        }

        val argb = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val results = c.classify(TensorImage.fromBitmap(argb))

        val found = ArrayList<Pair<String, Float>>()
        for (r in results) {
            for (cat in r.categories) {
                val name = cat.label ?: ""
                if (name.isNotEmpty() && name != "background") {
                    found.add(Pair(name, cat.score))
                }
            }
        }
        found.sortByDescending { it.second }

        if (found.isEmpty()) {
            return LabelResult("uncertain", null, "MobileNet found nothing it recognises")
        }

        val top = found[0]
        val summary = found.take(3).joinToString("\n") { "${it.first} - ${(it.second * 100).toInt()}%" }
        val label = if (top.second < 0.10f) "uncertain" else top.first
        return LabelResult(label, top.second, summary)
    }
}
