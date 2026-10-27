package com.example.contextsnap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executors

class HomeActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private lateinit var sensors: List<Sensor>
    private lateinit var checked: BooleanArray        // which rows the user ticked (last row = GPS)
    private lateinit var liveText: Array<String>      // latest value text per row
    private lateinit var lastWrite: LongArray         // throttle database writes per row
    private lateinit var adapter: RowAdapter
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView

    private val bg = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var refreshPending = false
    private var askedLocationPermission = false

    // The GPS row is one extra row after all the SensorManager sensors.
    private val gpsRow: Int
        get() = sensors.size

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startGps() else {
                liveText[gpsRow] = "Location permission denied"
                adapter.notifyDataSetChanged()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SessionStore.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_home)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        checked = BooleanArray(sensors.size + 1)
        liveText = Array(sensors.size + 1) { "--" }
        liveText[gpsRow] = "Waiting for GPS fix (go outdoors)..."
        lastWrite = LongArray(sensors.size + 1)

        startButton = findViewById(R.id.buttonStart)
        stopButton = findViewById(R.id.buttonStop)
        statusText = findViewById(R.id.textStatus)
        val sessionName = findViewById<EditText>(R.id.editSessionName)

        adapter = RowAdapter()
        findViewById<ListView>(R.id.listSensors).adapter = adapter

        startButton.setOnClickListener {
            val id = Db.get(this).startSession(
                SessionStore.userId(this),
                sessionName.text.toString().trim().ifEmpty { null }
            )
            AppState.activeSessionId = id
            updateButtons()
        }

        stopButton.setOnClickListener {
            val id = AppState.activeSessionId
            if (id != -1L) Db.get(this).endSession(id)
            AppState.activeSessionId = -1L
            updateButtons()
            Toast.makeText(this, "Session saved. See History.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.buttonCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        findViewById<Button>(R.id.buttonHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.buttonLogout).setOnClickListener {
            val id = AppState.activeSessionId
            if (id != -1L) Db.get(this).endSession(id)
            SessionStore.logout(this)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        setupFingerprintButton()
        updateButtons()
    }

    // ---------- fingerprint on/off toggle ----------
    private fun setupFingerprintButton() {
        val bioButton = findViewById<Button>(R.id.buttonFingerprint)
        if (!Fingerprint.isAvailable(this)) {
            bioButton.visibility = View.GONE
            return
        }
        fun refresh() {
            val on = Fingerprint.enabledUser(this) == SessionStore.userId(this)
            bioButton.text = if (on) "Fingerprint login: ON (tap to turn off)" else "Enable fingerprint login"
        }
        refresh()
        bioButton.setOnClickListener {
            if (Fingerprint.enabledUser(this) == SessionStore.userId(this)) {
                Fingerprint.disable(this)
                refresh()
            } else if (Build.VERSION.SDK_INT >= 28) {
                // Must scan a finger once to switch it on.
                Fingerprint.prompt(this, "Confirm your fingerprint",
                    onSuccess = { Fingerprint.enableFor(this, SessionStore.userId(this)); refresh() },
                    onFail = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() })
            }
        }
    }

    private fun updateButtons() {
        val recording = AppState.activeSessionId != -1L
        startButton.isEnabled = !recording && checked.any { it }
        stopButton.isEnabled = recording
        statusText.text = if (recording) "Recording... (only ticked rows are saved)" else "Not recording. Tick sensors, then press Start."
    }

    private fun scheduleRefresh() {
        if (!refreshPending) {
            refreshPending = true
            ui.postDelayed({ refreshPending = false; adapter.notifyDataSetChanged() }, 300)
        }
    }

    // ---------- start / stop listening ----------
    override fun onResume() {
        super.onResume()
        if (!::sensorManager.isInitialized) return
        for (sensor in sensors) {
            try {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            } catch (e: SecurityException) {
                // Some sensors (e.g. heart rate) need extra permission; skip them.
            }
        }
        startGps()
        updateButtons()
    }

    override fun onPause() {
        super.onPause()
        if (!::sensorManager.isInitialized) return
        sensorManager.unregisterListener(this)
        try { locationManager.removeUpdates(gpsListener) } catch (e: SecurityException) {}
    }

    // ---------- GPS ----------
    @SuppressLint("MissingPermission")
    private fun startGps() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            liveText[gpsRow] = "Location permission needed"
            if (!askedLocationPermission) {
                askedLocationPermission = true
                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            return
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            liveText[gpsRow] = "GPS is off - turn on Location in phone settings"
            return
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener)
        } catch (e: SecurityException) {
            liveText[gpsRow] = "Location permission needed"
        }
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val row = gpsRow
            liveText[row] = String.format(
                Locale.US, "lat %.6f, lon %.6f, alt %.1f m (accuracy %.0f m)",
                location.latitude, location.longitude, location.altitude, location.accuracy
            )
            scheduleRefresh()

            val sessionId = AppState.activeSessionId
            val now = System.currentTimeMillis()
            if (sessionId != -1L && checked[row] && now - lastWrite[row] >= 1000) {
                lastWrite[row] = now
                val vals = "${location.latitude},${location.longitude},${location.altitude}"
                val accuracy = location.accuracy.toInt()
                bg.execute { Db.get(applicationContext).insertReading(sessionId, "GPS Location", now, vals, accuracy) }
            }
        }
        // These three are needed so the listener also works on older Android versions.
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    // ---------- SensorManager sensors ----------
    override fun onSensorChanged(event: SensorEvent) {
        val i = sensors.indexOf(event.sensor)
        if (i < 0) return

        // 1) Live value on screen for EVERY sensor (ticked or not).
        liveText[i] = event.values.joinToString(", ") { String.format(Locale.US, "%.2f", it) }
        scheduleRefresh()

        // 2) Save to the database ONLY if a session is running AND this sensor is ticked.
        val sessionId = AppState.activeSessionId
        val now = System.currentTimeMillis()
        if (sessionId != -1L && checked[i] && now - lastWrite[i] >= 200) {
            lastWrite[i] = now
            val name = event.sensor.name
            val vals = event.values.joinToString(",")
            val accuracy = event.accuracy
            bg.execute { Db.get(applicationContext).insertReading(sessionId, name, now, vals, accuracy) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && AppState.activeSessionId != -1L && SessionStore.isLoggedIn(this)) {
            Db.get(this).endSession(AppState.activeSessionId)
            AppState.activeSessionId = -1L
        }
        bg.shutdown()
    }

    // ---------- list rows: all sensors + one GPS row ----------
    private inner class RowAdapter : BaseAdapter() {
        override fun getCount(): Int = sensors.size + 1
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_sensor, parent, false)
            v.findViewById<TextView>(R.id.textSensorName).text =
                if (position == gpsRow) "GPS Location" else sensors[position].name
            v.findViewById<TextView>(R.id.textSensorValue).text = liveText[position]
            val box = v.findViewById<CheckBox>(R.id.checkRecord)
            box.setOnCheckedChangeListener(null)
            box.isChecked = checked[position]
            box.setOnCheckedChangeListener { _, isChecked ->
                checked[position] = isChecked
                updateButtons()
            }
            return v
        }
    }
}
