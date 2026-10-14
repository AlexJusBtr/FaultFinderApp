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

/**
 * Home dashboard activity featuring an embedded OpenStreetMap preview,
 * quick-category reporting buttons, a recent reports list, and the speed-dial navigation FAB.
 */
class MainActivity : AppCompatActivity() {

    /**
     * Data model representing a fault item displayed on the Home screen and map preview.
     */
    data class FaultPreview(
        val ref: String,
        val emoji: String,
        val title: String,
        val location: String,
        val status: String,
        val category: String,
        val date: String,
        val description: String,
        val lat: Double,
        val lon: Double
    )

    private lateinit var map: MapView
    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabReport: FloatingActionButton
    private lateinit var fabMap: FloatingActionButton
    private lateinit var fabTracker: FloatingActionButton
    private val defaultCenter = GeoPoint(-33.8120, 18.4900) // Table View / Parklands area center
    private var isFabMenuOpen = false

    // Sample fault records used until live Firestore listener is attached
    private val recentFaults = listOf(
        FaultPreview(
            ref = "#CT-0001",
            emoji = "🚧",
            title = "Pothole on Main Rd",
            location = "Table View",
            status = "Pending",
            category = "Roads",
            date = "12 Oct 2026",
            description = "Deep pothole in the left lane near the intersection, causing vehicles to swerve.",
            lat = -33.8267,
            lon = 18.4912
        ),
        FaultPreview(
            ref = "#CT-0002",
            emoji = "💧",
            title = "Burst Pipe",
            location = "Blaauwberg St",
            status = "In Progress",
            category = "Water",
            date = "13 Oct 2026",
            description = "Clean water gushing onto the pavement and flooding the storm drain.",
            lat = -33.8075,
            lon = 18.4735
        ),
        FaultPreview(
            ref = "#CT-0003",
            emoji = "💡",
            title = "Streetlight Out",
            location = "Parklands North",
            status = "Resolved",
            category = "Streetlights",
            date = "10 Oct 2026",
            description = "Three consecutive streetlights out along the main residential stretch.",
            lat = -33.7965,
            lon = 18.5042
        ),
        FaultPreview(
            ref = "#CT-0004",
            emoji = "🗑️",
            title = "Illegal Dumping",
            location = "Sandown Rd",
            status = "Pending",
            category = "Waste",
            date = "14 Oct 2026",
            description = "Construction rubble and household refuse dumped on the road verge.",
            lat = -33.8012,
            lon = 18.5180
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure osmdroid tile cache and user agent
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        osmConfig.userAgentValue = packageName
        osmConfig.tileFileSystemCacheMaxBytes = 100L * 1024 * 1024
        osmConfig.tileFileSystemCacheTrimBytes = 80L * 1024 * 1024
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
        setupHeaderListeners()
    }

    /**
     * Initializes the embedded OpenStreetMap view, applies a subtle desaturation filter,
     * and places markers for recent faults.
     */
    private fun setupMap() {
        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.isFlingEnabled = true
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)

        val colorMatrix = ColorMatrix().apply { setSaturation(0.1f) }
        map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(colorMatrix))

        map.controller.setZoom(13.0)
        map.controller.setCenter(defaultCenter)

        // Add markers for each recent fault and allow tapping the marker window to view details
        recentFaults.forEach { fault ->
            val marker = Marker(map).apply {
                position = GeoPoint(fault.lat, fault.lon)
                title = "${fault.title} (${fault.ref})"
                snippet = "${fault.category} • ${fault.status}"
            }
            map.overlays.add(marker)
        }

