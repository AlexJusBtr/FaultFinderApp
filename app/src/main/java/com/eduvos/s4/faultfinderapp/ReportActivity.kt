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
import android.net.Uri
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Activity hosting both the Fault Reporting submission form and the Nearby & History
 * duplicate-check list using a single segmented layout (activity_report.xml).
 */
class ReportActivity : AppCompatActivity() {

    /**
     * Data structure for items displayed in the Nearby & History tab.
     */
    data class NearbyFaultItem(
        val ref: String,
        val emoji: String,
        val title: String,
        val location: String,
        val distanceText: String,
        val status: String,
        val category: String,
        val date: String,
        val description: String,
        val lat: Double,
        val lon: Double
    )

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
    private var selectedPhotoUri: Uri? = null
    private val repository = FirebaseRepository()

    //for the images
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            showPhotoConfirmation(uri)
        }
    }

    private val nearbyFaults = listOf(
        NearbyFaultItem(
            ref = "#CT-0001",
            emoji = "🚧",
            title = "Pothole on Main Rd",
            location = "Table View",
            distanceText = "50m away",
            status = "Pending",
            category = "Roads",
            date = "12 Oct 2026",
            description = "Deep pothole in the left lane near the intersection, causing vehicles to swerve.",
            lat = -33.8267,
            lon = 18.4912
        ),
        NearbyFaultItem(
            ref = "#CT-0002",
            emoji = "💧",
            title = "Burst Pipe",
            location = "Blaauwberg St",
            distanceText = "120m away",
            status = "In Progress",
            category = "Water",
            date = "13 Oct 2026",
            description = "Clean water gushing onto the pavement and flooding the storm drain.",
            lat = -33.8075,
            lon = 18.4735
        ),
        NearbyFaultItem(
            ref = "#CT-0003",
            emoji = "💡",
            title = "Streetlight Out",
            location = "Parklands North",
            distanceText = "250m away",
            status = "Resolved",
            category = "Streetlights",
            date = "10 Oct 2026",
            description = "Three consecutive streetlights out along the main residential stretch.",
            lat = -33.7965,
            lon = 18.5042
        ),
        NearbyFaultItem(
            ref = "#CT-0004",
            emoji = "🗑️",
            title = "Illegal Dumping",
            location = "Sandown Rd",
            distanceText = "400m away",
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
        findViewById<LinearLayout>(R.id.photoBox).setOnClickListener {
            pickImage.launch("image/*")
        }

        // Pre-check anonymous box if user enabled "Always Report Anonymously" in Settings
        val settingsPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        cbAnonymous.isChecked = settingsPrefs.getBoolean("default_anonymous", false)

        // Pre-select fault category if launched from a Home Quick Report button
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

        // Switch immediately to the Nearby & History tab if requested by MainActivity
        if (intent.getBooleanExtra("OPEN_NEARBY_TAB", false)) {
            showNearbyTab()
        }
    }

    private fun setupTabs() {
        findViewById<LinearLayout>(R.id.tabReport).setOnClickListener { showReportTab() }
        findViewById<LinearLayout>(R.id.tabNearby).setOnClickListener { showNearbyTab() }
    }

    /**
     * Displays the Report Form container and hides the Nearby & History container.
     */
    private fun showReportTab() {
        layoutReportForm.visibility = View.VISIBLE
        layoutNearbyList.visibility = View.GONE
        tvTabReport.setTextColor(Color.parseColor("#004225"))
        indicatorTabReport.setBackgroundColor(Color.parseColor("#004225"))
        tvTabNearby.setTextColor(Color.parseColor("#888888"))
        indicatorTabNearby.setBackgroundColor(Color.parseColor("#FFF2F6"))
    }

    /**
     * Displays the Nearby & History container and hides the Report Form container.
     */
    private fun showNearbyTab() {
        layoutReportForm.visibility = View.GONE
        layoutNearbyList.visibility = View.VISIBLE
        tvTabNearby.setTextColor(Color.parseColor("#004225"))
        indicatorTabNearby.setBackgroundColor(Color.parseColor("#004225"))
        tvTabReport.setTextColor(Color.parseColor("#888888"))
        indicatorTabReport.setBackgroundColor(Color.parseColor("#FFF2F6"))
    }

    /**
     * Populates the Nearby & History list and opens TrackActivity when any item is tapped.
     */
    private fun setupNearbyListView() {
        val listNearby: ListView = findViewById(R.id.listNearby)
        val displayRows = nearbyFaults.map {
            "${it.emoji} ${it.title} • ${it.location} (${it.distanceText})"
        }
        listNearby.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayRows)

        listNearby.setOnItemClickListener { _, _, position, _ ->
            val fault = nearbyFaults[position]
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
    fun showPhotoConfirmation(uri: Uri) {
        // Creates an image preview.
        val previewImage = ImageView(this).apply {
            setImageURI(uri)
            scaleType = ImageView.ScaleType.CENTER_CROP
            //adjustments to the image confirm scale is here
            val imageWidth = (280 * resources.displayMetrics.density).toInt()
            val imageHeight = (350 * resources.displayMetrics.density).toInt()

            layoutParams = LinearLayout.LayoutParams(
                imageWidth,
                imageHeight
            )
        }

        // Asks the user if they want to use this image.
        AlertDialog.Builder(this)
            .setTitle("Use this photo?")
            .setView(previewImage)

            // Save and display image after confirmation.
            .setPositiveButton("Use Photo") { _, _ ->
                selectedPhotoUri = uri

                val imageView =
                    findViewById<ImageView>(R.id.imgFaultPhoto)

                imageView.setImageURI(uri)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.imageTintList = null

                findViewById<TextView>(R.id.tvPhotoLabel).text =
                    "Photo selected"
            }

            // lets the user select another image.
            .setNegativeButton("Choose Another") { _, _ ->
                pickImage.launch("image/*")
            }

            .setNeutralButton("Cancel", null)
            .show()
    }

    /**
     * Validates form inputs before submitting the fault report.
     */
    private fun setupSubmission() {
        findViewById<Button>(R.id.submitButton).setOnClickListener {
            if (!repository.isLoggedIn()) {
                Toast.makeText(this, "Please log in or sign up to submit a fault report", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, AuthActivity::class.java))
                return@setOnClickListener
            }
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