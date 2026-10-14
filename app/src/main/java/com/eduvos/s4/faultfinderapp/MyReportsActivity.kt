package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Activity displaying the current user's submitted reports, summary metric cards
 * (Total, Open, Fixed), status filtering, and navigation to TrackActivity.
 */
class MyReportsActivity : AppCompatActivity() {

    /**
     * Data model representing a user-submitted fault report for item_report.xml.
     */
    data class UserReport(
        val ref: String,
        val title: String,
        val location: String,
        val dateDisplay: String,
        val status: String,
        val category: String,
        val description: String,
        val lat: Double,
        val lon: Double
    )

    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabLeft: FloatingActionButton
    private lateinit var fabDiag: FloatingActionButton
    private lateinit var fabTop: FloatingActionButton
    private lateinit var listMyReports: ListView
    private lateinit var emptyText: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvOpenCount: TextView
    private lateinit var tvFixedCount: TextView
    private var isFabMenuOpen = false

    // Sample user reports list (will be replaced by Firebase Firestore query for current user)
    private val allUserReports = listOf(
        UserReport(
            ref = "#CT-0001",
            title = "Pothole on Main Rd",
            location = "Table View",
            dateDisplay = "12 Oct",
            status = "Pending",
            category = "Roads",
            description = "Deep pothole in the left lane near the intersection, causing vehicles to swerve.",
            lat = -33.8267,
            lon = 18.4912
        ),
        UserReport(
            ref = "#CT-0002",
            title = "Burst Pipe on Blaauwberg St",
            location = "Table View",
            dateDisplay = "13 Oct",
            status = "In Progress",
            category = "Water",
            description = "Clean water gushing onto the pavement and flooding the storm drain.",
            lat = -33.8075,
            lon = 18.4735
        ),
        UserReport(
            ref = "#CT-0003",
            title = "Streetlight Out",
            location = "Parklands North",
            dateDisplay = "10 Oct",
            status = "Resolved",
            category = "Streetlights",
            description = "Three consecutive streetlights out along the main residential stretch.",
            lat = -33.7965,
            lon = 18.5042
        )
    )

    private var filteredReports: List<UserReport> = allUserReports

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_reports)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Home Reset Rule: Clear backstack and return to Home
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

        listMyReports = findViewById(R.id.listMyReports)
        emptyText = findViewById(R.id.emptyText)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvOpenCount = findViewById(R.id.tvOpenCount)
        tvFixedCount = findViewById(R.id.tvFixedCount)

        updateDashboardCounters()
        setupFilterRadios()
        renderReportList()
        setupSpeedDial()
    }

    /**
     * Calculates and displays Total, Open, and Fixed report counts in the top metric cards.
     */
    private fun updateDashboardCounters() {
        val total = allUserReports.size
        val fixed = allUserReports.count {
            it.status.equals("Resolved", ignoreCase = true) || it.status.equals("Fixed", ignoreCase = true)
        }
        val open = total - fixed

        tvTotalCount.text = total.toString()
        tvOpenCount.text = open.toString()
        tvFixedCount.text = fixed.toString()
    }

    /**
     * Filters the displayed reports when the All, Open, or Fixed radio buttons are toggled.
     */
    private fun setupFilterRadios() {
        val filterGroup = findViewById<RadioGroup>(R.id.filterGroup)
        filterGroup.setOnCheckedChangeListener { _, checkedId ->
            filteredReports = when (checkedId) {
                R.id.rbOpen -> allUserReports.filter {
                    !it.status.equals("Resolved", ignoreCase = true) && !it.status.equals("Fixed", ignoreCase = true)
                }
                R.id.rbFixed -> allUserReports.filter {
                    it.status.equals("Resolved", ignoreCase = true) || it.status.equals("Fixed", ignoreCase = true)
                }
                else -> allUserReports
            }
            renderReportList()
        }
    }

    /**
     * Binds the filtered list of reports to listMyReports using item_report.xml and
     * sets up click navigation to TrackActivity.
     */
    private fun renderReportList() {
        if (filteredReports.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            listMyReports.visibility = View.GONE
            return
        }

        emptyText.visibility = View.GONE
        listMyReports.visibility = View.VISIBLE

        listMyReports.adapter = object : BaseAdapter() {
            override fun getCount(): Int = filteredReports.size
            override fun getItem(position: Int): Any = filteredReports[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: LayoutInflater.from(this@MyReportsActivity)
                    .inflate(R.layout.item_report, parent, false)

                val item = filteredReports[position]
                view.findViewById<TextView>(R.id.tvReportRef).text = item.ref
                view.findViewById<TextView>(R.id.tvReportTitle).text = item.title
                view.findViewById<TextView>(R.id.tvReportLocation).text = "${item.location} • ${item.dateDisplay}"
                view.findViewById<TextView>(R.id.tvReportStatus).text = item.status

                return view
            }
        }

        // Open TrackActivity when any report card is pressed
        listMyReports.setOnItemClickListener { _, _, position, _ ->
            val item = filteredReports[position]
            val intent = Intent(this, TrackActivity::class.java).apply {
                putExtra("EXTRA_REF", item.ref)
                putExtra("EXTRA_TITLE", item.title)
                putExtra("EXTRA_LOCATION", item.location)
                putExtra("EXTRA_STATUS", item.status)
                putExtra("EXTRA_CATEGORY", item.category)
                putExtra("EXTRA_DATE", "${item.dateDisplay} 2026")
                putExtra("EXTRA_DESCRIPTION", item.description)
                putExtra("EXTRA_LAT", item.lat)
                putExtra("EXTRA_LON", item.lon)
            }
            startActivity(intent)
        }
    }

    /**
     * Configures the bottom-right radial speed-dial FAB menu.
     */
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

        // Left -> Map View
        fabLeft.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }

        // Diagonal -> Home Dashboard
        fabDiag.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        // Top -> Report a Fault
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