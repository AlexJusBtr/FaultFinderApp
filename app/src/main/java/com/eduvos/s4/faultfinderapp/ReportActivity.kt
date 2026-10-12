package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ReportActivity : AppCompatActivity() {

    private lateinit var layoutReportForm: LinearLayout
    private lateinit var layoutNearbyList: LinearLayout
    private lateinit var tvTabReport: TextView
    private lateinit var tvTabNearby: TextView
    private lateinit var indicatorTabReport: View
    private lateinit var indicatorTabNearby: View
    private lateinit var etTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var etLocation: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var cbAnonymous: CheckBox

    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabLeft: FloatingActionButton
    private lateinit var fabDiag: FloatingActionButton
    private lateinit var fabTop: FloatingActionButton
    private var isFabMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_report)

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

        layoutReportForm = findViewById(R.id.layoutReportForm)
        layoutNearbyList = findViewById(R.id.layoutNearbyList)
        tvTabReport = findViewById(R.id.tvTabReport)
        tvTabNearby = findViewById(R.id.tvTabNearby)
        indicatorTabReport = findViewById(R.id.indicatorTabReport)
        indicatorTabNearby = findViewById(R.id.indicatorTabNearby)

        etTitle = findViewById(R.id.etTitle)
        etDescription = findViewById(R.id.etDescription)
        etLocation = findViewById(R.id.etLocation)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        cbAnonymous = findViewById(R.id.cbAnonymous)

        val selectedCat = intent.getStringExtra("SELECTED_CATEGORY")
        if (selectedCat != null) {
            val categories = resources.getStringArray(R.array.fault_categories)
            val index = categories.indexOfFirst { it.equals(selectedCat, ignoreCase = true) }
            if (index >= 0) spinnerCategory.setSelection(index)
        }

        setupTabs()
        setupNearbyListView()
        setupSubmission()
        setupSpeedDial()

        if (intent.getBooleanExtra("OPEN_NEARBY_TAB", false)) {
            showNearbyTab()
        }
    }

    private fun setupTabs() {
        findViewById<LinearLayout>(R.id.tabReport).setOnClickListener { showReportTab() }
        findViewById<LinearLayout>(R.id.tabNearby).setOnClickListener { showNearbyTab() }
    }

    private fun showReportTab() {
        layoutReportForm.visibility = View.VISIBLE
        layoutNearbyList.visibility = View.GONE
        tvTabReport.setTextColor(Color.parseColor("#004225"))
        indicatorTabReport.setBackgroundColor(Color.parseColor("#004225"))
        tvTabNearby.setTextColor(Color.parseColor("#888888"))
        indicatorTabNearby.setBackgroundColor(Color.parseColor("#FFF2F6"))
    }

    private fun showNearbyTab() {
        layoutReportForm.visibility = View.GONE
        layoutNearbyList.visibility = View.VISIBLE
        tvTabNearby.setTextColor(Color.parseColor("#004225"))
        indicatorTabNearby.setBackgroundColor(Color.parseColor("#004225"))
        tvTabReport.setTextColor(Color.parseColor("#888888"))
        indicatorTabReport.setBackgroundColor(Color.parseColor("#FFF2F6"))
    }

    private fun setupNearbyListView() {
        val listNearby: ListView = findViewById(R.id.listNearby)
        val mockNearbyData = listOf(
            "🚧 Pothole on Main Rd • Table View (50m away)",
            "💧 Burst Pipe • Blaauwberg St (120m away)",
            "💡 Streetlight Out • Parklands North (250m away)",
            "🗑️ Illegal Dumping • Sandown Rd (400m away)"
        )
        listNearby.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mockNearbyData)
    }

    private fun setupSubmission() {
        findViewById<Button>(R.id.submitButton).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val loc = etLocation.text.toString().trim()

            if (title.isEmpty() || loc.isEmpty()) {
                Toast.makeText(this, "Please enter a title and location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Submitting report to Firebase...", Toast.LENGTH_SHORT).show()
            finish()
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
            startActivity(Intent(this, MyReportsActivity::class.java))
            finish()
        }

        // Top button is home equivalent
        fabTop.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
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