package com.example.formfit.ui.infoPullup

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.formfit.R
import com.example.formfit.ui.camera.CameraActivity

class InfoPullupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info_pullup)
        val button: Button = findViewById(R.id.button_start_check)
        button.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }
}