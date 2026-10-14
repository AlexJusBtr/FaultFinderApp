package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Full-screen interactive OpenStreetMap activity supporting category filtering
 * and deep-linked fault focusing from TrackActivity ("See on Map").
 */
class MapActivity : AppCompatActivity() {

    data class MapFaultPin(
        val ref: String,
        val coord: GeoPoint,
        val title: String,
        val category: String,
        val status: String
    )

    private lateinit var map: MapView
    private val defaultCenter = GeoPoint(-33.8120, 18.4900)

    private val allMapFaults = listOf(
        MapFaultPin("#CT-0001", GeoPoint(-33.8267, 18.4912), "Pothole on Main Rd", "Roads", "Pending"),
        MapFaultPin("#CT-0002", GeoPoint(-33.8075, 18.4735), "Burst Pipe", "Water", "In Progress"),
        MapFaultPin("#CT-0003", GeoPoint(-33.7965, 18.5042), "Streetlight Out", "Streetlights", "Resolved"),
        MapFaultPin("#CT-0004", GeoPoint(-33.8012, 18.5180), "Illegal Dumping", "Waste", "Pending")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        osmConfig.userAgentValue = packageName

        setContentView(R.layout.activity_map)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // If opened from TrackActivity ("See on Map"), simply finish() to go back to the report details;
        // otherwise follow the Home Reset Rule.
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (intent.hasExtra("FOCUS_LAT")) {
                finish()
            } else {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            }
        }

        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }

        setupMap()
        setupCategoryFilters()
        setupFloatingControls()
    }

    /**
     * Initializes the full-screen OpenStreetMap view, renders fault pins, and focuses on
     * a specific pin if launched via "See on Map" from TrackActivity.
     */
    private fun setupMap() {
        map = findViewById(R.id.fullMapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.isFlingEnabled = true
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)

        val colorMatrix = ColorMatrix().apply { setSaturation(0.15f) }
        map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(colorMatrix))

        renderMarkers(null)

        // Check if TrackActivity requested focusing on a specific fault coordinate
        if (intent.hasExtra("FOCUS_LAT")) {
            val focusLat = intent.getDoubleExtra("FOCUS_LAT", defaultCenter.latitude)
            val focusLon = intent.getDoubleExtra("FOCUS_LON", defaultCenter.longitude)
            val focusTitle = intent.getStringExtra("FOCUS_TITLE") ?: "Selected Fault"
            val focusStatus = intent.getStringExtra("FOCUS_STATUS") ?: "In Progress"

            val focusPoint = GeoPoint(focusLat, focusLon)
            val focusMarker = Marker(map).apply {
                position = focusPoint
                title = focusTitle
                snippet = "Status: $focusStatus"
            }
            map.overlays.add(focusMarker)
            focusMarker.showInfoWindow()

            map.controller.setZoom(17.0)
            map.controller.setCenter(focusPoint)
        } else {
            map.controller.setZoom(13.0)
            map.controller.setCenter(defaultCenter)
        }
    }

    /**
     * Renders map markers filtered by category (or all markers when categoryFilter is null).
     */
    private fun renderMarkers(categoryFilter: String?) {
        map.overlays.clear()

        val pinsToShow = if (categoryFilter == null) {
            allMapFaults
        } else {
            allMapFaults.filter { it.category.equals(categoryFilter, ignoreCase = true) }
        }

        pinsToShow.forEach { pin ->
            val marker = Marker(map).apply {
                position = pin.coord
                title = "${pin.title} (${pin.ref})"
                snippet = "${pin.category} • ${pin.status}"
            }
            map.overlays.add(marker)
        }
        map.invalidate()
    }

    /**
     * Wires the horizontal category filter chips at the top of activity_map.xml.
     */
    private fun setupCategoryFilters() {
        val filterButtons = mapOf(
            R.id.filterAll to null,
            R.id.filterRoads to "Roads",
            R.id.filterWater to "Water",
            R.id.filterElectricity to "Electricity",
            R.id.filterLights to "Streetlights",
            R.id.filterWaste to "Waste"
        )

        filterButtons.forEach { (viewId, category) ->
            findViewById<TextView>(viewId).setOnClickListener { clickedView ->
                // Update chip visual selection state
                filterButtons.keys.forEach { id ->
                    val tv = findViewById<TextView>(id)
                    if (id == clickedView.id) {
                        tv.setBackgroundResource(R.drawable.bg_btn_green)
                        tv.setTextColor(Color.WHITE)
                    } else {
                        tv.setBackgroundResource(R.drawable.bg_card)
                        tv.setTextColor(Color.parseColor("#1E293B"))
                    }
                }
                renderMarkers(category)
            }
        }
    }

    private fun setupFloatingControls() {
        findViewById<FloatingActionButton>(R.id.btnMapRecenter).setOnClickListener {
            map.controller.animateTo(defaultCenter)
            map.controller.setZoom(14.0)
        }

        findViewById<ExtendedFloatingActionButton>(R.id.btnQuickReportHere).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
            finish()
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
    override fun onDestroy() { super.onDestroy(); map.onDetach() }
}