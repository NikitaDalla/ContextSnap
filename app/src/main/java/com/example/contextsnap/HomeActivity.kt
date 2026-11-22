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
    private lateinit var liveText: Array<String>      // text shown for each row
    private lateinit var gotReading: BooleanArray     // has this row received a real value yet?
    private lateinit var lastWrite: LongArray         // throttle database writes per row
    private lateinit var stepBase: FloatArray         // step counter value when this screen opened
    private lateinit var stepCount: IntArray          // steps counted from the step detector
    private lateinit var adapter: RowAdapter
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView

    private val bg = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var refreshPending = false
    private var askedPermissions = false
    private var lastGpsFix = 0L

    // The GPS row is one extra row after all the SensorManager sensors.
    private val gpsRow: Int
        get() = sensors.size

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            registerSensors()
            startGps()
            refreshInitialTexts()
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

        val n = sensors.size + 1
        checked = BooleanArray(n)
        liveText = Array(n) { "--" }
        gotReading = BooleanArray(n)
        lastWrite = LongArray(n)
        stepBase = FloatArray(n) { -1f }
        stepCount = IntArray(n)
        for (i in sensors.indices) {
            liveText[i] = initialText(sensors[i])
        }
        liveText[gpsRow] = "Searching for location (needs Location permission)..."

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

    // ---------- permissions ----------
    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // Step counter / step detector need "Physical activity"; heart rate needs "Body sensors";
    // GPS needs "Location". Without these, Android silently delivers no readings.
    private fun askForPermissions() {
        if (askedPermissions) return
        val needed = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACTIVITY_RECOGNITION)) {
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        val hasHeartRate = sensors.any { it.type == Sensor.TYPE_HEART_RATE }
        if (hasHeartRate && !has(Manifest.permission.BODY_SENSORS)) {
            needed.add(Manifest.permission.BODY_SENSORS)
        }
        if (needed.isNotEmpty()) {
            askedPermissions = true
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    // ---------- row text ----------
    private fun initialText(s: Sensor): String {
        if (s.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
            return "Event sensor: it fires once when triggered, so it has no continuous value"
        }
        return when (s.type) {
            Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR ->
                if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACTIVITY_RECOGNITION))
                    "Needs the Physical activity permission. Allow it when asked."
                else
                    "Waiting for steps. Walk a few steps with the phone."
            Sensor.TYPE_HEART_RATE ->
                if (!has(Manifest.permission.BODY_SENSORS))
                    "Needs the Body sensors permission. Allow it when asked."
                else
                    "Hold a finger on the heart-rate sensor."
            else ->
                if (s.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE)
                    "Waiting for a change (this sensor only reports when its value changes)..."
                else
                    "Waiting for the first reading..."
        }
    }

    private fun refreshInitialTexts() {
        for (i in sensors.indices) {
            if (!gotReading[i]) liveText[i] = initialText(sensors[i])
        }
        adapter.notifyDataSetChanged()
    }

    // Turns raw numbers into readable text with the correct unit for each sensor type.
    private fun describe(i: Int, event: SensorEvent): String {
        val v = event.values
        fun f(x: Float): String = String.format(Locale.US, "%.2f", x)
        return when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION ->
                "x ${f(v[0])}   y ${f(v[1])}   z ${f(v[2])}   m/s²"
            Sensor.TYPE_GYROSCOPE ->
                "x ${f(v[0])}   y ${f(v[1])}   z ${f(v[2])}   rad/s"
            Sensor.TYPE_MAGNETIC_FIELD ->
                "x ${f(v[0])}   y ${f(v[1])}   z ${f(v[2])}   µT"
            Sensor.TYPE_LIGHT -> "${f(v[0])} lux"
            Sensor.TYPE_PRESSURE -> "${f(v[0])} hPa"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "${f(v[0])} °C"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "${f(v[0])} %"
            Sensor.TYPE_PROXIMITY ->
                if (v[0] < event.sensor.maximumRange) "NEAR  (${f(v[0])} cm)" else "FAR  (${f(v[0])} cm)"
            Sensor.TYPE_HEART_RATE ->
                if (v[0] > 0f) "${f(v[0])} bpm" else "Measuring... keep a finger on the sensor"
            Sensor.TYPE_STEP_COUNTER -> {
                if (stepBase[i] < 0f) stepBase[i] = v[0]
                "${(v[0] - stepBase[i]).toInt()} steps since you opened this screen (total since phone restart: ${v[0].toInt()})"
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                stepCount[i] += 1
                "${stepCount[i]} steps detected since you opened this screen"
            }
            else -> v.joinToString(", ") { f(it) }
        }
    }

    private fun updateButtons() {
        val recording = AppState.activeSessionId != -1L
        startButton.isEnabled = !recording && checked.any { it }
        stopButton.isEnabled = recording
        statusText.text = if (recording) "●  Recording - only ticked rows are saved" else "Not recording. Tick sensors, then press Start."
        statusText.setTextColor(ContextCompat.getColor(this, if (recording) R.color.recording_red else R.color.text_secondary))
    }

    private fun scheduleRefresh() {
        if (!refreshPending) {
            refreshPending = true
            ui.postDelayed({ refreshPending = false; adapter.notifyDataSetChanged() }, 300)
        }
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
                Fingerprint.prompt(this, "Confirm your fingerprint",
                    onSuccess = { Fingerprint.enableFor(this, SessionStore.userId(this)); refresh() },
                    onFail = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() })
            }
        }
    }

    // ---------- start / stop listening ----------
    private fun registerSensors() {
        for ((i, sensor) in sensors.withIndex()) {
            // One-shot "event" sensors cannot be read with this kind of listener.
            if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) continue
            try {
                val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                if (!ok && !gotReading[i]) liveText[i] = "This sensor could not be started on this phone"
            } catch (e: SecurityException) {
                if (!gotReading[i]) liveText[i] = "Permission needed for this sensor"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::sensorManager.isInitialized) return
        registerSensors()
        startGps()
        updateButtons()
        askForPermissions()
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        if (!::sensorManager.isInitialized) return
        sensorManager.unregisterListener(this)
        try {
            locationManager.removeUpdates(gpsListener)
        } catch (e: SecurityException) {
            // permission was revoked while the app was open; nothing to remove
        }
    }

    // ---------- GPS / location ----------
    // Uses the GPS chip outdoors and the network (Wi-Fi / mobile) location indoors,
    // so a position still appears when there is no clear view of the sky.
    @SuppressLint("MissingPermission")
    private fun startGps() {
        val fine = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = has(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) {
            liveText[gpsRow] = "Needs the Location permission. Allow it when asked."
            return
        }
        var started = false
        try {
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener)
                started = true
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, gpsListener)
                started = true
            }
        } catch (e: SecurityException) {
            liveText[gpsRow] = "Needs the Location permission. Allow it when asked."
            return
        }
        if (!started) {
            liveText[gpsRow] = "Location is switched off. Turn on Location in the phone's settings."
            return
        }
        if (!gotReading[gpsRow]) {
            val last = bestLastKnown()
            if (last != null) {
                showLocation(last, "Last known")
            } else {
                liveText[gpsRow] = "Searching for location... go near a window or outdoors."
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(): Location? {
        var best: Location? = null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (p in providers) {
            try {
                val l = locationManager.getLastKnownLocation(p)
                if (l != null && (best == null || l.time > best.time)) best = l
            } catch (e: SecurityException) {
                // no permission for this provider
            } catch (e: IllegalArgumentException) {
                // provider does not exist on this phone
            }
        }
        return best
    }

    private fun showLocation(location: Location, source: String) {
        liveText[gpsRow] = String.format(
            Locale.US, "%s: lat %.6f, lon %.6f, alt %.0f m (accuracy ±%.0f m)",
            source, location.latitude, location.longitude, location.altitude, location.accuracy
        )
        gotReading[gpsRow] = true
        scheduleRefresh()
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = System.currentTimeMillis()
            val isGps = location.provider == LocationManager.GPS_PROVIDER
            if (isGps) {
                lastGpsFix = now
            } else if (now - lastGpsFix < 10000L) {
                return   // ignore the less precise network position while GPS is working
            }
            showLocation(location, if (isGps) "GPS" else "Network (approximate)")

            val row = gpsRow
            val sessionId = AppState.activeSessionId
            if (sessionId != -1L && checked[row] && now - lastWrite[row] >= 1000L) {
                lastWrite[row] = now
                val vals = "${location.latitude},${location.longitude},${location.altitude}"
                val accuracy = location.accuracy.toInt()
                bg.execute { Db.get(applicationContext).insertReading(sessionId, "GPS Location", now, vals, accuracy) }
            }
        }
        // These are needed so the listener also works on older Android versions.
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
        gotReading[i] = true
        liveText[i] = describe(i, event)
        scheduleRefresh()

        // 2) Save to the database ONLY if a session is running AND this sensor is ticked.
        val sessionId = AppState.activeSessionId
        val now = System.currentTimeMillis()
        if (sessionId != -1L && checked[i] && now - lastWrite[i] >= 200L) {
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
