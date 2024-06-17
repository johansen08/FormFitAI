package com.example.formfit.ui.infoPushup

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.formfit.R
import com.example.formfit.ui.camera.pushup.PushupCameraActivity

class InfoPushupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info_pushup)
        val button: Button = findViewById(R.id.button_start_check)
        button.setOnClickListener {
            val intent = Intent(this, PushupCameraActivity::class.java)
            startActivity(intent)
        }
    }

}