package com.example.formfit.ui.feedback

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.formfit.R

class FeedbackActivity : AppCompatActivity() {

    private lateinit var feedbackTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        feedbackTextView = findViewById(R.id.feedbackTextView)

        // Ambil teks dari SharedPreferences
        val sharedPref: SharedPreferences = getSharedPreferences(
            "feedback_prefs",
            Context.MODE_PRIVATE
        )
        val feedbackText = sharedPref.getString("feedback_text", "") ?: ""

        // Tampilkan teks di TextView
        feedbackTextView.text = feedbackText
    }
    override fun onResume() {
        super.onResume()

        // Hapus teks dari SharedPreferences setelah selesai menampilkannya
        val sharedPref: SharedPreferences = getSharedPreferences(
            "feedback_prefs",
            Context.MODE_PRIVATE
        )
        sharedPref.edit().remove("feedback_text").apply()
    }
}
