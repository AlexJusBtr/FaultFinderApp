package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.ListenerRegistration
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide


/**
 * Activity responsible for displaying full details of a selected fault report,
 * its assigned municipal department, priority, 4-stage resolution timeline,
 * real-time citizen comments, and deep-linking to the fault's pin on OpenStreetMap.
 */
class TrackActivity : AppCompatActivity() {

    private val repository = FirebaseRepository()
    private var faultId: String = ""
    private var commentsListener: ListenerRegistration? = null
    private var baseDescription: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_track)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }

        populateReportDetails()
        loadFaultPhoto()

        findViewById<Button>(R.id.btnViewOnMap).setOnClickListener {
            val lat = intent.getDoubleExtra("EXTRA_LAT", -33.8120)
            val lon = intent.getDoubleExtra("EXTRA_LON", 18.4900)
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

        findViewById<Button>(R.id.commentBtn).setOnClickListener {
            showAddCommentDialog()
        }
    }

    private fun populateReportDetails() {
        faultId = intent.getStringExtra("EXTRA_FAULT_ID") ?: ""
        val ref = intent.getStringExtra("EXTRA_REF") ?: "#CT-0001"
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Pothole on Main Rd"
        val status = intent.getStringExtra("EXTRA_STATUS") ?: "Pending"
        val priority = intent.getStringExtra("EXTRA_PRIORITY") ?: "Medium"
        val location = intent.getStringExtra("EXTRA_LOCATION") ?: "Main Rd, Table View"
        val date = intent.getStringExtra("EXTRA_DATE") ?: "20 Oct 2026"
        val confirmations = intent.getIntExtra("EXTRA_CONFIRMATIONS", 1)
        baseDescription = intent.getStringExtra("EXTRA_DESCRIPTION")
            ?: "Reported via FaultFinder mobile application. Awaiting municipal field team inspection."
        val category = intent.getStringExtra("EXTRA_CATEGORY") ?: inferCategoryFromTitle(title)
        val department = intent.getStringExtra("EXTRA_DEPARTMENT")
            ?.ifBlank { mapCategoryToDepartment(category) }
            ?: mapCategoryToDepartment(category)

        findViewById<TextView>(R.id.tvDetailRef).text =
            "$ref • $priority Priority • $confirmations Confirmed"
        findViewById<TextView>(R.id.tvDetailTitle).text = title
        findViewById<TextView>(R.id.tvDetailStatus).text = status
        findViewById<TextView>(R.id.tvDetailLocation).text = "📍 $location"
        findViewById<TextView>(R.id.tvDetailDate).text = "📅 $date"
        findViewById<TextView>(R.id.tvDetailDescription).text = baseDescription
        findViewById<TextView>(R.id.deptText).text = department

        updateTimeline(status)

        if (faultId.isNotBlank()) {
            listenForComments(faultId)
        }
    }

    private fun listenForComments(targetFaultId: String) {
        commentsListener = repository.listenToFaultComments(
            faultId = targetFaultId,
            onChange = { comments ->
                if (comments.isNotEmpty()) {
                    val latest = comments.takeLast(2).joinToString("\n") {
                        "💬 ${it.userName}: ${it.message}"
                    }
                    findViewById<TextView>(R.id.tvDetailDescription).text =
                        "$baseDescription\n\n$latest"
                }
            },
            onFailure = {}
        )
    }

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

    private fun updateTimeline(status: String) {
        val step1 = findViewById<TextView>(R.id.step1)
        val step2 = findViewById<TextView>(R.id.step2)
        val step3 = findViewById<TextView>(R.id.step3)
        val step4 = findViewById<TextView>(R.id.step4)

        val green = Color.parseColor("#004225")
        val grey = Color.parseColor("#94A3B8")

        when (status.trim().lowercase()) {
            "submitted", "pending", "open" -> {
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
            "validated" -> {
                step1.text = "✔  Report submitted"
                step1.setTextColor(green)
                step1.setTypeface(null, Typeface.NORMAL)

                step2.text = "●  Verified by City"
                step2.setTextColor(green)
                step2.setTypeface(null, Typeface.BOLD)

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
            else -> { // "Assigned" or "In Progress"
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
        if (!repository.isLoggedIn()) {
            Toast.makeText(this, "Please log in to add a comment", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, AuthActivity::class.java))
            return
        }

        val commentView = layoutInflater.inflate(R.layout.dialog_add_comment, null)
        val commentInput = commentView.findViewById<EditText>(R.id.commentInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Comment")
            .setView(commentView)
            .setPositiveButton("Submit", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val comment = commentInput.text.toString().trim()

                if (comment.isEmpty()) {
                    commentInput.error = "Please enter a comment"
                    return@setOnClickListener
                }

                if (faultId.isBlank()) {
                    Toast.makeText(this, "Comment added: $comment", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setOnClickListener
                }

                repository.addFaultComment(
                    faultId = faultId,
                    message = comment,
                    onSuccess = {
                        Toast.makeText(this, "Comment saved to Firebase!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    },
                    onFailure = { e ->
                        Toast.makeText(
                            this,
                            "Failed to save comment: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        dialog.show()
    }
    private fun loadFaultPhoto() {
        val imageView = findViewById<ImageView>(R.id.imgDetailPhoto)
        val imageUrl = intent.getStringExtra("EXTRA_IMAGE_URL").orEmpty()

        // First try the image URL passed from the previous screen.
        if (imageUrl.isNotBlank()) {
            displayFaultPhoto(imageUrl)
            return
        }

        // If no URL was passed, retrieve it from the Firestore fault record.
        val id = intent.getStringExtra("EXTRA_FAULT_ID").orEmpty()

        if (id.isBlank()) {
            imageView.visibility = View.GONE
            return
        }

        repository.getFaultById(
            faultId = id,
            onSuccess = { fault ->
                runOnUiThread {
                    if (fault.imageUrl.isNotBlank()) {
                        displayFaultPhoto(fault.imageUrl)
                    } else {
                        imageView.visibility = View.GONE
                    }
                }
            },
            onFailure = {
                runOnUiThread {
                    imageView.visibility = View.GONE
                }
            }
        )
    }

    private fun displayFaultPhoto(imageUrl: String) {
        val imageView = findViewById<ImageView>(R.id.imgDetailPhoto)

        imageView.visibility = View.VISIBLE

        Glide.with(this)
            .load(imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .centerCrop()
            .into(imageView)
    }

    override fun onDestroy() {
        commentsListener?.remove()
        super.onDestroy()
    }
}