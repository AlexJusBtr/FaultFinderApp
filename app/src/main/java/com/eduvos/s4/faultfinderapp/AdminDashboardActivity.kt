package com.eduvos.s4.faultfinderapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var emailText: TextView
    private lateinit var statsText: TextView
    private lateinit var messageText: TextView
    private lateinit var reportsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var refreshButton: Button

    private var isAdministrator = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            showLoginRequired()
            return
        }

        setContentView(R.layout.activity_admin_dashboard)

        emailText = findViewById(R.id.tvAdminAccountEmail)
        statsText = findViewById(R.id.tvAdminStatisticsSummary)
        messageText = findViewById(R.id.tvAdminMessage)
        reportsContainer = findViewById(R.id.adminReportsContainer)
        progressBar = findViewById(R.id.adminProgressBar)
        refreshButton = findViewById(R.id.btnAdminRefresh)

        emailText.text = auth.currentUser?.email ?: "Administrator"

        refreshButton.setOnClickListener {
            if (isAdministrator) {
                loadReports()
            }
        }

        findViewById<Button>(R.id.btnAdminSignOut).setOnClickListener {
            auth.signOut()
            showLoginRequired()
        }

        verifyAdministrator()
    }

    private fun verifyAdministrator() {
        val user = auth.currentUser

        if (user == null) {
            showLoginRequired()
            return
        }

        progressBar.visibility = View.VISIBLE
        messageText.text = "Verifying administrator permissions..."

        // Force-refresh the token so the newly assigned custom claim is checked.
        user.getIdToken(true)
            .addOnSuccessListener { result ->
                val hasAdminClaim = result.claims["admin"] == true

                if (!hasAdminClaim) {
                    isAdministrator = false
                    auth.signOut()

                    Toast.makeText(
                        this,
                        "Access denied. Administrator permission is required.",
                        Toast.LENGTH_LONG
                    ).show()

                    showLoginRequired()
                    return@addOnSuccessListener
                }

                isAdministrator = true
                messageText.text = "Administrator access verified."
                loadReports()
            }
            .addOnFailureListener { error ->
                progressBar.visibility = View.GONE
                messageText.text =
                    "Could not verify administrator access: " +
                            (error.localizedMessage ?: "Unknown error")
            }
    }

    private fun loadReports() {
        if (!isAdministrator) return

        progressBar.visibility = View.VISIBLE
        refreshButton.isEnabled = false
        messageText.text = "Loading fault reports..."

        db.collection("faults")
            .get()
            .addOnSuccessListener { result ->
                progressBar.visibility = View.GONE
                refreshButton.isEnabled = true
                reportsContainer.removeAllViews()

                val documents = result.documents.sortedByDescending {
                    getTimestampMillis(it, "createdAt")
                }

                val total = documents.size
                val pending = documents.count {
                    it.getString("status").equals("Pending", true) ||
                            it.getString("status").equals("Submitted", true)
                }
                val inProgress = documents.count {
                    it.getString("status").equals("In Progress", true)
                }
                val resolved = documents.count {
                    it.getString("status").equals("Resolved", true)
                }

                statsText.text = """
                    Total reports: $total
                    Pending / Submitted: $pending
                    In progress: $inProgress
                    Resolved: $resolved
                """.trimIndent()

                if (documents.isEmpty()) {
                    messageText.text = "No fault reports were found."
                    return@addOnSuccessListener
                }

                messageText.text = "Showing $total fault report(s)."

                documents.forEach { document ->
                    addReportCard(document)
                }
            }
            .addOnFailureListener { error ->
                progressBar.visibility = View.GONE
                refreshButton.isEnabled = true
                messageText.text =
                    "Could not load reports: " +
                            (error.localizedMessage ?: "Unknown error")
            }
    }

    private fun addReportCard(document: DocumentSnapshot) {
        val title = document.getString("title")
            ?.takeIf { it.isNotBlank() } ?: "Untitled fault"

        val category = document.getString("category") ?: "Other"
        val status = document.getString("status") ?: "Unknown"
        val priority = document.getString("priority") ?: "Unassigned"
        val reference = document.getString("ref")
            ?.takeIf { it.isNotBlank() } ?: document.id

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_card)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(0xFF172033.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val detailsView = TextView(this).apply {
            text = """
                Reference: $reference
                Category: $category
                Status: $status
                Priority: $priority
                Submitted: ${formatDate(getTimestampMillis(document, "createdAt"))}
            """.trimIndent()
            textSize = 14f
            setTextColor(0xFF394150.toInt())
            setPadding(0, dp(8), 0, dp(10))
        }

        val detailsButton = Button(this).apply {
            text = "View details / Manage"
            setOnClickListener { showReportDetails(document) }
        }

        card.addView(titleView)
        card.addView(detailsView)
        card.addView(detailsButton)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }

        reportsContainer.addView(card, params)
    }

    private fun showReportDetails(document: DocumentSnapshot) {
        if (!isAdministrator) return

        val title = document.getString("title") ?: "Untitled fault"
        val description = document.getString("description") ?: "No description"
        val category = document.getString("category") ?: "Other"
        val status = document.getString("status") ?: "Unknown"
        val priority = document.getString("priority") ?: "Unassigned"
        val reference = document.getString("ref")?.takeIf {
            it.isNotBlank()
        } ?: document.id
        val department = document.getString("department") ?: "Not assigned"
        val location = document.getString("location") ?: "Not provided"
        val userId = document.getString("userID")
            ?: document.getString("userId")
            ?: "Not available"
        val imageUrl = document.getString("imageURL")
            ?: document.getString("imageUrl")
            ?: ""

        val details = """
            Reference: $reference
            Title: $title
            Category: $category
            Description: $description

            Status: $status
            Priority: $priority
            Department: $department
            Location: $location

            Submitted: ${formatDate(getTimestampMillis(document, "createdAt"))}
            Updated: ${formatDate(getTimestampMillis(document, "updatedAt"))}
            Anonymous: ${document.getBoolean("anonymous")
            ?: document.getBoolean("isAnonymous") ?: false}
            Citizen UID: $userId
            Report ID: ${document.id}
            Image: ${if (imageUrl.isBlank()) "No image attached" else imageUrl}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(details)
            .setPositiveButton("Update status") { _, _ ->
                showStatusOptions(document)
            }
            .setNeutralButton("View status history") { _, _ ->
                loadStatusHistory(document.id)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showStatusOptions(document: DocumentSnapshot) {
        if (!isAdministrator) return

        val statuses = arrayOf(
            "Submitted",
            "Pending",
            "In Progress",
            "Resolved"
        )

        AlertDialog.Builder(this)
            .setTitle("Select new status")
            .setItems(statuses) { _, which ->
                showStatusCommentDialog(document, statuses[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStatusCommentDialog(
        document: DocumentSnapshot,
        newStatus: String
    ) {
        val commentInput = EditText(this).apply {
            hint = "Enter a status update comment"
            minLines = 2
            gravity = Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(
                commentInput,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Update to $newStatus")
            .setView(wrapper)
            .setPositiveButton("Save") { _, _ ->
                val comment = commentInput.text.toString().trim()

                if (comment.isBlank()) {
                    Toast.makeText(
                        this,
                        "Enter a comment explaining the status change.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    updateReportStatus(document, newStatus, comment)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateReportStatus(
        document: DocumentSnapshot,
        newStatus: String,
        comment: String
    ) {
        if (!isAdministrator) return

        val user = auth.currentUser ?: return
        val now = System.currentTimeMillis()

        val historyEntry = hashMapOf(
            "changeBy" to user.uid,
            "comment" to comment,
            "status" to newStatus,
            "timestamp" to now
        )

        val reportReference = db.collection("faults").document(document.id)

        reportReference.update(
            mapOf(
                "status" to newStatus,
                "updatedAt" to now
            )
        ).addOnSuccessListener {
            reportReference.collection("statusHistory")
                .add(historyEntry)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "Report status updated.",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadReports()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(
                        this,
                        "Status changed, but history could not be saved: " +
                                (error.localizedMessage ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                    loadReports()
                }
        }.addOnFailureListener { error ->
            Toast.makeText(
                this,
                "Could not update status: " +
                        (error.localizedMessage ?: "Unknown error"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadStatusHistory(faultId: String) {
        if (!isAdministrator) return

        db.collection("faults")
            .document(faultId)
            .collection("statusHistory")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    AlertDialog.Builder(this)
                        .setTitle("Status history")
                        .setMessage("No status history entries were found.")
                        .setPositiveButton("Close", null)
                        .show()
                    return@addOnSuccessListener
                }

                val entries = result.documents.sortedByDescending {
                    getTimestampMillis(it, "timestamp")
                }.map { entry ->
                    val status = entry.getString("status") ?: "Unknown"
                    val comment = entry.getString("comment") ?: ""
                    val changedBy = entry.getString("changeBy") ?: "Unknown"
                    val timestamp = getTimestampMillis(entry, "timestamp")

                    """
                    Status: $status
                    Comment: $comment
                    Changed by UID: $changedBy
                    Date: ${formatDate(timestamp)}
                    """.trimIndent()
                }

                AlertDialog.Builder(this)
                    .setTitle("Status history")
                    .setMessage(entries.joinToString("\n\n────────────\n\n"))
                    .setPositiveButton("Close", null)
                    .show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Could not load status history: " +
                            (error.localizedMessage ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun getTimestampMillis(
        document: DocumentSnapshot,
        field: String
    ): Long {
        return when (val value = document.get(field)) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Timestamp -> value.toDate().time
            is Date -> value.time
            else -> 0L
        }
    }

    private fun formatDate(millis: Long): String {
        if (millis <= 0L) return "Not available"

        return try {
            SimpleDateFormat(
                "dd MMM yyyy, HH:mm",
                Locale.getDefault()
            ).format(Date(millis))
        } catch (_: Exception) {
            "Not available"
        }
    }

    private fun showLoginRequired() {
        val intent = android.content.Intent(
            this,
            AdminLoginActivity::class.java
        ).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
