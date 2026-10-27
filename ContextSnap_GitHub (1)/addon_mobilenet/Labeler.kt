package com.example.contextsnap

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

// REAL MobileNet version. Replace the demo Labeler.kt with this file
// (same name, same package) AFTER adding the dependency + model (see README).
object Labeler {
    private var classifier: ImageClassifier? = null

    fun classify(context: Context, bitmap: Bitmap): Pair<String, Float?> {
        val c = classifier ?: ImageClassifier.createFromFileAndOptions(
            context,
            "mobilenet.tflite",
            ImageClassifier.ImageClassifierOptions.builder().setMaxResults(1).build()
        ).also { classifier = it }

        val argb = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val top = c.classify(TensorImage.fromBitmap(argb)).firstOrNull()?.categories?.firstOrNull()
            ?: return Pair("uncertain", null)

        return if (top.score < 0.5f) Pair("uncertain", top.score) else Pair(top.label, top.score)
    }
}
