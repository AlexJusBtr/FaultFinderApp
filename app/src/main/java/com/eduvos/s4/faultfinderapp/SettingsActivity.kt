package com.eduvos.s4.faultfinderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import org.osmdroid.config.Configuration

class SettingsActivity : AppCompatActivity() {

    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabLeft: FloatingActionButton
    private lateinit var fabDiag: FloatingActionButton
    private lateinit var fabTop: FloatingActionButton
    private var isFabMenuOpen = false

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.sendStatusNotification(
                context = this,
                ref = "FLT-TEST",
                title = "Sample Municipal Fault",
                newStatus = "In Progress"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val switchAlerts = findViewById<MaterialSwitch>(R.id.switchStatusAlerts)
        val switchAnonymous = findViewById<MaterialSwitch>(R.id.switchDefaultAnonymous)
        val layoutNotificationOptions = findViewById<LinearLayout>(R.id.layoutNotificationOptions)
        val rgScope = findViewById<RadioGroup>(R.id.rgNotificationScope)
        val layoutDistanceContainer = findViewById<LinearLayout>(R.id.layoutDistanceContainer)
        val seekBarDistance = findViewById<SeekBar>(R.id.seekBarDistance)
        val tvMaxDistanceValue = findViewById<TextView>(R.id.tvMaxDistanceValue)

        // Load saved preferences
        val alertsEnabled = prefs.getBoolean("status_alerts", true)
        val notifyAllNearby = prefs.getBoolean("notify_all_nearby", false)
        val maxDistanceKm = prefs.getInt("notify_max_distance_km", 5).coerceIn(1, 25)

        switchAlerts.isChecked = alertsEnabled
        switchAnonymous.isChecked = prefs.getBoolean("default_anonymous", false)
        layoutNotificationOptions.visibility = if (alertsEnabled) View.VISIBLE else View.GONE

        if (notifyAllNearby) {
            rgScope.check(R.id.rbNotifyNearby)
            layoutDistanceContainer.visibility = View.VISIBLE
        } else {
            rgScope.check(R.id.rbNotifyMine)
            layoutDistanceContainer.visibility = View.GONE
        }

        seekBarDistance.progress = maxDistanceKm - 1
        tvMaxDistanceValue.text = "$maxDistanceKm km"

        // Master notification toggle listener
        switchAlerts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("status_alerts", isChecked).apply()
            layoutNotificationOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Scope toggle listener (Only Mine vs All Nearby)
        rgScope.setOnCheckedChangeListener { _, checkedId ->
            val allNearby = (checkedId == R.id.rbNotifyNearby)
            prefs.edit().putBoolean("notify_all_nearby", allNearby).apply()
            layoutDistanceContainer.visibility = if (allNearby) View.VISIBLE else View.GONE
        }

        // Distance slider listener (1 km to 25 km)
        seekBarDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val km = (progress + 1).coerceIn(1, 25)
                tvMaxDistanceValue.text = "$km km"
                prefs.edit().putInt("notify_max_distance_km", km).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Test notification button
        findViewById<MaterialButton>(R.id.btnTestNotification).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationHelper.sendStatusNotification(
                    context = this,
                    ref = "FLT-DEMO",
                    title = "Burst Water Pipe",
                    newStatus = "In Progress"
                )
                Toast.makeText(this, "Test notification sent!", Toast.LENGTH_SHORT).show()
            }
        }

        // Anonymous toggle listener
        switchAnonymous.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("default_anonymous", isChecked).apply()
        }

        // Clear OSM cache button
        findViewById<MaterialButton>(R.id.btnClearCache).setOnClickListener {
            try {
                val osmCacheDir = Configuration.getInstance().osmdroidTileCache
                if (osmCacheDir.exists()) {
                    osmCacheDir.deleteRecursively()
                    osmCacheDir.mkdirs()
                }
                Toast.makeText(this, "Map tile cache cleared successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to clear cache: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        setupSpeedDial()
    }

    private fun setupSpeedDial() {
        fabMain = findViewById(R.id.fabMain)
        fabLeft = findViewById(R.id.fabLeft)
        fabDiag = findViewById(R.id.fabDiag)
        fabTop = findViewById(R.id.fabTop)

        listOf(fabLeft, fabDiag, fabTop).forEach {
            it.alpha = 0f
            it.scaleX = 0f
            it.scaleY = 0f
            it.visibility = View.INVISIBLE
        }

        fabMain.setOnClickListener { toggleSpeedDial() }

        fabLeft.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }

        fabDiag.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MyReportsActivity::class.java))
            finish()
        }

        fabTop.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, ReportActivity::class.java))
            finish()
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    private fun toggleSpeedDial() {
        isFabMenuOpen = !isFabMenuOpen
        if (isFabMenuOpen) {
            listOf(fabLeft, fabDiag, fabTop).forEach { it.visibility = View.VISIBLE }
            fabMain.animate().rotation(45f).setDuration(220).start()
            fabLeft.animate().translationX(-dpToPx(75f)).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
            fabDiag.animate().translationX(-dpToPx(55f)).translationY(-dpToPx(55f)).alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
            fabTop.animate().translationX(0f).translationY(-dpToPx(75f)).alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
        } else {
            fabMain.animate().rotation(0f).setDuration(200).start()
            listOf(fabLeft, fabDiag, fabTop).forEach { fab ->
                fab.animate().translationX(0f).translationY(0f).alpha(0f).scaleX(0f).scaleY(0f).setDuration(200)
                    .withEndAction { fab.visibility = View.INVISIBLE }.start()
            }
        }
    }
}