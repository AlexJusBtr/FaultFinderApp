package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AlertDialog

/**
 * Activity responsible for displaying full details of a selected fault report,
 * its assigned municipal department, its 4-stage resolution timeline, and
 * deep-linking to the fault's pin on the OpenStreetMap view.
 */
class TrackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_track)

        // Apply system window insets so UI elements do not overlap status or navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Back button returns to whichever list opened this report (Home, Nearby, or My Reports)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Profile icon navigates to ProfileActivity
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }

        // Populate all UI fields and timeline steps from Intent extras
        populateReportDetails()

        // Launch MapActivity centered on this specific fault's GPS coordinates
        findViewById<Button>(R.id.btnViewOnMap).setOnClickListener {
            val lat = intent.getDoubleExtra("EXTRA_LAT", -33.9249)
            val lon = intent.getDoubleExtra("EXTRA_LON", 18.4241)
            val title = intent.getStringExtra("EXTRA_TITLE") ?: "Pothole on Main Rd"
            val status = intent.getStringExtra("EXTRA_STATUS") ?: "In Progress"
            val ref = intent.getStringExtra("EXTRA_REF") ?: "#CT-0001"

            val mapIntent = Intent(this, MapActivity::class.java).apply {
                putExtra("FOCUS_LAT", lat)
                putExtra("FOCUS_LON", lon)
                putExtra("FOCUS_TITLE", "$title ($ref)")
                putExtra("FOCUS_STATUS", status)
            }
            startActivity(mapIntent)
        }

        // Placeholder feedback for adding a comment until Firebase Firestore comments are wired
        findViewById<Button>(R.id.commentBtn).setOnClickListener {
            showAddCommentDialog()
        }
    }

    /**
     * Reads report attributes from the launching Intent and binds them to the layout views.
     */
    private fun populateReportDetails() {
        val ref = intent.getStringExtra("EXTRA_REF") ?: "#CT-0001"
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Pothole on Main Rd"
        val status = intent.getStringExtra("EXTRA_STATUS") ?: "In Progress"
        val location = intent.getStringExtra("EXTRA_LOCATION") ?: "Main Rd, Table View"
        val date = intent.getStringExtra("EXTRA_DATE") ?: "14 Oct 2026"
        val description = intent.getStringExtra("EXTRA_DESCRIPTION")
            ?: "Reported via FaultFinder mobile application. Awaiting municipal field team inspection."
        val category = intent.getStringExtra("EXTRA_CATEGORY") ?: inferCategoryFromTitle(title)

        findViewById<TextView>(R.id.tvDetailRef).text = ref
        findViewById<TextView>(R.id.tvDetailTitle).text = title
        findViewById<TextView>(R.id.tvDetailStatus).text = status
        findViewById<TextView>(R.id.tvDetailLocation).text = "📍 $location"
        findViewById<TextView>(R.id.tvDetailDate).text = "📅 $date"
        findViewById<TextView>(R.id.tvDetailDescription).text = description
        findViewById<TextView>(R.id.deptText).text = mapCategoryToDepartment(category)

        updateTimeline(status)
    }

    /**
     * Infers a fault category from the title if no explicit category extra was provided.
     */
    private fun inferCategoryFromTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            "pothole" in lower || "road" in lower -> "Roads"
            "pipe" in lower || "water" in lower || "leak" in lower -> "Water"
            "light" in lower -> "Streetlights"
            "power" in lower || "elec" in lower -> "Electricity"
            "dump" in lower || "waste" in lower || "bin" in lower -> "Waste"
            else -> "Other"
        }
    }

    /**
     * Maps a fault category to the responsible City of Cape Town municipal department.
     */
    private fun mapCategoryToDepartment(category: String): String {
        return when (category.lowercase()) {
            "roads" -> "Roads & Stormwater"
            "water" -> "Water & Sanitation"
            "electricity" -> "Energy & Electricity"
            "streetlights" -> "Public Lighting & Electrical"
            "waste" -> "Solid Waste Management"
            else -> "General Municipal Maintenance"
        }
    }

    /**
     * Updates the 4-step progress indicator colors, icons, and text styles based on fault status.
     */
    private fun updateTimeline(status: String) {
        val step1 = findViewById<TextView>(R.id.step1)
        val step2 = findViewById<TextView>(R.id.step2)
        val step3 = findViewById<TextView>(R.id.step3)
        val step4 = findViewById<TextView>(R.id.step4)

        val green = Color.parseColor("#004225")
        val grey = Color.parseColor("#94A3B8")

        when (status.lowercase()) {
            "pending", "open" -> {
                step1.text = "●  Report submitted"
                step1.setTextColor(green)
                step1.setTypeface(null, Typeface.BOLD)

                step2.text = "○  Verified by City"
                step2.setTextColor(grey)
                step2.setTypeface(null, Typeface.NORMAL)

                step3.text = "○  Team assigned"
                step3.setTextColor(grey)
                step3.setTypeface(null, Typeface.NORMAL)

                step4.text = "○  Fixed"
                step4.setTextColor(grey)
                step4.setTypeface(null, Typeface.NORMAL)
            }
            "resolved", "fixed" -> {
                step1.text = "✔  Report submitted"
                step1.setTextColor(green)
                step1.setTypeface(null, Typeface.NORMAL)

                step2.text = "✔  Verified by City"
                step2.setTextColor(green)
                step2.setTypeface(null, Typeface.NORMAL)

                step3.text = "✔  Team assigned"
                step3.setTextColor(green)
                step3.setTypeface(null, Typeface.NORMAL)

                step4.text = "✔  Fixed"
                step4.setTextColor(green)
                step4.setTypeface(null, Typeface.BOLD)
            }
            else -> { // Default: "In Progress"
                step1.text = "✔  Report submitted"
                step1.setTextColor(green)
                step1.setTypeface(null, Typeface.NORMAL)

                step2.text = "✔  Verified by City"
                step2.setTextColor(green)
                step2.setTypeface(null, Typeface.NORMAL)

                step3.text = "●  Team assigned"
                step3.setTextColor(green)
                step3.setTypeface(null, Typeface.BOLD)

                step4.text = "○  Fixed"
                step4.setTextColor(grey)
                step4.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun showAddCommentDialog() {
        val commentView = layoutInflater.inflate(
            R.layout.dialog_add_comment,
            null
        )
        val commentInput = commentView.findViewById<android.widget.EditText>(
            R.id.commentInput
        )


        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Comment")
            .setView(commentView)
            .setPositiveButton("Submit", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val comment = commentInput.text.toString().trim()

                    if (comment.isEmpty()) {
                        commentInput.error = "Please enter a comment"
                    } else {
                        Toast.makeText(
                            this,
                            "Comment entered successfully. Saving to Firebase is next.",
                            Toast.LENGTH_SHORT
                        ).show()

                        dialog.dismiss()
                    }
                }
        }

        dialog.show()
    }

}