package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton


import android.app.AlertDialog
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class ProfileActivity : AppCompatActivity() {

    private val firebaseRepository = FirebaseRepository()

    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabLeft: FloatingActionButton
    private lateinit var fabDiag: FloatingActionButton
    private lateinit var fabTop: FloatingActionButton
    private var isFabMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

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

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        // just changed the logout button function  so that we can have the reg/login
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        updateAuthButton(btnLogout)

        btnLogout.setOnClickListener {
            if (firebaseRepository.isLoggedIn()) {
                firebaseRepository.logoutUser()
                updateAuthButton(btnLogout)
            } else {
                showAuthDialog()
            }
        }


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

    private fun updateAuthButton(button: Button) {
        button.text = if (firebaseRepository.isLoggedIn()) {
            "Log Out"
        } else {
            "Log In / Register"
        }
    }
    private fun showAuthDialog() {
        val dialogView = layoutInflater.inflate(
            R.layout.dialog_auth,
            null
        )

        val title = dialogView.findViewById<TextView>(R.id.authTitle)
        val nameInput = dialogView.findViewById<EditText>(R.id.authName)
        val emailInput = dialogView.findViewById<EditText>(R.id.authEmail)
        val passwordInput = dialogView.findViewById<EditText>(R.id.authPassword)
        val submitButton = dialogView.findViewById<Button>(R.id.authSubmit)
        val switchText = dialogView.findViewById<TextView>(R.id.authSwitch)

        var isRegisterMode = false

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        fun updateDialogMode() {
            if (isRegisterMode) {
                title.text = "Register"
                nameInput.visibility = View.VISIBLE
                submitButton.text = "Register"
                switchText.text = "Already have an account? Log In"
            } else {
                title.text = "Log In"
                nameInput.visibility = View.GONE
                submitButton.text = "Log In"
                switchText.text = "Don't have an account? Register"
            }
        }

        switchText.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateDialogMode()
        }
        //this controls the authtication  for regester/login
        submitButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val name = nameInput.text.toString().trim()

            if (isRegisterMode) {
                firebaseRepository.registerUser(
                    name,
                    email,
                    password,
                    onSuccess = {
                        runOnUiThread {
                            dialog.dismiss()
                            updateAuthButton(
                                findViewById(R.id.btnLogout)
                            )

                            Toast.makeText(
                                this,
                                "Registration successful!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                error.message ?: "Registration failed.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } else {
                firebaseRepository.loginUser(
                    email,
                    password,
                    onSuccess = {
                        runOnUiThread {
                            dialog.dismiss()
                            updateAuthButton(
                                findViewById(R.id.btnLogout)
                            )

                            Toast.makeText(
                                this,
                                "Login successful!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                error.message ?: "Login failed.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }

        updateDialogMode()
        dialog.show()
    }

}