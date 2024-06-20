package com.example.formfit.ui.feedback

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.formfit.R

class FeedbackActivity : AppCompatActivity() {

    private lateinit var repetitionResults: MutableList<FloatArray>
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var resultsAdapter: ResultsAdapterPushup

    private lateinit var totalPushupTextView: TextView
    private lateinit var correctPushupTextView: TextView
    private lateinit var wrongPushupTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        totalPushupTextView = findViewById(R.id.totalPushupTextView)
        correctPushupTextView = findViewById(R.id.correctPushupTextView)
        wrongPushupTextView = findViewById(R.id.wrongPushupTextView)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)

        repetitionResults = mutableListOf()

        // Retrieve the repetition results passed through the intent
        val bundle = intent.extras
        if (bundle != null) {
            val resultArray = bundle.getSerializable("repetitionResults") as Array<FloatArray>
            repetitionResults.addAll(resultArray)
            displayResults()
        }
    }

    private fun calculateHeadPositionScore(angle: Float, threshold: Float): Int {
        return ((angle / 180) * 100).toInt()

    }

    private fun calculateHipPositionScore(angle: Float, threshold: Float): Int {
        return ((angle / 180) * 100).toInt()
    }

    private fun calculateHandPositionScore(angle: Float, threshold: Float): Int {
        return when {
            angle <= threshold -> ((angle / 1.3) * 100).toInt()
            else -> (100 - ((angle - 1.3 / 1.3) * 100)*2).toInt()
        }
    }

    private fun displayResults() {
        val resultsList = mutableListOf<PushupResult>()
        var correctPushups = 0
        var wrongPushups = 0

        for ((index, result) in repetitionResults.withIndex()) {
            val bottomPositionScore = calculateHipPositionScore(result[0], 160f)
            val handPositionScore = calculateHandPositionScore(result[1], 1.3f)
            val headPositionScore = calculateHeadPositionScore(result[2], 140f)

            val bottomPositionLabel = if (result[0] >= 160) "Benar" else "Salah"
            val handPositionLabel = if (result[1] <= 1.3) "Benar" else "Salah"
            val headPositionLabel = if (result[2] >= 140) "Benar" else "Salah"

            val feedback = StringBuilder()
            if (bottomPositionLabel == "Salah") {
                feedback.append("Pinggul harus berada dalam rentang 165-195 derajat. ")
            }
            if (handPositionLabel == "Salah") {
                feedback.append("Rasio tangan dan bahu harus kurang dari 1.3. ")
            }
            if (headPositionLabel == "Salah") {
                feedback.append("Kepala harus berada dalam rentang 140-160 derajat. ")
            }

            resultsList.add(
                PushupResult(
                    repetition = index + 1,
                    bottomPosition = result[0],
                    handPosition = result[1],
                    headPosition = result[2],
                    bottomPositionLabel = bottomPositionLabel,
                    handPositionLabel = handPositionLabel,
                    headPositionLabel = headPositionLabel,
                    bottomPositionScore = bottomPositionScore,
                    handPositionScore = handPositionScore,
                    headPositionScore = headPositionScore,
                    feedback = feedback.toString()
                )
            )

            if (bottomPositionLabel == "Benar" && handPositionLabel == "Benar" && headPositionLabel == "Benar") {
                correctPushups++
            } else {
                wrongPushups++
            }
        }

        totalPushupTextView.text = "Total Push-up: ${repetitionResults.size}"
        correctPushupTextView.text = "Benar: $correctPushups"
        wrongPushupTextView.text = "Salah: $wrongPushups"

        resultsAdapter = ResultsAdapterPushup(resultsList)
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = resultsAdapter
    }
}
