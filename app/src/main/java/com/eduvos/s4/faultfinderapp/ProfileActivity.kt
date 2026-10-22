package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ProfileActivity : AppCompatActivity() {

    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabLeft: FloatingActionButton
    private lateinit var fabDiag: FloatingActionButton
    private lateinit var fabTop: FloatingActionButton
    private var isFabMenuOpen = false
    private val repository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

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

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.btnAdminPortal).setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }

        refreshProfileState()
        setupSpeedDial()
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

    override fun onResume() {
        super.onResume()
        refreshProfileState()
    }

    private fun refreshProfileState() {
        val tvName = findViewById<TextView>(R.id.tvProfileName)
        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val btnAuthAction = findViewById<MaterialButton>(R.id.btnLogout)
        val btnAdminPortal = findViewById<MaterialButton>(R.id.btnAdminPortal)

        if (repository.isLoggedIn()) {
            repository.getCurrentUser(
                onSuccess = { user ->
                    val badge = if (user.role.equals("admin", true) || user.role.equals("municipal", true)) " (Admin)" else ""
                    tvName.text = (user.name.ifBlank { "Citizen User" }) + badge
                    tvEmail.text = user.email
                },
                onFailure = {
                    tvName.text = "Citizen User"
                    tvEmail.text = "Logged in"
                }
            )

            // Only show the red Admin Portal button if the logged-in account is an Admin
            repository.isAdminUser { isAdmin ->
                btnAdminPortal.visibility = if (isAdmin) View.VISIBLE else View.GONE
            }

            btnAuthAction.text = "Log Out"
            btnAuthAction.setTextColor(Color.parseColor("#EF4444"))
            btnAuthAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            btnAuthAction.iconTint = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            btnAuthAction.strokeColor = ColorStateList.valueOf(Color.parseColor("#FECACA"))

            btnAuthAction.setOnClickListener {
                repository.logoutUser()
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                refreshProfileState()
            }
        } else {
            tvName.text = "Guest Citizen"
            tvEmail.text = "Not logged in"
            btnAdminPortal.visibility = View.GONE

            btnAuthAction.text = "Log In / Sign Up"
            btnAuthAction.setTextColor(Color.WHITE)
            btnAuthAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#004225"))
            btnAuthAction.iconTint = ColorStateList.valueOf(Color.WHITE)
            btnAuthAction.strokeColor = ColorStateList.valueOf(Color.parseColor("#004225"))

            btnAuthAction.setOnClickListener {
                startActivity(Intent(this, AuthActivity::class.java))
            }
        }
    }
}