package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Activity displaying the current user's submitted reports from Cloud Firestore,
 * summary metric cards (Total, Open, Fixed), status filtering, Glide thumbnails,
 * and CRUD actions (Update/Delete).
 */
class MyReportsActivity : AppCompatActivity() {

    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabLeft: FloatingActionButton
    private lateinit var fabDiag: FloatingActionButton
    private lateinit var fabTop: FloatingActionButton
    private lateinit var listMyReports: ListView
    private lateinit var emptyText: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvOpenCount: TextView
    private lateinit var tvFixedCount: TextView
    private lateinit var filterGroup: RadioGroup
    private var isFabMenuOpen = false

    private val repository = FirebaseRepository()
    private var allUserReports: List<Fault> = emptyList()
    private var filteredReports: List<Fault> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_reports)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
        filterGroup = findViewById(R.id.filterGroup)

        setupFilterRadios()
        setupSpeedDial()
        loadUserReports()
    }

    override fun onResume() {
        super.onResume()
        loadUserReports()
    }

    private fun loadUserReports() {
        if (!repository.isLoggedIn()) {
            allUserReports = emptyList()
            filteredReports = emptyList()
            emptyText.text = "Log in via Profile to view and manage your submitted reports."
            updateDashboardCounters()
            renderReportList()
            return
        }

        repository.getMyFaults(
            onSuccess = { faults ->
                allUserReports = faults
                emptyText.text = "No reports have been made"
                applyCurrentFilter()
                updateDashboardCounters()
            },
            onFailure = { e ->
                Toast.makeText(this, "Error loading reports: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

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

    private fun setupFilterRadios() {
        filterGroup.setOnCheckedChangeListener { _, _ ->
            applyCurrentFilter()
        }
    }

    private fun applyCurrentFilter() {
        filteredReports = when (filterGroup.checkedRadioButtonId) {
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

    private fun renderReportList() {
        val emptyContainer = findViewById<View>(R.id.emptyContainer)
        if (filteredReports.isEmpty()) {
            emptyContainer?.visibility = View.VISIBLE
            listMyReports.visibility = View.GONE
            return
        }

        emptyContainer?.visibility = View.GONE
        listMyReports.visibility = View.VISIBLE

        listMyReports.adapter = object : BaseAdapter() {
            override fun getCount(): Int = filteredReports.size
            override fun getItem(position: Int): Any = filteredReports[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: LayoutInflater.from(this@MyReportsActivity)
                    .inflate(R.layout.item_report, parent, false)

                val item = filteredReports[position]
                view.findViewById<TextView>(R.id.tvReportRef).text = "${item.ref} • ${item.priority} Priority"
                view.findViewById<TextView>(R.id.tvReportTitle).text = item.title
                view.findViewById<TextView>(R.id.tvReportLocation).text = "${item.location} • ${item.date}"
                view.findViewById<TextView>(R.id.tvReportStatus).text = item.status

                val imgThumb = view.findViewById<ImageView>(R.id.reportImg)
                if (item.imageUrl.isNotBlank()) {
                    Glide.with(this@MyReportsActivity)
                        .load(item.imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(imgThumb)
                } else {
                    imgThumb.setImageResource(android.R.drawable.ic_menu_gallery)
                    imgThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }

                return view
            }
        }

        // Single tap opens TrackActivity details with Cloudinary image URL
        listMyReports.setOnItemClickListener { _, _, position, _ ->
            val item = filteredReports[position]
            val intent = Intent(this, TrackActivity::class.java).apply {
                putExtra("EXTRA_FAULT_ID", item.id)
                putExtra("EXTRA_REF", item.ref)
                putExtra("EXTRA_TITLE", item.title)
                putExtra("EXTRA_LOCATION", item.location)
                putExtra("EXTRA_STATUS", item.status)
                putExtra("EXTRA_PRIORITY", item.priority)
                putExtra("EXTRA_DEPARTMENT", item.department)
                putExtra("EXTRA_CATEGORY", item.category)
                putExtra("EXTRA_DATE", item.date)
                putExtra("EXTRA_DESCRIPTION", item.description)
                putExtra("EXTRA_LAT", item.latitude)
                putExtra("EXTRA_LON", item.longitude)
                putExtra("EXTRA_CONFIRMATIONS", item.confirmations)
                putExtra("EXTRA_IMAGE_URL", item.imageUrl)
            }
            startActivity(intent)
        }

        // Long-press provides Update Status and Delete Report options (Full CRUD)
        listMyReports.setOnItemLongClickListener { _, _, position, _ ->
            val item = filteredReports[position]
            val options = arrayOf(
                "Mark as Resolved",
                "Mark as In Progress",
                "Delete Report"
            )
            AlertDialog.Builder(this)
                .setTitle("Manage Report ${item.ref}")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> repository.updateFaultStatus(
                            faultId = item.id,
                            newStatus = "Resolved",
                            comment = "Marked as resolved by reporting citizen",
                            onSuccess = {
                                Toast.makeText(this, "${item.ref} marked as Resolved", Toast.LENGTH_SHORT).show()
                                loadUserReports()
                            },
                            onFailure = { e ->
                                Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        1 -> repository.updateFaultStatus(
                            faultId = item.id,
                            newStatus = "In Progress",
                            comment = "Status updated to In Progress",
                            onSuccess = {
                                Toast.makeText(this, "${item.ref} marked as In Progress", Toast.LENGTH_SHORT).show()
                                loadUserReports()
                            },
                            onFailure = { e ->
                                Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        2 -> repository.deleteFault(
                            faultId = item.id,
                            onSuccess = {
                                Toast.makeText(this, "Report ${item.ref} deleted", Toast.LENGTH_SHORT).show()
                                loadUserReports()
                            },
                            onFailure = { e ->
                                Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                .show()
            true
        }
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
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
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