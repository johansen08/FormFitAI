package com.example.formfit.ui.feedback

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.formfit.R

class FeedbackPullupActivity : AppCompatActivity() {
    private lateinit var repetitionResults: Array<IntArray>
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var resultsAdapter: ResultsAdapterPullUp

    private lateinit var totalSquatsTextView: TextView
    private lateinit var correctSquatsTextView: TextView
    private lateinit var wrongSquatsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback_squat)

        totalSquatsTextView = findViewById(R.id.totalSquatsTextView)
        correctSquatsTextView = findViewById(R.id.correctSquatsTextView)
        wrongSquatsTextView = findViewById(R.id.wrongSquatsTextView)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)

        // Retrieve the repetition results passed through the intent
        val bundle = intent.extras
        if (bundle != null) {
            repetitionResults = bundle.getSerializable("repetitionResults") as Array<IntArray>
            displayResults()
        }
    }



    private fun displayResults() {
        val resultsList = mutableListOf<PullUpResult>()
        var correctPullUp = 0
        var wrongPullUp = 0

        for ((index, result) in repetitionResults.withIndex()) {
            val gripAnalysis = result[0]
            val romAnalysis = result[1]
            val momentumAnalysis = result[2]


            resultsList.add(
                PullUpResult(
                    repetition = index + 1,
                    gripAnalysis = gripAnalysis,
                    romAnalysis = romAnalysis,
                    momentumAnalysis = romAnalysis
                )
            )

            if (gripAnalysis == 0 && romAnalysis==0 && momentumAnalysis == 0) {
                correctPullUp++
            } else {
                wrongPullUp++
            }
        }

        totalSquatsTextView.text = "Total Squat: ${repetitionResults.size}"
        correctSquatsTextView.text = "Benar: $correctPullUp"
        wrongSquatsTextView.text = "Salah: $wrongPullUp"

        resultsAdapter = ResultsAdapterPullUp(resultsList)
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = resultsAdapter
    }
}