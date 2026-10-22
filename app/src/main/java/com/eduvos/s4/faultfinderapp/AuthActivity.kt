package com.eduvos.s4.faultfinderapp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Activity handling citizen Login and Sign-Up using FirebaseRepository.
 */
class AuthActivity : AppCompatActivity() {

    private val repository = FirebaseRepository()
    private var isLoginMode = true

    private lateinit var layoutNameField: LinearLayout
    private lateinit var tvTabLogin: TextView
    private lateinit var tvTabRegister: TextView
    private lateinit var indicatorLogin: View
    private lateinit var indicatorRegister: View
    private lateinit var tvAuthSubtitle: TextView
    private lateinit var etAuthName: EditText
    private lateinit var etAuthEmail: EditText
    private lateinit var etAuthPassword: EditText
    private lateinit var btnAuthSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_auth)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        layoutNameField = findViewById(R.id.layoutNameField)
        tvTabLogin = findViewById(R.id.tvTabLogin)
        tvTabRegister = findViewById(R.id.tvTabRegister)
        indicatorLogin = findViewById(R.id.indicatorLogin)
        indicatorRegister = findViewById(R.id.indicatorRegister)
        tvAuthSubtitle = findViewById(R.id.tvAuthSubtitle)
        etAuthName = findViewById(R.id.etAuthName)
        etAuthEmail = findViewById(R.id.etAuthEmail)
        etAuthPassword = findViewById(R.id.etAuthPassword)
        btnAuthSubmit = findViewById(R.id.btnAuthSubmit)

        findViewById<LinearLayout>(R.id.tabLogin).setOnClickListener { setMode(true) }
        findViewById<LinearLayout>(R.id.tabRegister).setOnClickListener { setMode(false) }

        btnAuthSubmit.setOnClickListener { handleAuthSubmit() }
    }

    private fun setMode(login: Boolean) {
        isLoginMode = login
        if (login) {
            layoutNameField.visibility = View.GONE
            btnAuthSubmit.text = "Log In"
            tvAuthSubtitle.text = "Log in to report faults and track maintenance progress."
            tvTabLogin.setTextColor(Color.parseColor("#004225"))
            indicatorLogin.setBackgroundColor(Color.parseColor("#004225"))
            tvTabRegister.setTextColor(Color.parseColor("#888888"))
            indicatorRegister.setBackgroundColor(Color.parseColor("#FFF2F6"))
        } else {
            layoutNameField.visibility = View.VISIBLE
            btnAuthSubmit.text = "Create Account"
            tvAuthSubtitle.text = "Create a citizen account to submit and track municipal faults."
            tvTabRegister.setTextColor(Color.parseColor("#004225"))
            indicatorRegister.setBackgroundColor(Color.parseColor("#004225"))
            tvTabLogin.setTextColor(Color.parseColor("#888888"))
            indicatorLogin.setBackgroundColor(Color.parseColor("#FFF2F6"))
        }
    }

    private fun handleAuthSubmit() {
        val name = etAuthName.text.toString().trim()
        val email = etAuthEmail.text.toString().trim()
        val password = etAuthPassword.text.toString().trim()

        btnAuthSubmit.isEnabled = false
        btnAuthSubmit.text = if (isLoginMode) "Logging in..." else "Creating account..."

        if (isLoginMode) {
            repository.loginUser(
                email = email,
                password = password,
                onSuccess = {
                    Toast.makeText(this, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = { e ->
                    btnAuthSubmit.isEnabled = true
                    btnAuthSubmit.text = "Log In"
                    Toast.makeText(this, e.message ?: "Login failed", Toast.LENGTH_LONG).show()
                }
            )
        } else {
            repository.registerUser(
                name = name,
                email = email,
                password = password,
                onSuccess = {
                    Toast.makeText(this, "Account created! Welcome, ${it.name}", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = { e ->
                    btnAuthSubmit.isEnabled = true
                    btnAuthSubmit.text = "Create Account"
                    Toast.makeText(this, e.message ?: "Sign-up failed", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}