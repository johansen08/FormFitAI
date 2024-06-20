package com.example.formfit.ui.onBoarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.formfit.R

class FirstOnBoardingActivity : AppCompatActivity() {

    private lateinit var nextActivity: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_first_on_boarding)

        nextActivity = findViewById(R.id.nextActivity)

        nextActivity.setOnClickListener {
            startActivity(Intent(this, SecondOnBoardingActivity::class.java))
            finish()
        }

    }
}