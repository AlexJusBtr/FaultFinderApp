package com.eduvos.s4.faultfinderapp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    //map view
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //load osm config so tiles actually load
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        //keep edge to edge padding clean
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //find the map
        map = findViewById(R.id.mapView)

        //map style and pinch zoom
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true) // pinch to zoom

        //map size / zoom level and center spot
        val mapController = map.controller
        mapController.setZoom(14.0) // zoom level
        val startPoint = GeoPoint(-33.9249, 18.4241) // default center (Cape Town)
        mapController.setCenter(startPoint)

        //test pin to see report marker on map
        val marker = Marker(map)
        marker.position = startPoint
        marker.title = "Sample Fault"
        marker.snippet = "Pothole - High Priority"
        map.overlays.add(marker)
    }

    //to resume map when opening app
    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    //to pause map so it doesnt drain battery or crash
    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}