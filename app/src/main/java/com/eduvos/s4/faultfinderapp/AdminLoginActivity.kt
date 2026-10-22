package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class AdminLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        auth = FirebaseAuth.getInstance()

        val emailInput = findViewById<EditText>(R.id.etAdminEmail)
        val passwordInput = findViewById<EditText>(R.id.etAdminPassword)
        val loginButton = findViewById<Button>(R.id.btnAdminLogin)
        val message = findViewById<TextView>(R.id.tvAdminLoginMessage)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isBlank() || password.isBlank()) {
                message.text = "Enter your email and password."
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            message.text = "Signing in"

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    loginButton.isEnabled = true

                    if (task.isSuccessful) {

                        startActivity(
                            Intent(this, AdminDashboardActivity::class.java)
                        )
                        finish()
                    } else {
                        message.text =
                            task.exception?.localizedMessage
                                ?: "Login failed. Please try again."
                    }
                }
        }
    }
}
