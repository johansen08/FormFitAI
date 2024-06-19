package com.example.formfit.ui.splashScreen

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import com.example.formfit.MainActivity
import com.example.formfit.R
import com.example.formfit.ui.login.LoginActivity

class SplashScreenActivity : AppCompatActivity() {

    private lateinit var logoSplashScreen: ImageView
    private lateinit var textSplashScreen: TextView
    private lateinit var imageAnim: Animation
    private lateinit var textAnim: Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        logoSplashScreen = findViewById(R.id.spash_screen_logo)
        textSplashScreen = findViewById(R.id.splash_screen_text)

        imageAnim = AnimationUtils.loadAnimation(this, R.anim.imageanim)
        textAnim = AnimationUtils.loadAnimation(this, R.anim.textanim)

        logoSplashScreen.setAnimation(imageAnim)
        textSplashScreen.setAnimation(textAnim)

        val myHandler =  Handler()
        myHandler.postDelayed({
            startActivity(Intent(this@SplashScreenActivity, LoginActivity::class.java))
            finish()
        }, 3000)

    }
}