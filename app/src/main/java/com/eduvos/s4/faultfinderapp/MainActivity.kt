package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

/**
 * Home dashboard activity featuring an embedded OpenStreetMap preview, real-time search bar,
 * quick-category reporting buttons, live Firestore recent reports, and speed-dial navigation.
 */
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
        val confirmations: Int
    )

    private lateinit var map: MapView
    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabReport: FloatingActionButton
    private lateinit var fabMap: FloatingActionButton
    private lateinit var fabTracker: FloatingActionButton
    private val defaultCenter = GeoPoint(-33.8120, 18.4900) // Table View / Parklands area center
    private val repository = FirebaseRepository()
    private var isFabMenuOpen = false
    private var faultsListener: ListenerRegistration? = null

    private var allLoadedFaults = listOf<FaultPreview>()
    private var recentFaults = listOf<FaultPreview>()

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

        setupMap()
        setupSearchBar()
        loadFaults()
        setupSpeedDial()
        setupHeaderListeners()
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

        refreshMapMarkers()

        findViewById<ImageButton>(R.id.btnRecenter).setOnClickListener {
            map.controller.animateTo(defaultCenter)
            map.controller.setZoom(14.0)
        }
    }


    private fun refreshMapMarkers() {
        if (!::map.isInitialized) return

        map.overlays.clear()

        recentFaults.forEach { fault ->
            val markerDrawable = FaultMapMarkerDrawable(
                context = this,
                pinColor = getPriorityColor(fault.priority),
                iconResource = getFaultIcon(fault.category)
            )

            val marker = Marker(map).apply {
                position = GeoPoint(fault.lat, fault.lon)
                title = "${fault.title} (${fault.ref})"
                snippet =
                    "${fault.category} • ${fault.status} • ${fault.priority} Priority"

                icon = markerDrawable
                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )
            }

            map.overlays.add(marker)
        }

        map.invalidate()
    }


    /**
     * these 2 functions decide which color and icon to use for each fault based on priority and category.
     * this only applies to the map on main screen.
     * */
    private fun getPriorityColor(priority: String): Int {
        return when (priority.trim().lowercase()) {
            "high" -> Color.rgb(220, 38, 38)
            "medium" -> Color.rgb(245, 158, 11)
            "low" -> Color.rgb(22, 163, 74)
            else -> Color.GRAY
        }
    }

    private fun getFaultIcon(category: String): Int {
        return when (category.trim().lowercase()) {
            "roads" -> R.drawable.ic_fault_roadwork
            "water" -> R.drawable.ic_fault_water
            "electricity" -> R.drawable.ic_fault_electricity
            "streetlights" -> R.drawable.ic_fault_streetlights
            "waste" -> R.drawable.ic_fault_waste
            else -> R.drawable.ic_fault_default
        }
    }


    /**
     * Wires the top search bar to filter recent reports and map markers in real time.
     */
    private fun setupSearchBar() {
        val searchBar = findViewById<EditText>(R.id.searchBar)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                recentFaults = if (query.isEmpty()) {
                    allLoadedFaults
                } else {
                    allLoadedFaults.filter {
                        it.title.lowercase().contains(query) ||
                                it.location.lowercase().contains(query) ||
                                it.category.lowercase().contains(query) ||
                                it.ref.lowercase().contains(query)
                    }
                }
                setupRecentReports()
                refreshMapMarkers()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

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

        findViewById<View>(R.id.headerRecentReports).setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java).apply {
                putExtra("OPEN_NEARBY_TAB", true)
            }
            startActivity(intent)
        }

        recentReportsList.setOnItemClickListener { _, _, position, _ ->
            val fault = recentFaults[position]
            val intent = Intent(this, TrackActivity::class.java).apply {
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
            }
            startActivity(intent)
        }
    }

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

    private fun loadFaults() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        faultsListener = repository.listenToAllFaults(
            onChange = { faults ->
                allLoadedFaults = faults.map { fault ->
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
                        confirmations = fault.confirmations
                    )
                }
                recentFaults = allLoadedFaults
                setupRecentReports()
                refreshMapMarkers()
            },
            onFailure = { exception ->
                Toast.makeText(
                    this,
                    "Failed to load reports: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun getFaultEmoji(category: String): String {
        return when (category.trim().lowercase()) {
            "roads" -> "🚧"
            "water" -> "💧"
            "electricity" -> "⚡"
            "streetlights" -> "💡"
            "waste" -> "🗑️"
            "other" -> "❓"
            else -> "📍"
        }
    }

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
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