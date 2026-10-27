package com.eduvos.s4.faultfinderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Full-screen interactive OpenStreetMap activity supporting live Firestore fault markers,
 * clean blue-dot GPS user location tracking, automatic filtering of resolved faults,
 * category filtering, and interactive GPS location picking.
 */
class MapActivity : AppCompatActivity() {

    data class MapFaultPin(
        val id: String,
        val ref: String,
        val coord: GeoPoint,
        val title: String,
        val category: String,
        val status: String,
        val priority: String
    )

    private lateinit var map: MapView
    private var locationOverlay: MyLocationNewOverlay? = null
    private val defaultCenter = GeoPoint(-33.8120, 18.4900)
    private val repository = FirebaseRepository()
    private var allMapFaults = listOf<MapFaultPin>()
    private var activeCategoryFilter: String? = null

    private val requestLocationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                centerCurrentLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission is needed to show where you are.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

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

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (intent.hasExtra("FOCUS_LAT") || intent.getBooleanExtra("PICK_LOCATION", false)) {
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
        requestCurrentLocation()
        loadLiveFaultPins()

        if (intent.getBooleanExtra("PICK_LOCATION", false)) {
            Toast.makeText(this, "Tap anywhere on the map to select the fault location", Toast.LENGTH_LONG).show()
        }

        setupCategoryFilters()
        setupFloatingControls()
    }

    private fun setupMap() {
        map = findViewById(R.id.fullMapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.isFlingEnabled = true
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)

        val provider = GpsMyLocationProvider(this).apply {
            addLocationSource(LocationManager.NETWORK_PROVIDER)
        }

        // Custom clean blue dot without default osmdroid arrow icon
        val blueDot = createCleanBlueDot()
        locationOverlay = MyLocationNewOverlay(provider, map).apply {
            enableMyLocation()
            setDirectionArrow(blueDot, blueDot)
        }
        map.overlays.add(locationOverlay)

        val colorMatrix = ColorMatrix().apply { setSaturation(0.15f) }
        map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(colorMatrix))

