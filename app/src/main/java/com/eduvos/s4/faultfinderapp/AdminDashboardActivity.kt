package com.eduvos.s4.faultfinderapp

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
    private lateinit var tvEmptyReports: TextView

    // Segmented Tab Controls
    private lateinit var tabActive: LinearLayout
    private lateinit var tabResolved: LinearLayout
    private lateinit var tvTabActive: TextView
    private lateinit var tvTabResolved: TextView
    private lateinit var indicatorTabActive: View
    private lateinit var indicatorTabResolved: View

    private var isAdministrator = false
    private var allDocuments: List<DocumentSnapshot> = emptyList()

    private val TAB_ACTIVE = 0
    private val TAB_RESOLVED = 1
    private var currentTab = TAB_ACTIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (auth.currentUser == null) {
            showLoginRequired()
            return
        }

        setContentView(R.layout.activity_admin_dashboard)

        // Pushes the top bar down below the status bar and notch
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        emailText = findViewById(R.id.tvAdminAccountEmail)
        statsText = findViewById(R.id.tvAdminStatisticsSummary)
        messageText = findViewById(R.id.tvAdminMessage)
        reportsContainer = findViewById(R.id.adminReportsContainer)
        progressBar = findViewById(R.id.adminProgressBar)
        refreshButton = findViewById(R.id.btnAdminRefresh)
        tvEmptyReports = findViewById(R.id.tvEmptyReports)

        tabActive = findViewById(R.id.tabActive)
        tabResolved = findViewById(R.id.tabResolved)
        tvTabActive = findViewById(R.id.tvTabActive)
        tvTabResolved = findViewById(R.id.tvTabResolved)
        indicatorTabActive = findViewById(R.id.indicatorTabActive)
        indicatorTabResolved = findViewById(R.id.indicatorTabResolved)

        emailText.text = auth.currentUser?.email ?: "Administrator"

        setupTabs()

        refreshButton.setOnClickListener {
            if (isAdministrator) loadReports()
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        findViewById<Button>(R.id.btnAdminSignOut).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        verifyAdministrator()
    }

    private fun setupTabs() {
        tabActive.setOnClickListener { switchTab(TAB_ACTIVE) }
        tabResolved.setOnClickListener { switchTab(TAB_RESOLVED) }
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        if (tab == TAB_ACTIVE) {
            tvTabActive.setTextColor(Color.parseColor("#DC2626"))
            tvTabActive.setTypeface(null, Typeface.BOLD)
            indicatorTabActive.setBackgroundColor(Color.parseColor("#DC2626"))

            tvTabResolved.setTextColor(Color.parseColor("#64748B"))
            tvTabResolved.setTypeface(null, Typeface.NORMAL)
            indicatorTabResolved.setBackgroundColor(Color.parseColor("#F1F5F9"))
        } else {
            tvTabResolved.setTextColor(Color.parseColor("#15803D")) // Green for resolved tab
            tvTabResolved.setTypeface(null, Typeface.BOLD)
            indicatorTabResolved.setBackgroundColor(Color.parseColor("#15803D"))

            tvTabActive.setTextColor(Color.parseColor("#64748B"))
            tvTabActive.setTypeface(null, Typeface.NORMAL)
            indicatorTabActive.setBackgroundColor(Color.parseColor("#F1F5F9"))
        }
        renderCurrentTab()
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

                allDocuments = result.documents.sortedByDescending {
                    getTimestampMillis(it, "createdAt")
                }

                val total = allDocuments.size
                val pending = allDocuments.count {
                    it.getString("status").equals("Pending", true) ||
                            it.getString("status").equals("Submitted", true)
                }
                val inProgress = allDocuments.count {
                    it.getString("status").equals("In Progress", true)
                }
                val resolvedCount = allDocuments.count {
                    it.getString("status").equals("Resolved", true)
                }
                val activeCount = total - resolvedCount

                statsText.text = """
                    Total reports: $total
                    Pending / Submitted: $pending
                    In progress: $inProgress
                    Resolved: $resolvedCount
                """.trimIndent()

                tvTabActive.text = "Active ($activeCount)"
                tvTabResolved.text = "Resolved ($resolvedCount)"

                renderCurrentTab()
            }
            .addOnFailureListener { error ->
                progressBar.visibility = View.GONE
                refreshButton.isEnabled = true
                messageText.text =
                    "Could not load reports: " +
                            (error.localizedMessage ?: "Unknown error")
            }
    }

    private fun renderCurrentTab() {
        reportsContainer.removeAllViews()

        val filteredDocs = if (currentTab == TAB_ACTIVE) {
            allDocuments.filter {
                !it.getString("status").equals("Resolved", ignoreCase = true)
            }
        } else {
            allDocuments.filter {
                it.getString("status").equals("Resolved", ignoreCase = true)
            }
        }

        if (filteredDocs.isEmpty()) {
            tvEmptyReports.visibility = View.VISIBLE
            tvEmptyReports.text = if (currentTab == TAB_ACTIVE) {
                "No active faults requiring attention."
            } else {
                "No resolved / fixed faults found in the archive."
            }
            messageText.text = if (currentTab == TAB_ACTIVE) "Showing 0 active report(s)." else "Showing 0 resolved report(s)."
            return
        }

        tvEmptyReports.visibility = View.GONE
        messageText.text = if (currentTab == TAB_ACTIVE) {
            "Showing ${filteredDocs.size} active fault report(s)."
        } else {
            "Showing ${filteredDocs.size} resolved / fixed fault report(s)."
        }

        filteredDocs.forEach { document ->
            addReportCard(document)
        }
    }

    // =========================================================================
    // REPORT CARD BUILDER
    // =========================================================================

    private fun addReportCard(document: DocumentSnapshot) {
        val title = document.getString("title")?.takeIf { it.isNotBlank() } ?: "Untitled Fault"
        val category = document.getString("category") ?: "Other"
        val status = document.getString("status") ?: "Submitted"
        val priority = document.getString("priority") ?: "Medium"
        val reference = document.getString("ref")?.takeIf { it.isNotBlank() } ?: document.id
        val location = document.getString("location") ?: "Cape Town"

        val card = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = dp(1)
            strokeColor = Color.parseColor("#E2E8F0")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Top Row: Reference & Category + Status Badge
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val refView = TextView(this).apply {
            text = "$reference  •  $category"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val statusBadge = createStatusPill(status)

        topRow.addView(refView)
        topRow.addView(statusBadge)
        innerLayout.addView(topRow)

        // Title
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(2))
        }
        innerLayout.addView(titleView)

        // Subtitle & Location
        val locationView = TextView(this).apply {
            text = "📍 $location  •  Submitted ${formatDate(getTimestampMillis(document, "createdAt"))}"
            textSize = 12f
            setTextColor(Color.parseColor("#475569"))
        }
        innerLayout.addView(locationView)

        // Bottom Row: Priority Stripe + Manage Button
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val priorityBadge = createPriorityPill(priority)
        val manageBtn = MaterialButton(this).apply {
            text = "Manage Fault"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#004225"))
            cornerRadius = dp(8)
            insetTop = 0
            insetBottom = 0
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            )
            setOnClickListener { showReportDetails(document) }
        }

        bottomRow.addView(priorityBadge)
        bottomRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        bottomRow.addView(manageBtn)
        innerLayout.addView(bottomRow)

        card.addView(innerLayout)
        reportsContainer.addView(card)
    }

    // =========================================================================
    // REPORT DETAILS DIALOG (Update Status, History, Delete)
    // =========================================================================

    private fun showReportDetails(document: DocumentSnapshot) {
        if (!isAdministrator) return

        val title = document.getString("title")?.takeIf { it.isNotBlank() } ?: "Untitled Fault"
        val description = document.getString("description") ?: "No description provided"
        val category = document.getString("category") ?: "Other"
        val status = document.getString("status") ?: "Unknown"
        val priority = document.getString("priority") ?: "Medium"
        val reference = document.getString("ref")?.takeIf { it.isNotBlank() } ?: document.id
        val department = document.getString("department") ?: "General Municipal Maintenance"
        val location = document.getString("location") ?: "Cape Town"
        val confirmations = document.getLong("confirmations") ?: 1L
        val imageUrl = document.getString("imageUrl") ?: document.getString("imageURL") ?: ""

        val scrollContainer = ScrollView(this).apply {
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (imageUrl.isNotBlank()) {
            val photoView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(140)
                ).apply { bottomMargin = dp(12) }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            Glide.with(this)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(photoView)
            contentLayout.addView(photoView)
        }

        val infoBox = TextView(this).apply {
            text = """
                📌 Reference: $reference
                📁 Category: $category
                ⚡ Priority: $priority
                🏢 Department: $department
                📍 Location: $location
                👥 Confirmations: $confirmations citizen(s)
                
                📝 Description:
                $description
                
                📅 Submitted: ${formatDate(getTimestampMillis(document, "createdAt"))}
                🕒 Last Updated: ${formatDate(getTimestampMillis(document, "updatedAt"))}
            """.trimIndent()
            textSize = 13f
            setTextColor(Color.parseColor("#334155"))
            setLineSpacing(0f, 1.25f)
        }
        contentLayout.addView(infoBox)
        scrollContainer.addView(contentLayout)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollContainer)
            .setPositiveButton("Update Status") { _, _ ->
                showStatusOptions(document)
            }
            .setNeutralButton("Delete Report") { _, _ ->
                confirmDeleteFault(document)
            }
            .setNegativeButton("History") { _, _ ->
                loadStatusHistory(document.id)
            }
            .show()
    }

    private fun confirmDeleteFault(document: DocumentSnapshot) {
        val reference = document.getString("ref") ?: document.id

        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to permanently delete fault $reference? This action cannot be undone.")
            .setPositiveButton("Delete Permanently") { _, _ ->
                progressBar.visibility = View.VISIBLE
                messageText.text = "Deleting report $reference..."

                db.collection("faults")
                    .document(document.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Fault $reference deleted successfully.", Toast.LENGTH_SHORT).show()
                        loadReports()
                    }
                    .addOnFailureListener { error ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Deletion failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStatusOptions(document: DocumentSnapshot) {
        if (!isAdministrator) return

        val statuses = arrayOf(
            "Pending",
            "In Progress",
            "Resolved"
        )

        AlertDialog.Builder(this)
            .setTitle("Select New Resolution Status")
            .setItems(statuses) { _, which ->
                showStatusCommentDialog(document, statuses[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStatusCommentDialog(document: DocumentSnapshot, newStatus: String) {
        val commentInput = EditText(this).apply {
            hint = "e.g., Road team dispatched / Pipe repaired and sealed"
            minLines = 3
            gravity = Gravity.TOP
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            textSize = 13.5f
            setTextColor(Color.parseColor("#1E293B"))
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(TextView(this@AdminDashboardActivity).apply {
                text = "Explain the actions taken for setting status to '$newStatus':"
                textSize = 12.5f
                setTextColor(Color.parseColor("#475569"))
                setPadding(0, 0, 0, dp(8))
            })
            addView(commentInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Status Update: $newStatus")
            .setView(wrapper)
            .setPositiveButton("Save Status") { _, _ ->
                val comment = commentInput.text.toString().trim()
                val finalComment = comment.ifBlank { "Status updated to $newStatus by municipal administrator." }
                updateReportStatus(document, newStatus, finalComment)
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
            "changedBy" to user.uid,
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
                    Toast.makeText(this, "Status updated to '$newStatus'.", Toast.LENGTH_SHORT).show()
                    loadReports()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Status updated.", Toast.LENGTH_SHORT).show()
                    loadReports()
                }
        }.addOnFailureListener { error ->
            Toast.makeText(this, "Could not update status: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadStatusHistory(faultId: String) {
        if (!isAdministrator) return

        progressBar.visibility = View.VISIBLE
        db.collection("faults")
            .document(faultId)
            .collection("statusHistory")
            .get()
            .addOnSuccessListener { result ->
                progressBar.visibility = View.GONE
                if (result.isEmpty) {
                    AlertDialog.Builder(this)
                        .setTitle("Status History")
                        .setMessage("No historical progression entries logged for this fault.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@addOnSuccessListener
                }

                val entries = result.documents.sortedByDescending {
                    getTimestampMillis(it, "timestamp")
                }.joinToString("\n\n") { entry ->
                    val status = entry.getString("status") ?: "Status Update"
                    val comment = entry.getString("comment") ?: "No remark provided"
                    val date = formatDate(getTimestampMillis(entry, "timestamp"))
                    "● [$status] — $date\n   Note: $comment"
                }

                AlertDialog.Builder(this)
                    .setTitle("Audit Timeline")
                    .setMessage(entries)
                    .setPositiveButton("Close", null)
                    .show()
            }
            .addOnFailureListener { error ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Could not fetch audit history: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    // =========================================================================
    // UI HELPERS (PILLS & FORMATTING)
    // =========================================================================

    private fun createStatusPill(status: String): TextView {
        val bgDrawable = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            when (status.trim().lowercase()) {
                "resolved" -> setColor(Color.parseColor("#DCFCE7")) // Light green
                "in progress" -> setColor(Color.parseColor("#FEF3C7")) // Amber
                else -> setColor(Color.parseColor("#F1F5F9")) // Light slate
            }
        }

        return TextView(this).apply {
            text = status
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = bgDrawable
            setTextColor(
                when (status.trim().lowercase()) {
                    "resolved" -> Color.parseColor("#15803D")
                    "in progress" -> Color.parseColor("#B45309")
                    else -> Color.parseColor("#475569")
                }
            )
        }
    }

    private fun createPriorityPill(priority: String): TextView {
        return TextView(this).apply {
            text = "$priority Priority"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(
                when (priority.trim().lowercase()) {
                    "critical", "high" -> Color.parseColor("#DC2626")
                    "medium" -> Color.parseColor("#D97706")
                    else -> Color.parseColor("#16A34A")
                }
            )
        }
    }

    private fun getTimestampMillis(document: DocumentSnapshot, field: String): Long {
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
        if (millis <= 0L) return "Recent"
        return try {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            "Recent"
        }
    }

    private fun showLoginRequired() {
        startActivity(Intent(this, AdminLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}