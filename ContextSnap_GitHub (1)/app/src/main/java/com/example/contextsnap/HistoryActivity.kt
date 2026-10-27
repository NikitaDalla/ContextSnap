package com.example.contextsnap

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val db = Db.get(this)
        val userId = SessionStore.userId(this)
        val sessions = db.listSessions(userId)
        val images = db.listImages(userId)
        val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

        val rows = ArrayList<String>()
        for (s in sessions) {
            val seconds = ((s.end ?: s.start) - s.start) / 1000
            rows.add("SESSION: ${s.name ?: "Untitled"}\n${fmt.format(Date(s.start))}  |  ${seconds}s  |  ${s.readings} readings")
        }
        for (img in images) {
            val c = img.confidence
            val conf = if (c != null) " (${(c * 100).toInt()}%)" else ""
            rows.add("PHOTO (${img.cameraId} camera)\n${fmt.format(Date(img.time))}  |  ${img.label ?: "not labeled"}$conf")
        }

        if (rows.isEmpty()) {
            findViewById<TextView>(R.id.textEmpty).visibility = View.VISIBLE
        }

        val list = findViewById<ListView>(R.id.listHistory)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        list.setOnItemClickListener { _, _, position, _ ->
            if (position < sessions.size) {
                val s = sessions[position]
                val counts = db.readingsPerSensor(s.id).joinToString("\n") { "${it.first}: ${it.second}" }
                val latest = db.latestReadings(s.id, 5).joinToString("\n")
                AlertDialog.Builder(this)
                    .setTitle(s.name ?: "Session")
                    .setMessage("Readings per sensor:\n$counts\n\nLatest readings:\n$latest")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
