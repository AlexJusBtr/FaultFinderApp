package com.eduvos.s4.faultfinderapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MyReportsActivity : AppCompatActivity() {

    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabLeft: FloatingActionButton
    private lateinit var fabDiag: FloatingActionButton
    private lateinit var fabTop: FloatingActionButton
    private var isFabMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_reports)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Home Reset Rule: Clear backstack and return to Home
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

        // Map Button Fix: Routes properly and kills current instance
        fabLeft.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }

        // Diag -> Routes directly to Home, clears stack
        fabDiag.setOnClickListener {
            toggleSpeedDial()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        // Top -> Report a Fault
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
}