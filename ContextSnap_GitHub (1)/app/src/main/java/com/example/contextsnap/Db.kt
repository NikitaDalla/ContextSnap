package com.example.contextsnap

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserRow(val id: Long, val name: String, val email: String, val passHash: String, val answerHash: String)
data class SessionRow(val id: Long, val name: String?, val start: Long, val end: Long?, val readings: Int)
data class ImageRow(val id: Long, val cameraId: String, val time: Long, val label: String?, val confidence: Float?)

// Remembers which recording session (if any) is running right now.
object AppState {
    @Volatile var activeSessionId: Long = -1L
}

// The whole database: 4 tables (users, sessions, sensor_readings, images).
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "context_snap_demo.db", null, 1) {

    companion object {
        @Volatile private var instance: Db? = null
        fun get(context: Context): Db =
            instance ?: synchronized(this) { instance ?: Db(context).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, answer_hash TEXT NOT NULL)")
        db.execSQL("CREATE TABLE sessions (session_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, name TEXT, start_time INTEGER NOT NULL, end_time INTEGER)")
        db.execSQL("CREATE TABLE sensor_readings (reading_id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, sensor_name TEXT NOT NULL, timestamp INTEGER NOT NULL, vals TEXT NOT NULL, accuracy INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE images (image_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, session_id INTEGER, camera_id TEXT NOT NULL, timestamp INTEGER NOT NULL, file_path TEXT NOT NULL, ml_label TEXT, confidence REAL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------- users ----------
    fun createUser(name: String, email: String, passHash: String, answerHash: String): Long {
        val cv = ContentValues()
        cv.put("name", name)
        cv.put("email", email.lowercase())
        cv.put("password_hash", passHash)
        cv.put("answer_hash", answerHash)
        return writableDatabase.insert("users", null, cv)   // returns -1 if the email already exists
    }

    fun getUserByEmail(email: String): UserRow? {
        val c = readableDatabase.rawQuery(
            "SELECT user_id, name, email, password_hash, answer_hash FROM users WHERE email = ?",
            arrayOf(email.lowercase())
        )
        var result: UserRow? = null
        if (c.moveToFirst()) {
            result = UserRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
        }
        c.close()
        return result
    }

    fun updatePassword(userId: Long, newHash: String) {
        val cv = ContentValues()
        cv.put("password_hash", newHash)
        writableDatabase.update("users", cv, "user_id = ?", arrayOf(userId.toString()))
    }

    // ---------- sessions & readings ----------
    fun startSession(userId: Long, name: String?): Long {
        val cv = ContentValues()
        cv.put("user_id", userId)
        cv.put("name", name)
        cv.put("start_time", System.currentTimeMillis())
        return writableDatabase.insert("sessions", null, cv)
    }

    fun endSession(sessionId: Long) {
        val cv = ContentValues()
        cv.put("end_time", System.currentTimeMillis())
        writableDatabase.update("sessions", cv, "session_id = ?", arrayOf(sessionId.toString()))
    }

    fun insertReading(sessionId: Long, sensorName: String, timestamp: Long, vals: String, accuracy: Int) {
        val cv = ContentValues()
        cv.put("session_id", sessionId)
        cv.put("sensor_name", sensorName)
        cv.put("timestamp", timestamp)
        cv.put("vals", vals)
        cv.put("accuracy", accuracy)
        writableDatabase.insert("sensor_readings", null, cv)
    }

    fun listSessions(userId: Long): List<SessionRow> {
        val list = ArrayList<SessionRow>()
        readableDatabase.rawQuery(
            "SELECT s.session_id, s.name, s.start_time, s.end_time, " +
                "(SELECT COUNT(*) FROM sensor_readings r WHERE r.session_id = s.session_id) " +
                "FROM sessions s WHERE s.user_id = ? ORDER BY s.start_time DESC",
            arrayOf(userId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    SessionRow(
                        c.getLong(0),
                        if (c.isNull(1)) null else c.getString(1),
                        c.getLong(2),
                        if (c.isNull(3)) null else c.getLong(3),
                        c.getInt(4)
                    )
                )
            }
        }
        return list
    }

    fun readingsPerSensor(sessionId: Long): List<Pair<String, Int>> {
        val list = ArrayList<Pair<String, Int>>()
        readableDatabase.rawQuery(
            "SELECT sensor_name, COUNT(*) FROM sensor_readings WHERE session_id = ? GROUP BY sensor_name",
            arrayOf(sessionId.toString())
        ).use { c -> while (c.moveToNext()) list.add(Pair(c.getString(0), c.getInt(1))) }
        return list
    }

    fun latestReadings(sessionId: Long, n: Int): List<String> {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val list = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT sensor_name, timestamp, vals FROM sensor_readings WHERE session_id = ? ORDER BY timestamp DESC LIMIT $n",
            arrayOf(sessionId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add("${fmt.format(Date(c.getLong(1)))}  ${c.getString(0)}: ${c.getString(2)}")
            }
        }
        return list
    }

    // ---------- images ----------
    fun insertImage(userId: Long, sessionId: Long?, cameraId: String, filePath: String): Long {
        val cv = ContentValues()
        cv.put("user_id", userId)
        cv.put("session_id", sessionId)
        cv.put("camera_id", cameraId)
        cv.put("timestamp", System.currentTimeMillis())
        cv.put("file_path", filePath)
        return writableDatabase.insert("images", null, cv)
    }

    fun updateImageLabel(imageId: Long, label: String, confidence: Float?) {
        val cv = ContentValues()
        cv.put("ml_label", label)
        cv.put("confidence", confidence)
        writableDatabase.update("images", cv, "image_id = ?", arrayOf(imageId.toString()))
    }

    fun listImages(userId: Long): List<ImageRow> {
        val list = ArrayList<ImageRow>()
        readableDatabase.rawQuery(
            "SELECT image_id, camera_id, timestamp, ml_label, confidence FROM images WHERE user_id = ? ORDER BY timestamp DESC",
            arrayOf(userId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ImageRow(
                        c.getLong(0), c.getString(1), c.getLong(2),
                        if (c.isNull(3)) null else c.getString(3),
                        if (c.isNull(4)) null else c.getFloat(4)
                    )
                )
            }
        }
        return list
    }
}
