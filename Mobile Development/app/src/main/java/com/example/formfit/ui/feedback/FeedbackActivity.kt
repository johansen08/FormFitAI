package com.example.formfit.ui.feedback

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.formfit.R

class FeedbackActivity : AppCompatActivity() {

    private lateinit var feedbackTextView: TextView
    private lateinit var resultsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        resultsTextView = findViewById(R.id.resultsTextView)

        // Retrieve repetition results from Intent
        val repetitionResults = intent.getSerializableExtra("repetitionResults") as? Array<FloatArray>
        if (repetitionResults != null) {
            // Format the repetition results
            val resultsText = repetitionResults.mapIndexed { index, array ->
                val hipStatus = if (array[0] >= 165) "Benar" else "Salah"
                val handStatus = if (array[1] <= 1.3) "Benar" else "Salah"
                val headStatus = if (array[2] >= 140) "Benar" else "Salah"
                "Repetisi ${index + 1}:\nPinggang: $hipStatus | Tangan: $handStatus | Kepala: $headStatus"
            }.joinToString(separator = "\n\n")

            val feedbackText = repetitionResults.mapIndexed { index, array ->
                val hipFeedback = if (array[0] < 165) "Posisi Pinggang tidak lurus, sudut antara titik bahu, pinggang dan lutut ${array[0]}" else ""
                val handFeedback = if (array[1] > 1.3) "Posisi Tangan tidak benar" else ""
                val headFeedback = if (array[2] < 140) "Posisi Kepala tidak benar" else ""
                "Repetisi ${index + 1} Feedback: ${listOf(hipFeedback, handFeedback, headFeedback).filter { it.isNotEmpty() }.joinToString(", ")}"
            }.filter { it.isNotEmpty() }.joinToString(separator = "\n")

            // Display the formatted results in the TextView
            resultsTextView.text = "$resultsText\n\nFeedback:\n$feedbackText"
        }
    }

    override fun onResume() {
        super.onResume()

        // Clear the feedback text from SharedPreferences after displaying it
        val sharedPref: SharedPreferences = getSharedPreferences("feedback_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().remove("feedback_text").apply()
        sharedPref.edit().remove("repetition_results").apply()
    }
}
