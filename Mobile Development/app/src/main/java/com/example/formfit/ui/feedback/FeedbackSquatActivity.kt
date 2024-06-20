package com.example.formfit.ui.feedback

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.formfit.R

class FeedbackSquatActivity : AppCompatActivity() {

    private lateinit var repetitionResults: Array<FloatArray>
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var resultsAdapter: ResultsAdapter

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
            repetitionResults = bundle.getSerializable("repetitionResults") as Array<FloatArray>
            displayResults()
        }
    }

    private fun calculateKneeScore(angle: Float): Int {
        return when {
            angle < 40 -> 0
            angle <= 90 -> ((angle / 90) * 100).toInt()
            else -> (100 - ((angle - 90) / 90) * 100).toInt()
        }
    }

    private fun calculateHipScore(angle: Float): Int {
        return when {
            angle < 5 -> 0
            angle <= 45 -> ((angle / 45) * 100).toInt()
            else -> (100 - ((angle - 45) / 45) * 100).toInt()
        }
    }

    private fun displayResults() {
        val resultsList = mutableListOf<SquatResult>()
        var correctSquats = 0
        var wrongSquats = 0

        for ((index, result) in repetitionResults.withIndex()) {
            val kneeScore = calculateKneeScore(result[0])
            val hipScore = calculateHipScore(result[1])

            val kneePosition = if (kneeScore >= 50) "Benar" else "Salah"
            val hipPosition = if (hipScore >= 50) "Benar" else "Salah"

            resultsList.add(
                SquatResult(
                    repetition = index + 1,
                    kneeAngle = result[0],
                    hipAngle = result[1],
                    kneePosition = kneePosition,
                    hipPosition = hipPosition,
                    kneeScore = kneeScore,
                    hipScore = hipScore
                )
            )

            if (kneePosition == "Benar" && hipPosition == "Benar") {
                correctSquats++
            } else {
                wrongSquats++
            }
        }

        totalSquatsTextView.text = "Total Squat: ${repetitionResults.size}"
        correctSquatsTextView.text = "Benar: $correctSquats"
        wrongSquatsTextView.text = "Salah: $wrongSquats"

        resultsAdapter = ResultsAdapter(resultsList)
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = resultsAdapter
    }
}
