package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.widget.ImageButton
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

class MapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private val defaultCenter = GeoPoint(-33.9249, 18.4241)

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

        // Home Reset Rule
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }

        setupMap()
        setupFloatingControls()
    }

    private fun setupMap() {
        map = findViewById(R.id.fullMapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.isFlingEnabled = true
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)

        val colorMatrix = ColorMatrix().apply { setSaturation(0.15f) }
        map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(colorMatrix))

        map.controller.setZoom(14.0)
        map.controller.setCenter(defaultCenter)

        val sampleFaults = listOf(
            Triple(GeoPoint(-33.9249, 18.4241), "Pothole on Main Rd", "Roads • High Priority"),
            Triple(GeoPoint(-33.9280, 18.4320), "Burst Pipe", "Water • In Progress"),
            Triple(GeoPoint(-33.9180, 18.4190), "Streetlight Out", "Streetlights • Pending")
        )

        sampleFaults.forEach { (coord, faultTitle, faultSnippet) ->
            val marker = Marker(map).apply {
                position = coord
                title = faultTitle
                snippet = faultSnippet
            }
            map.overlays.add(marker)
        }
    }

    private fun setupFloatingControls() {
        findViewById<FloatingActionButton>(R.id.btnMapRecenter).setOnClickListener {
            map.controller.animateTo(defaultCenter)
            map.controller.setZoom(15.5)
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