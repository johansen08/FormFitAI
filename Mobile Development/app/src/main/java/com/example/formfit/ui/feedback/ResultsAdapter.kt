package com.example.formfit.ui.feedback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.formfit.R

data class SquatResult(
    val repetition: Int,
    val kneeAngle: Float,
    val hipAngle: Float,
    val kneePosition: String,
    val hipPosition: String,
    val kneeScore: Int,
    val hipScore: Int
)

class ResultsAdapter(private val resultsList: List<SquatResult>) : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val repetitionTextView: TextView = view.findViewById(R.id.repetitionTextView)
        val kneePositionTextView: TextView = view.findViewById(R.id.kneePositionTextView)
        val hipPositionTextView: TextView = view.findViewById(R.id.hipPositionTextView)
        val kneeScoreTextView: TextView = view.findViewById(R.id.kneeScoreTextView)
        val hipScoreTextView: TextView = view.findViewById(R.id.hipScoreTextView)
        val feedbackTextView: TextView = view.findViewById(R.id.feedbackTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.squat_result_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = resultsList[position]
        holder.repetitionTextView.text = "Repetisi ${result.repetition}"
        holder.kneePositionTextView.text = "Posisi Lutut: ${result.kneePosition}"
        holder.hipPositionTextView.text = "Posisi Pinggul: ${result.hipPosition}"
        holder.kneeScoreTextView.text = "Skor: ${result.kneeScore}"
        holder.hipScoreTextView.text = "Skor: ${result.hipScore}"

        // Set background based on the condition
        val kneeBackground = if (result.kneePosition == "Benar") R.drawable.rounded_green_background else R.drawable.rounded_red_background
        val hipBackground = if (result.hipPosition == "Benar") R.drawable.rounded_green_background else R.drawable.rounded_red_background

        holder.kneeScoreTextView.setBackgroundResource(kneeBackground)
        holder.hipScoreTextView.setBackgroundResource(hipBackground)

        val feedback = StringBuilder()
        if (result.kneePosition == "Salah") {
            feedback.append("Lutut harus lebih dekat ke 90 derajat. ")
        }
        if (result.hipPosition == "Salah") {
            feedback.append("Pinggul harus berada dalam rentang 20-45 derajat. ")
        }
        holder.feedbackTextView.text = feedback.toString()
    }

    override fun getItemCount() = resultsList.size
}
