package com.example.contextsnap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

// DEMO PLACEHOLDER: labels a photo as "bright" or "dark" from its average brightness.
// This is NOT deep learning. To use the real MobileNet model, replace this whole file
// with addon_mobilenet/Labeler.kt (see README, "Add MobileNet").
object Labeler {
    fun classify(context: Context, bitmap: Bitmap): Pair<String, Float?> {
        var sum = 0L
        var n = 0
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                val px = bitmap.getPixel(x, y)
                sum += (Color.red(px) + Color.green(px) + Color.blue(px)) / 3
                n++
            }
        }
        val avg = if (n == 0) 0 else (sum / n).toInt()
        val label = if (avg > 110) "bright scene [placeholder]" else "dark scene [placeholder]"
        return Pair(label, null)
    }
}