        if (intent.hasExtra("FOCUS_LAT")) {
            val focusLat = intent.getDoubleExtra("FOCUS_LAT", defaultCenter.latitude)
            val focusLon = intent.getDoubleExtra("FOCUS_LON", defaultCenter.longitude)
            val focusPoint = GeoPoint(focusLat, focusLon)
            map.controller.setZoom(17.0)
            map.controller.setCenter(focusPoint)
        } else {
            map.controller.setZoom(13.0)
            map.controller.setCenter(defaultCenter)
        }
    }

    private fun createCleanBlueDot(): Bitmap {
        val density = resources.displayMetrics.density
        val size = (18 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer white border ring
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Solid blue center dot (removes internal white arrow)
        paint.color = Color.parseColor("#2563EB")
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (2.5f * density), paint)

        return bitmap
    }

    private fun loadLiveFaultPins() {
        repository.getAllFaults(
            onSuccess = { faults ->
                allMapFaults = faults.map { fault ->
                    MapFaultPin(
                        id = fault.id,
                        ref = fault.ref,
                        coord = GeoPoint(fault.latitude, fault.longitude),
                        title = fault.title,
                        category = fault.category,
                        status = fault.status,
                        priority = fault.priority
                    )
                }
                renderMarkers(activeCategoryFilter)
            },
            onFailure = {
                renderMarkers(activeCategoryFilter)
            }
        )
    }

    private fun renderMarkers(categoryFilter: String?) {
        activeCategoryFilter = categoryFilter
        map.overlays.clear()

        // 1. Keep map tap listener at index 0
        setupMapTapListener()

        // 2. Re-add blue dot overlay so category switches don't erase user position
        locationOverlay?.let { map.overlays.add(it) }

        // 3. Filter category and hide resolved faults
        val filteredByCategory = if (categoryFilter == null) {
            allMapFaults
        } else {
            allMapFaults.filter { it.category.equals(categoryFilter, ignoreCase = true) }
        }

        val pinsToShow = filteredByCategory.filter {
            !it.status.equals("Resolved", ignoreCase = true)
        }

        pinsToShow.forEach { pin ->
            val markerDrawable = FaultMapMarkerDrawable(
                context = this,
                pinColor = getPriorityColor(pin.priority),
                iconResource = getFaultIcon(pin.category)
            )

            val marker = Marker(map).apply {
                position = pin.coord
                title = "${pin.title} (${pin.ref})"
                snippet = "${pin.category} • ${pin.status} • ${pin.priority} Priority"
                icon = markerDrawable
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
        }

        // 4. If deep-linked from TrackActivity, ensure focused pin is centered and opened
        if (intent.hasExtra("FOCUS_LAT")) {
            val focusLat = intent.getDoubleExtra("FOCUS_LAT", defaultCenter.latitude)
            val focusLon = intent.getDoubleExtra("FOCUS_LON", defaultCenter.longitude)
            val focusTitle = intent.getStringExtra("FOCUS_TITLE") ?: "Selected Fault"
            val focusStatus = intent.getStringExtra("FOCUS_STATUS") ?: "In Progress"
            val focusedPoint = GeoPoint(focusLat, focusLon)

            val existingMarker = map.overlays
                .filterIsInstance<Marker>()
                .firstOrNull { m ->
                    m.position.latitude == focusedPoint.latitude &&
                            m.position.longitude == focusedPoint.longitude
                }

            if (existingMarker != null) {
                map.controller.animateTo(focusedPoint)
                existingMarker.showInfoWindow()
            } else {
                val focusMarker = Marker(map).apply {
                    position = focusedPoint
                    title = focusTitle
                    snippet = "Status: $focusStatus"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(focusMarker)
                map.controller.animateTo(focusedPoint)
                focusMarker.showInfoWindow()
            }
        }

        map.invalidate()
    }

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
            requestCurrentLocation()
        }

        findViewById<ExtendedFloatingActionButton>(R.id.btnQuickReportHere).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
            finish()
        }
    }

    private fun requestCurrentLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            centerCurrentLocation()
        } else {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun centerCurrentLocation() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).filter { provider ->
                locationManager.isProviderEnabled(provider)
            }

            if (providers.isEmpty()) {
                map.controller.animateTo(defaultCenter)
                map.controller.setZoom(14.0)
                return
            }

            val lastLocation = providers.firstNotNullOfOrNull { provider ->
                locationManager.getLastKnownLocation(provider)
            }

            if (lastLocation != null && !intent.hasExtra("FOCUS_LAT")) {
                map.controller.setZoom(16.0)
                map.controller.animateTo(GeoPoint(lastLocation.latitude, lastLocation.longitude))
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!intent.hasExtra("FOCUS_LAT")) {
                        map.controller.setZoom(16.0)
                        map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                    }
                    locationManager.removeUpdates(this)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            providers.forEach { provider ->
                locationManager.requestLocationUpdates(provider, 1000L, 0f, listener)
            }
        } catch (_: SecurityException) {
            map.controller.animateTo(defaultCenter)
        }
    }

    private fun setupMapTapListener() {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                if (intent.getBooleanExtra("PICK_LOCATION", false)) {
                    Toast.makeText(
                        this@MapActivity,
                        "Selected: ${point.latitude}, ${point.longitude}",
                        Toast.LENGTH_SHORT
                    ).show()

                    val result = Intent().apply {
                        putExtra("SELECTED_LAT", point.latitude)
                        putExtra("SELECTED_LON", point.longitude)
                    }
                    setResult(RESULT_OK, result)
                    finish()
                    return true
                }
                return false
            }

            override fun longPressHelper(point: GeoPoint): Boolean = false
        }

        map.overlays.add(0, MapEventsOverlay(receiver))
    }

    private fun getPriorityColor(priority: String): Int = when (priority.trim().lowercase()) {
        "critical", "high" -> Color.rgb(220, 38, 38)
        "medium" -> Color.rgb(245, 158, 11)
        "low" -> Color.rgb(22, 163, 74)
        else -> Color.GRAY
    }

    private fun getFaultIcon(category: String): Int = when (category.trim().lowercase()) {
        "roads" -> R.drawable.ic_fault_roadwork
        "water" -> R.drawable.ic_fault_water
        "electricity" -> R.drawable.ic_fault_electricity
        "streetlights" -> R.drawable.ic_fault_streetlights
        "waste" -> R.drawable.ic_fault_waste
        else -> R.drawable.ic_fault_default
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        locationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationOverlay?.disableMyLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        map.onDetach()
    }
}