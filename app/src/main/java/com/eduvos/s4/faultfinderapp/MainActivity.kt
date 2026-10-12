package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabReport: FloatingActionButton
    private lateinit var fabMap: FloatingActionButton
    private lateinit var fabSettings: FloatingActionButton
    private var isFabMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Optimize osmdroid cache & threading
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        osmConfig.userAgentValue = packageName
        osmConfig.tileFileSystemCacheMaxBytes = 100L * 1024 * 1024 // 100 MB disk tile cache
        osmConfig.tileFileSystemCacheTrimBytes = 80L * 1024 * 1024 // 80 MB trim threshold
        osmConfig.cacheMapTileCount = 24.toShort()
        osmConfig.tileDownloadThreads = 4

        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupMap()
        setupRecentReports()
        setupSpeedDial()
        setupCategoryAndHeaderListeners()
    }

    private fun setupMap() {
        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.isFlingEnabled = true
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        map.isHorizontalMapRepetitionEnabled = false
        map.isVerticalMapRepetitionEnabled = false

        val colorMatrix = ColorMatrix().apply { setSaturation(0.1f) }
        map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(colorMatrix))

        val mapController = map.controller
        mapController.setZoom(14.5)
        val defaultCenter = GeoPoint(-33.9249, 18.4241) // Default Cape Town center
        mapController.setCenter(defaultCenter)

        val marker = Marker(map).apply {
            position = defaultCenter
            title = "Sample Fault"
            snippet = "Pothole - High Priority"
        }
        map.overlays.add(marker)
    }

    private fun setupRecentReports() {
        val recentReportsList: ListView = findViewById(R.id.listRecentReports)
        val reportsData = listOf(
            "🚧 Pothole on Main Rd • Table View (Pending)",
            "💧 Burst Pipe • Blaauwberg St (In Progress)",
            "💡 Streetlight Out • Parklands North (Resolved)",
            "🗑️ Illegal Dumping • Sandown Rd (Pending)"
        )

        recentReportsList.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, reportsData) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as TextView
                textView.textSize = 11.5f
                textView.setTextColor(Color.parseColor("#475569"))
                textView.setPadding(18, 12, 18, 12)
                return textView
            }
        }

        recentReportsList.setOnItemClickListener { _, _, _, _ ->
            startActivity(Intent(this, MyReportsActivity::class.java))
        }
    }

    private fun setupSpeedDial() {
        fabMain = findViewById(R.id.fabMain)
        fabReport = findViewById(R.id.fabReport) // Top
        fabMap = findViewById(R.id.fabMap)       // Left
        fabSettings = findViewById(R.id.fabSettings) // Diagonally Top-Left

        listOf(fabReport, fabMap, fabSettings).forEach { resetFabInitialState(it) }

        fabMain.setOnClickListener { toggleSpeedDial() }

        // Report FAB routes directly to ReportActivity
        fabReport.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, ReportActivity::class.java))
        }

        // Map and Settings do nothing at the moment
        fabMap.setOnClickListener {
            toggleSpeedDial()
        }

        fabSettings.setOnClickListener {
            toggleSpeedDial()
        }
    }

    private fun setupCategoryAndHeaderListeners() {
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, MyReportsActivity::class.java))
        }

        val categoryButtons = mapOf(
            R.id.roadsBtn to "Roads",
            R.id.waterBtn to "Water",
            R.id.electricityBtn to "Electricity",
            R.id.lightsBtn to "Streetlights",
            R.id.wasteBtn to "Waste",
            R.id.otherBtn to "Other"
        )

        categoryButtons.forEach { (viewId, category) ->
            findViewById<TextView>(viewId).setOnClickListener {
                val intent = Intent(this, ReportActivity::class.java).apply {
                    putExtra("SELECTED_CATEGORY", category)
                }
                startActivity(intent)
            }
        }
    }

    private fun resetFabInitialState(fab: FloatingActionButton) {
        fab.alpha = 0f
        fab.scaleX = 0f
        fab.scaleY = 0f
        fab.visibility = View.INVISIBLE
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    private fun toggleSpeedDial() {
        isFabMenuOpen = !isFabMenuOpen

        if (isFabMenuOpen) {
            listOf(fabReport, fabMap, fabSettings).forEach { it.visibility = View.VISIBLE }
            fabMain.animate().rotation(45f).setDuration(220).start()

            // 1. Mini FAB Left: Map
            fabMap.animate()
                .translationX(-dpToPx(75f))
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .start()

            // 2. Mini FAB Diagonally Top-Left: Settings
            fabSettings.animate()
                .translationX(-dpToPx(55f))
                .translationY(-dpToPx(55f))
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .start()

            // 3. Mini FAB Top: Report
            fabReport.animate()
                .translationX(0f)
                .translationY(-dpToPx(75f))
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .start()
        } else {
            fabMain.animate().rotation(0f).setDuration(200).start()

            listOf(fabReport, fabMap, fabSettings).forEach { fab ->
                fab.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .alpha(0f)
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(200)
                    .withEndAction { fab.visibility = View.INVISIBLE }
                    .start()
            }
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
    override fun onDestroy() { super.onDestroy(); map.onDetach() }
}