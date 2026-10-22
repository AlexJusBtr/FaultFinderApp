package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.util.Locale

/**
 * Activity hosting both the Fault Reporting submission form and the live Nearby & History
 * duplicate-check list using a single segmented layout (activity_report.xml).
 */
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
    private var selectedPhotoUri: Uri? = null
    private var takenPhoto: Uri? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private val repository = FirebaseRepository()

    private val defaultLat = -33.8120
    private val defaultLon = 18.4900
    private var allFetchedFaults: List<Fault> = emptyList()
    private var displayedNearbyFaults: List<Fault> = emptyList()

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            showPhotoConfirmation(uri)
        }
    }

    private val takePic = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            takenPhoto?.let { uri ->
                showPhotoConfirmation(uri)
            }
        }
    }

    private val selectLocation = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val latitude = data.getDoubleExtra("SELECTED_LAT", Double.NaN)
            val longitude = data.getDoubleExtra("SELECTED_LON", Double.NaN)

            if (!latitude.isNaN() && !longitude.isNaN()) {
                selectedLatitude = latitude
                selectedLongitude = longitude
                etLocation.setText("Finding address...")

                getAddressFromCoordinates(
                    latitude = latitude,
                    longitude = longitude,
                    onSuccess = { address ->
                        etLocation.setText(address)
                    },
                    onFailure = {
                        etLocation.setText(
                            String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
                        )
                        Toast.makeText(
                            this,
                            "Coordinates captured from map.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_report)

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

        findViewById<ImageButton>(R.id.locationBtn).setOnClickListener {
            val mapIntent = Intent(this, MapActivity::class.java).apply {
                putExtra("PICK_LOCATION", true)
            }
            selectLocation.launch(mapIntent)
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
            showPhotoOptions()
        }

        val settingsPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        cbAnonymous.isChecked = settingsPrefs.getBoolean("default_anonymous", false)

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
        loadLiveNearbyFaults()
    }

    /**
     * Loads live faults from Firestore for the Nearby & History tab and supports
     * toggling between "Near me" (sorted by distance) and "All history" (sorted by date).
     */
    private fun setupNearbyListView() {
        val nearbyToggle = findViewById<RadioGroup>(R.id.nearbyToggle)
        nearbyToggle.setOnCheckedChangeListener { _, _ ->
            renderNearbyList()
        }
        loadLiveNearbyFaults()
    }

    private fun loadLiveNearbyFaults() {
        repository.getAllFaults(
            onSuccess = { faults ->
                allFetchedFaults = faults
                renderNearbyList()
            },
            onFailure = { e ->
                Toast.makeText(this, "Could not load nearby reports: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun renderNearbyList() {
        val listNearby: ListView = findViewById(R.id.listNearby)
        val nearbyToggle = findViewById<RadioGroup>(R.id.nearbyToggle)
        val refLat = selectedLatitude ?: defaultLat
        val refLon = selectedLongitude ?: defaultLon

        displayedNearbyFaults = if (nearbyToggle.checkedRadioButtonId == R.id.rbNearMe) {
            allFetchedFaults.sortedBy { fault ->
                val dist = FloatArray(1)
                Location.distanceBetween(refLat, refLon, fault.latitude, fault.longitude, dist)
                dist[0]
            }
        } else {
            allFetchedFaults.sortedByDescending { it.createdAt }
        }

        val displayRows = displayedNearbyFaults.map { fault ->
            val dist = FloatArray(1)
            Location.distanceBetween(refLat, refLon, fault.latitude, fault.longitude, dist)
            val distStr = if (dist[0] < 1000f) {
                "${dist[0].toInt()}m away"
            } else {
                String.format(Locale.US, "%.1fkm away", dist[0] / 1000f)
            }
            val emoji = getCategoryEmoji(fault.category)
            "$emoji ${fault.title} • ${fault.location} ($distStr • ${fault.status})"
        }

        listNearby.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayRows)

        listNearby.setOnItemClickListener { _, _, position, _ ->
            val fault = displayedNearbyFaults[position]
            val intent = Intent(this, TrackActivity::class.java).apply {
                putExtra("EXTRA_FAULT_ID", fault.id)
                putExtra("EXTRA_REF", fault.ref)
                putExtra("EXTRA_TITLE", fault.title)
                putExtra("EXTRA_LOCATION", fault.location)
                putExtra("EXTRA_STATUS", fault.status)
                putExtra("EXTRA_PRIORITY", fault.priority)
                putExtra("EXTRA_DEPARTMENT", fault.department)
                putExtra("EXTRA_CATEGORY", fault.category)
                putExtra("EXTRA_DATE", fault.date)
                putExtra("EXTRA_DESCRIPTION", fault.description)
                putExtra("EXTRA_IMAGE_URL", fault.imageUrl)
                putExtra("EXTRA_LAT", fault.latitude)
                putExtra("EXTRA_LON", fault.longitude)
                putExtra("EXTRA_CONFIRMATIONS", fault.confirmations)
            }
            startActivity(intent)
        }
    }

    private fun getCategoryEmoji(category: String): String {
        return when (category.trim().lowercase()) {
            "roads" -> "🚧"
            "water" -> "💧"
            "electricity" -> "⚡"
            "streetlights" -> "💡"
            "waste" -> "🗑️"
            else -> "❓"
        }
    }

    fun showPhotoConfirmation(uri: Uri) {
        val previewImage = ImageView(this).apply {
            setImageURI(uri)
            scaleType = ImageView.ScaleType.CENTER_CROP
            val imageWidth = (280 * resources.displayMetrics.density).toInt()
            val imageHeight = (350 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(imageWidth, imageHeight)
        }

        AlertDialog.Builder(this)
            .setTitle("Use this photo?")
            .setView(previewImage)
            .setPositiveButton("Use Photo") { _, _ ->
                selectedPhotoUri = uri
                val imageView = findViewById<ImageView>(R.id.imgFaultPhoto)
                imageView.setImageURI(uri)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.imageTintList = null
                findViewById<TextView>(R.id.tvPhotoLabel).text = "Photo selected"
            }
            .setNegativeButton("Choose Another") { _, _ ->
                showPhotoOptions()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    /**
     * Validates form inputs, resolves coordinates if typed manually, checks for 100m
     * spatial duplicates, and submits the fault report to Firebase.
     */
    private fun setupSubmission() {
        val submitBtn = findViewById<Button>(R.id.submitButton)
        submitBtn.setOnClickListener {
            if (!repository.isLoggedIn()) {
                Toast.makeText(
                    this,
                    "Please log in or sign up to submit a fault report",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(this, AuthActivity::class.java))
                return@setOnClickListener
            }

            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val category = spinnerCategory.selectedItem.toString()
            val anonymous = cbAnonymous.isChecked
            val loc = etLocation.text.toString().trim()

            if (title.isEmpty() || loc.isEmpty()) {
                Toast.makeText(this, "Please enter a title and location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val latitude = selectedLatitude ?: defaultLat
            val longitude = selectedLongitude ?: defaultLon

            submitBtn.isEnabled = false
            submitBtn.text = "Checking for duplicates..."

            repository.checkForDuplicatesBeforeSubmission(
                category = category,
                latitude = latitude,
                longitude = longitude,
                onDuplicatesFound = { duplicates ->
                    runOnUiThread {
                        submitBtn.isEnabled = true
                        submitBtn.text = "Submit Fault Report"
                        val existing = duplicates.first()

                        AlertDialog.Builder(this)
                            .setTitle("Possible Duplicate Report")
                            .setMessage(
                                "An open $category fault (${existing.ref}: \"${existing.title}\") " +
                                        "was already reported within 100m at ${existing.location}.\n\n" +
                                        "Would you like to confirm/upvote the existing report instead?"
                            )
                            .setPositiveButton("Confirm Existing (+1)") { _, _ ->
                                repository.confirmExistingFault(
                                    faultId = existing.id,
                                    onSuccess = {
                                        Toast.makeText(
                                            this,
                                            "Confirmed existing fault ${existing.ref}! Priority boosted.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        finish()
                                    },
                                    onFailure = {
                                        Toast.makeText(this, "Confirmed fault!", Toast.LENGTH_SHORT).show()
                                        finish()
                                    }
                                )
                            }
                            .setNeutralButton("Submit Anyway") { _, _ ->
                                submitFault(
                                    title = title,
                                    category = category,
                                    description = description,
                                    location = loc,
                                    latitude = latitude,
                                    longitude = longitude,
                                    isAnonymous = anonymous
                                )
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                },
                onNoDuplicatesFound = {
                    submitFault(
                        title = title,
                        category = category,
                        description = description,
                        location = loc,
                        latitude = latitude,
                        longitude = longitude,
                        isAnonymous = anonymous
                    )
                },
                onFailure = {
                    // Proceed with submission even if duplicate query fails
                    submitFault(
                        title = title,
                        category = category,
                        description = description,
                        location = loc,
                        latitude = latitude,
                        longitude = longitude,
                        isAnonymous = anonymous
                    )
                }
            )
        }
    }

    private fun submitFault(
        title: String,
        category: String,
        description: String,
        location: String,
        latitude: Double,
        longitude: Double,
        isAnonymous: Boolean
    ) {
        val submitBtn = findViewById<Button>(R.id.submitButton)
        runOnUiThread {
            submitBtn.isEnabled = false
            submitBtn.text = "Submitting Report..."
        }

        repository.createFaultWithImage(
            title = title,
            category = category,
            description = description,
            latitude = latitude,
            longitude = longitude,
            location = location,
            isAnonymous = isAnonymous,
            imageUri = selectedPhotoUri,
            onSuccess = { reference ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Fault reported! Reference: $reference",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            },
            onFailure = { exception ->
                runOnUiThread {
                    submitBtn.isEnabled = true
                    submitBtn.text = "Submit Fault Report"
                    Toast.makeText(
                        this,
                        "Failed to submit fault: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
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

    private fun createImgUri(): Uri? {
        return try {
            val image = File(applicationContext.filesDir, "camera_photo.png")
            FileProvider.getUriForFile(
                applicationContext,
                "com.eduvos.s4.faultfinderapp.fileProvider",
                image
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun openCamera() {
        val uri = createImgUri()
        if (uri == null) {
            Toast.makeText(this, "Opening gallery to select photo...", Toast.LENGTH_SHORT).show()
            pickImage.launch("image/*")
            return
        }
        takenPhoto = uri
        takePic.launch(uri)
    }

    private fun showPhotoOptions() {
        val options = arrayOf("Take a photo", "Choose from gallery")
        AlertDialog.Builder(this)
            .setTitle("Add a photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> pickImage.launch("image/*")
                }
            }
            .show()
    }

    private fun getAddressFromCoordinates(
        latitude: Double,
        longitude: Double,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (!Geocoder.isPresent()) {
            onFailure(Exception("Geocoder is not available on this device"))
            return
        }

        val geocoder = Geocoder(this, Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                runOnUiThread {
                    val address = addresses.firstOrNull()?.getAddressLine(0)
                    if (address != null) {
                        onSuccess(address)
                    } else {
                        onFailure(Exception("No address found"))
                    }
                }
            }
        } else {
            Thread {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val address = addresses?.firstOrNull()?.getAddressLine(0)
                    runOnUiThread {
                        if (address != null) {
                            onSuccess(address)
                        } else {
                            onFailure(Exception("No address found"))
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { onFailure(e) }
                }
            }.start()
        }
    }
}