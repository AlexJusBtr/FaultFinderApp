package com.eduvos.s4.faultfinderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    data class FaultPreview(
        val id: String,
        val ref: String,
        val emoji: String,
        val title: String,
        val location: String,
        val status: String,
        val priority: String,
        val department: String,
        val category: String,
        val dateString: String,
        val description: String,
        val lat: Double,
        val lon: Double,
        val confirmations: Int,
        val imageUrl: String,
        val distanceMeters: Float,
        val distanceText: String
    )

    private lateinit var map: MapView
    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabReport: FloatingActionButton
    private lateinit var fabMap: FloatingActionButton
    private lateinit var fabTracker: FloatingActionButton
    private lateinit var listRecentReports: ListView
    private lateinit var emptyRecentReports: TextView
    private lateinit var btnAdminHeader: MaterialButton

    private val defaultCenter = GeoPoint(-33.8120, 18.4900)
    private var userLocation: Location? = null
    private var hasCenteredOnUser = false
    private val repository = FirebaseRepository()
    private var isFabMenuOpen = false
    private var faultsListener: ListenerRegistration? = null

    private var rawFirestoreFaults = listOf<Fault>()
    private var allLoadedFaults = listOf<FaultPreview>()
    private var recentFaults = listOf<FaultPreview>()

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            updateUserLocation()
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        listRecentReports = findViewById(R.id.listRecentReports)
        emptyRecentReports = findViewById(R.id.emptyRecentReports)
        btnAdminHeader = findViewById(R.id.btnAdminHeader)

        NotificationHelper.createNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setupMap()
        setupSearchBar()
        checkLocationPermissionAndFetch()
        loadFaults()
        setupSpeedDial()
        setupHeaderListeners()
        checkAdminButtonVisibility()
    }

    private fun checkLocationPermissionAndFetch() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            updateUserLocation()
        } else {
            requestLocationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun updateUserLocation() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { locationManager.isProviderEnabled(it) }

            val lastLoc = providers.firstNotNullOfOrNull { locationManager.getLastKnownLocation(it) }
            if (lastLoc != null) {
                userLocation = lastLoc
                recalculateDistancesAndSort()

                if (!hasCenteredOnUser && ::map.isInitialized) {
                    hasCenteredOnUser = true
                    map.controller.animateTo(GeoPoint(lastLoc.latitude, lastLoc.longitude))
                    map.controller.setZoom(15.0)
                }
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    userLocation = location
                    recalculateDistancesAndSort()

                    if (!hasCenteredOnUser && ::map.isInitialized) {
                        hasCenteredOnUser = true
                        map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                        map.controller.setZoom(15.0)
                    }
                    locationManager.removeUpdates(this)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            providers.forEach { locationManager.requestLocationUpdates(it, 1000L, 2f, listener) }
        } catch (_: SecurityException) { }
    }

    private fun checkAdminButtonVisibility() {
        val btnAdmin = findViewById<MaterialButton>(R.id.btnAdminHeader)
        repository.isAdminUser { isAdmin ->
            btnAdmin.visibility = if (isAdmin) View.VISIBLE else View.GONE
        }
        btnAdmin.setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }
    }

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

        findViewById<ImageButton>(R.id.btnRecenter).setOnClickListener {
            val target = userLocation?.let { GeoPoint(it.latitude, it.longitude) } ?: defaultCenter
            map.controller.animateTo(target)
            map.controller.setZoom(15.0)
        }
    }

    private fun refreshMapMarkers() {
        if (!::map.isInitialized) return
        map.overlays.clear()

        val activeFaults = recentFaults.filter { !it.status.equals("Resolved", ignoreCase = true) }
        activeFaults.forEach { fault ->
            val marker = Marker(map).apply {
                position = GeoPoint(fault.lat, fault.lon)
                title = "${fault.title} (${fault.ref})"
                snippet = "${fault.category} • ${fault.distanceText} • ${fault.status}"
                icon = FaultMapMarkerDrawable(this@MainActivity, getPriorityColor(fault.priority), getFaultIcon(fault.category))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
        }
        map.invalidate()
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

    private fun setupSearchBar() {
        findViewById<EditText>(R.id.searchBar).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                recentFaults = if (query.isEmpty()) allLoadedFaults else allLoadedFaults.filter {
                    it.title.lowercase().contains(query) ||
                            it.location.lowercase().contains(query) ||
                            it.category.lowercase().contains(query) ||
                            it.ref.lowercase().contains(query)
                }
                bindRecentReportsList()
                refreshMapMarkers()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadFaults() {
        faultsListener = repository.listenToAllFaults(
            onChange = { faults ->
                val refLat = userLocation?.latitude ?: defaultCenter.latitude
                val refLon = userLocation?.longitude ?: defaultCenter.longitude
                NotificationHelper.checkAndNotifyChanges(
                    context = this,
                    faults = faults,
                    currentUid = FirebaseAuth.getInstance().currentUser?.uid,
                    userLat = refLat,
                    userLon = refLon
                )
                rawFirestoreFaults = faults
                recalculateDistancesAndSort()
            },
            onFailure = { e ->
                Toast.makeText(this, "Failed to load reports: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun recalculateDistancesAndSort() {
        val activeFaults = rawFirestoreFaults.filter { !it.status.equals("Resolved", ignoreCase = true) }
        if (activeFaults.isEmpty()) {
            allLoadedFaults = emptyList()
            recentFaults = emptyList()
            bindRecentReportsList()
            refreshMapMarkers()
            return
        }

        val refLat = userLocation?.latitude ?: defaultCenter.latitude
        val refLon = userLocation?.longitude ?: defaultCenter.longitude
        val dateFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)

        allLoadedFaults = activeFaults.map { fault ->
            val dist = FloatArray(1)
            Location.distanceBetween(refLat, refLon, fault.latitude, fault.longitude, dist)
            val meters = dist[0]
            val distText = if (meters < 1000f) "${meters.toInt()}m away" else String.format(Locale.US, "%.1f km away", meters / 1000f)
            val displayDate = fault.date.ifBlank { dateFormat.format(Date(fault.createdAt)) }

            FaultPreview(
                id = fault.id,
                ref = fault.ref,
                emoji = getFaultEmoji(fault.category),
                title = fault.title,
                location = fault.location,
                status = fault.status,
                priority = fault.priority,
                department = fault.department,
                category = fault.category,
                dateString = displayDate,
                description = fault.description,
                lat = fault.latitude,
                lon = fault.longitude,
                confirmations = fault.confirmations,
                imageUrl = fault.imageUrl,
                distanceMeters = meters,
                distanceText = distText
            )
        }.sortedBy { it.distanceMeters }

        val query = findViewById<EditText>(R.id.searchBar).text.toString().trim().lowercase()
        recentFaults = if (query.isEmpty()) allLoadedFaults else allLoadedFaults.filter {
            it.title.lowercase().contains(query) ||
                    it.location.lowercase().contains(query) ||
                    it.category.lowercase().contains(query) ||
                    it.ref.lowercase().contains(query)
        }

        bindRecentReportsList()
        refreshMapMarkers()
    }

    private fun bindRecentReportsList() {
        if (recentFaults.isEmpty()) {
            emptyRecentReports.visibility = View.VISIBLE
            listRecentReports.visibility = View.GONE
            return
        }

        emptyRecentReports.visibility = View.GONE
        listRecentReports.visibility = View.VISIBLE

        listRecentReports.adapter = object : BaseAdapter() {
            override fun getCount(): Int = recentFaults.size
            override fun getItem(position: Int): Any = recentFaults[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_report, parent, false)
                val fault = recentFaults[position]

                view.findViewById<TextView>(R.id.tvReportRef).text = "${fault.ref} • ${fault.distanceText}"
                view.findViewById<TextView>(R.id.tvReportTitle).text = "${fault.emoji} ${fault.title}"
                view.findViewById<TextView>(R.id.tvReportLocation).text = "${fault.location} • ${fault.dateString}"
                view.findViewById<TextView>(R.id.tvReportStatus).text = fault.status

                val imgThumb = view.findViewById<ImageView>(R.id.reportImg)
                if (fault.imageUrl.isNotBlank()) {
                    Glide.with(this@MainActivity)
                        .load(fault.imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(imgThumb)
                } else {
                    imgThumb.setImageResource(getFaultIcon(fault.category))
                    imgThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                return view
            }
        }

        findViewById<View>(R.id.headerRecentReports).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java).putExtra("OPEN_NEARBY_TAB", true))
        }

        listRecentReports.setOnItemClickListener { _, _, position, _ ->
            val fault = recentFaults[position]
            startActivity(Intent(this, TrackActivity::class.java).apply {
                putExtra("EXTRA_FAULT_ID", fault.id)
                putExtra("EXTRA_REF", fault.ref)
                putExtra("EXTRA_TITLE", fault.title)
                putExtra("EXTRA_LOCATION", fault.location)
                putExtra("EXTRA_STATUS", fault.status)
                putExtra("EXTRA_PRIORITY", fault.priority)
                putExtra("EXTRA_DEPARTMENT", fault.department)
                putExtra("EXTRA_CATEGORY", fault.category)
                putExtra("EXTRA_DATE", fault.dateString)
                putExtra("EXTRA_DESCRIPTION", fault.description)
                putExtra("EXTRA_LAT", fault.lat)
                putExtra("EXTRA_LON", fault.lon)
                putExtra("EXTRA_CONFIRMATIONS", fault.confirmations)
                putExtra("EXTRA_IMAGE_URL", fault.imageUrl)
            })
        }
    }

    private fun getFaultEmoji(category: String): String = when (category.trim().lowercase()) {
        "roads" -> "🚧"
        "water" -> "💧"
        "electricity" -> "⚡"
        "streetlights" -> "💡"
        "waste" -> "🗑️"
        "other" -> "❓"
        else -> "📍"
    }

    private fun setupSpeedDial() {
        fabMain = findViewById(R.id.fabMain)
        fabReport = findViewById(R.id.fabReport)
        fabMap = findViewById(R.id.fabMap)
        fabTracker = findViewById(R.id.fabTracker)

        listOf(fabReport, fabMap, fabTracker).forEach {
            it.alpha = 0f; it.scaleX = 0f; it.scaleY = 0f; it.visibility = View.INVISIBLE
        }

        fabMain.setOnClickListener { toggleSpeedDial() }
        fabReport.setOnClickListener { toggleSpeedDial(); startActivity(Intent(this, ReportActivity::class.java)) }
        fabTracker.setOnClickListener { toggleSpeedDial(); startActivity(Intent(this, MyReportsActivity::class.java)) }
        fabMap.setOnClickListener { toggleSpeedDial(); startActivity(Intent(this, MapActivity::class.java)) }
    }

    private fun setupHeaderListeners() {
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        mapOf(
            R.id.roadsBtn to "Roads",
            R.id.waterBtn to "Water",
            R.id.electricityBtn to "Electricity",
            R.id.lightsBtn to "Streetlights",
            R.id.wasteBtn to "Waste",
            R.id.otherBtn to "Other"
        ).forEach { (viewId, category) ->
            findViewById<TextView>(viewId).setOnClickListener {
                startActivity(Intent(this, ReportActivity::class.java).putExtra("SELECTED_CATEGORY", category))
            }
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

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

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
        checkAdminButtonVisibility()
        updateUserLocation()
    }

    override fun onPause() {
        if (::map.isInitialized) map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        faultsListener?.remove()
        if (::map.isInitialized) map.onDetach()
        super.onDestroy()
    }
}