package com.example.formfit.ui.feedback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.formfit.R

data class PushupResult(
    val repetition: Int,
    val bottomPosition: Float,
    val handPosition: Float,
    val headPosition: Float,
    val bottomPositionLabel: String,
    val handPositionLabel: String,
    val headPositionLabel: String,
    val bottomPositionScore: Int,
    val handPositionScore: Int,
    val headPositionScore: Int,
    val feedback: String
)

class ResultsAdapterPushup(private val resultsList: List<PushupResult>) : RecyclerView.Adapter<ResultsAdapterPushup.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val repetitionTextView: TextView = view.findViewById(R.id.repetitionTextView)
        val bottomPositionTextView: TextView = view.findViewById(R.id.hipPositionTextView)
        val handPositionTextView: TextView = view.findViewById(R.id.handPositionTextView)
        val headPositionTextView: TextView = view.findViewById(R.id.headPositionTextView)
        val bottomPositionScoreTextView: TextView = view.findViewById(R.id.hipScoreTextView)
        val handPositionScoreTextView: TextView = view.findViewById(R.id.handScoreTextView)
        val headPositionScoreTextView: TextView = view.findViewById(R.id.headScoreTextView)
        val feedbackTextView: TextView = view.findViewById(R.id.feedbackTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.pushup_result_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = resultsList[position]
        holder.repetitionTextView.text = "Repetisi ${result.repetition}"
        holder.bottomPositionTextView.text = "Posisi Pinggul: ${result.bottomPositionLabel}"
        holder.handPositionTextView.text = "Posisi Tangan: ${result.handPositionLabel}"
        holder.headPositionTextView.text = "Posisi Kepala: ${result.headPositionLabel}"
        holder.bottomPositionScoreTextView.text = "Skor: ${result.bottomPositionScore}"
        holder.handPositionScoreTextView.text = "Skor: ${result.handPositionScore}"
        holder.headPositionScoreTextView.text = "Skor: ${result.headPositionScore}"
        holder.feedbackTextView.text = result.feedback

        val bottomPositionBackground = if (result.bottomPositionLabel == "Benar") R.drawable.rounded_green_background else R.drawable.rounded_red_background
        val handPositionBackground = if (result.handPositionLabel == "Benar") R.drawable.rounded_green_background else R.drawable.rounded_red_background
        val headPositionBackground = if (result.headPositionLabel == "Benar") R.drawable.rounded_green_background else R.drawable.rounded_red_background

        holder.bottomPositionScoreTextView.setBackgroundResource(bottomPositionBackground)
        holder.handPositionScoreTextView.setBackgroundResource(handPositionBackground)
        holder.headPositionScoreTextView.setBackgroundResource(headPositionBackground)
    }

    override fun getItemCount() = resultsList.size
}
