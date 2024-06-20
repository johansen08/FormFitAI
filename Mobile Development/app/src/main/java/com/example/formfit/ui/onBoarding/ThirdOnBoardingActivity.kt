package com.example.formfit.ui.onBoarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.formfit.MainActivity
import com.example.formfit.R
import com.example.formfit.ui.login.LoginActivity

class ThirdOnBoardingActivity : AppCompatActivity() {

    private lateinit var nextActivity3: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_third_on_boarding)

        nextActivity3 = findViewById(R.id.nextActivity3)

        nextActivity3.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

    }
}