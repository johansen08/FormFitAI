package com.example.formfit.ui.onBoarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.formfit.R

class SecondOnBoardingActivity : AppCompatActivity() {

    private lateinit var nextActivity2: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_second_on_boarding)

        nextActivity2 = findViewById(R.id.nextActivity2)

        nextActivity2.setOnClickListener {
            startActivity(Intent(this, ThirdOnBoardingActivity::class.java))
            finish()
        }

    }
}