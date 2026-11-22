package com.example.contextsnap

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// One row in the History list: either a recording session or a stored photo.
private class HistoryEntry(val text: String, val session: SessionRow?, val image: ImageRow?)

class HistoryActivity : AppCompatActivity() {

    private val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val db = Db.get(this)
        val userId = SessionStore.userId(this)

        val entries = ArrayList<HistoryEntry>()
        for (s in db.listSessions(userId)) {
            val seconds = ((s.end ?: s.start) - s.start) / 1000
            entries.add(
                HistoryEntry(
                    "SESSION: ${s.name ?: "Untitled"}\n${fmt.format(Date(s.start))}  |  ${seconds}s  |  ${s.readings} readings\n(tap to see saved values)",
                    s, null
                )
            )
        }
        for (img in db.listImages(userId)) {
            val c = img.confidence
            val conf = if (c != null) " (${(c * 100).toInt()}%)" else ""
            entries.add(
                HistoryEntry(
                    "PHOTO #${img.id} (${img.cameraId} camera)\n${fmt.format(Date(img.time))}\n${img.label ?: "not labeled"}$conf",
                    null, img
                )
            )
        }

        if (entries.isEmpty()) {
            findViewById<TextView>(R.id.textEmpty).visibility = View.VISIBLE
        }

        val list = findViewById<ListView>(R.id.listHistory)
        list.adapter = object : BaseAdapter() {
            override fun getCount(): Int = entries.size
            override fun getItem(position: Int): Any = entries[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_history, parent, false)
                val entry = entries[position]
                v.findViewById<TextView>(R.id.textHistoryItem).text = entry.text

                // Photos show their picture, read back out of the database.
                val thumb = v.findViewById<ImageView>(R.id.imageHistoryThumb)
                val img = entry.image
                var shown = false
                if (img != null) {
                    val bytes = db.getImageBytes(img.id)
                    if (bytes != null) {
                        thumb.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        shown = true
                    }
                }
                if (!shown) thumb.setImageDrawable(null)
                thumb.visibility = if (shown) View.VISIBLE else View.GONE
                return v
            }
        }

        list.setOnItemClickListener { _, _, position, _ ->
            val entry = entries[position]
            val img = entry.image
            val session = entry.session
            if (img != null) {
                showPhoto(db, img)
            } else if (session != null) {
                showSession(db, session)
            }
        }
    }

    private fun showSession(db: Db, s: SessionRow) {
        val counts = db.readingsPerSensor(s.id).joinToString("\n") { "${it.first}: ${it.second}" }
        val latest = db.latestReadings(s.id, 10).joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle(s.name ?: "Session")
            .setMessage("Readings saved per sensor:\n$counts\n\nLatest saved values:\n$latest")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPhoto(db: Db, img: ImageRow) {
        val view = layoutInflater.inflate(R.layout.dialog_photo, null)
        val bytes = db.getImageBytes(img.id)
        val picture = view.findViewById<ImageView>(R.id.imageDialogPhoto)
        if (bytes != null) {
            picture.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
        val c = img.confidence
        val conf = if (c != null) " (${(c * 100).toInt()}%)" else ""
        view.findViewById<TextView>(R.id.textDialogInfo).text =
            "Camera: ${img.cameraId}\n" +
            "Time: ${fmt.format(Date(img.time))}\n" +
            "Label: ${img.label ?: "not labeled"}$conf\n" +
            "Stored in database as image #${img.id}\n" +
            "File copy: ${img.filePath}"
        AlertDialog.Builder(this)
            .setTitle("Photo")
            .setView(view)
            .setPositiveButton("OK", null)
            .show()
    }
}