        // Recenter button restores default zoom and position
        findViewById<ImageButton>(R.id.btnRecenter).setOnClickListener {
            map.controller.animateTo(defaultCenter)
            map.controller.setZoom(14.0)
        }
    }

    /**
     * Populates the recent reports ListView and wires click listeners for both
     * the section header (opens Nearby tab) and individual items (opens TrackActivity).
     */
    private fun setupRecentReports() {
        val recentReportsList: ListView = findViewById(R.id.listRecentReports)
        val reportsDisplayList = recentFaults.map {
            "${it.emoji} ${it.title} • ${it.location} (${it.status})"
        }

        recentReportsList.adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            reportsDisplayList
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as TextView
                textView.textSize = 11.5f
                textView.setTextColor(Color.parseColor("#475569"))
                textView.setPadding(18, 12, 18, 12)
                return textView
            }
        }

        // Tapping the "Recent reports near you / View all >" header opens the Nearby & History tab
        findViewById<View>(R.id.headerRecentReports).setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java).apply {
                putExtra("OPEN_NEARBY_TAB", true)
            }
            startActivity(intent)
        }

        // Tapping a specific fault report opens TrackActivity with its details
        recentReportsList.setOnItemClickListener { _, _, position, _ ->
            val fault = recentFaults[position]
            val intent = Intent(this, TrackActivity::class.java).apply {
                putExtra("EXTRA_REF", fault.ref)
                putExtra("EXTRA_TITLE", fault.title)
                putExtra("EXTRA_LOCATION", fault.location)
                putExtra("EXTRA_STATUS", fault.status)
                putExtra("EXTRA_CATEGORY", fault.category)
                putExtra("EXTRA_DATE", fault.date)
                putExtra("EXTRA_DESCRIPTION", fault.description)
                putExtra("EXTRA_LAT", fault.lat)
                putExtra("EXTRA_LON", fault.lon)
            }
            startActivity(intent)
        }
    }

    /**
     * Configures the bottom-right radial speed-dial FAB menu.
     */
    private fun setupSpeedDial() {
        fabMain = findViewById(R.id.fabMain)
        fabReport = findViewById(R.id.fabReport)
        fabMap = findViewById(R.id.fabMap)
        fabTracker = findViewById(R.id.fabTracker)

        listOf(fabReport, fabMap, fabTracker).forEach {
            it.alpha = 0f
            it.scaleX = 0f
            it.scaleY = 0f
            it.visibility = View.INVISIBLE
        }

        fabMain.setOnClickListener { toggleSpeedDial() }

        fabReport.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, ReportActivity::class.java))
        }

        fabTracker.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MyReportsActivity::class.java))
        }

        fabMap.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MapActivity::class.java))
        }
    }

    /**
     * Sets up listeners for the top profile icon and horizontal quick-report category chips.
     */
    private fun setupHeaderListeners() {
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        val quickReportButtons = mapOf(
            R.id.roadsBtn to "Roads",
            R.id.waterBtn to "Water",
            R.id.electricityBtn to "Electricity",
            R.id.lightsBtn to "Streetlights",
            R.id.wasteBtn to "Waste",
            R.id.otherBtn to "Other"
        )

        quickReportButtons.forEach { (viewId, category) ->
            findViewById<TextView>(viewId).setOnClickListener {
                val intent = Intent(this, ReportActivity::class.java).apply {
                    putExtra("SELECTED_CATEGORY", category)
                }
                startActivity(intent)
            }
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    /**
     * Animates the opening and closing of the speed-dial FAB cluster.
     */
    private fun toggleSpeedDial() {
        isFabMenuOpen = !isFabMenuOpen
        if (isFabMenuOpen) {
            listOf(fabReport, fabMap, fabTracker).forEach { it.visibility = View.VISIBLE }
            fabMain.animate().rotation(45f).setDuration(220).start()
            fabMap.animate().translationX(-dpToPx(75f)).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
            fabTracker.animate().translationX(-dpToPx(55f)).translationY(-dpToPx(55f)).alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
            fabReport.animate().translationX(0f).translationY(-dpToPx(75f)).alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
        } else {
            fabMain.animate().rotation(0f).setDuration(200).start()
            listOf(fabReport, fabMap, fabTracker).forEach { fab ->
                fab.animate().translationX(0f).translationY(0f).alpha(0f).scaleX(0f).scaleY(0f).setDuration(200)
                    .withEndAction { fab.visibility = View.INVISIBLE }.start()
            }
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
    override fun onDestroy() { super.onDestroy(); map.onDetach() }
}