package com.example.formfit.ui.feedback

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.formfit.R

data class PullUpResult(
    val repetition: Int,
    val gripAnalysis : Int,
    val romAnalysis : Int,
    val momentumAnalysis : Int
)

class ResultsAdapterPullUp(private val resultsList: List<PullUpResult>) : RecyclerView.Adapter<ResultsAdapterPullUp.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val repetitionTextView: TextView = view.findViewById(R.id.repetitionTextView)
        val gripScoreTextView: TextView = view.findViewById(R.id.gripScoreTextView)
        val romScoreTextView: TextView = view.findViewById(R.id.romScoreTextView)
        val momentumScoreTextView: TextView = view.findViewById(R.id.momentumScoreTextView)
        val feedbackTextView: TextView = view.findViewById(R.id.feedbackTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.pull_up_result_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = resultsList[position]

        holder.repetitionTextView.text = "Repetisi ke - ${result.repetition}"

        if (result.gripAnalysis == 0) {
            holder.gripScoreTextView.text = "Benar"
        } else if (result.gripAnalysis == 1) {
            holder.gripScoreTextView.text = "Grip Terlalu Lebar!"
        } else {
            holder.gripScoreTextView.text = "Grip Terlalu Kecil!"
        }

        holder.romScoreTextView.text = if (result.romAnalysis == 0) "Benar" else "Tidak Full ROM"
        holder.momentumScoreTextView.text = if (result.romAnalysis == 0) "Benar" else "Menggunakan Ayunan"

        // Set background based on the condition
        val gripBackground = if (result.gripAnalysis == 0) R.drawable.rounded_green_background else R.drawable.rounded_red_background
        val romBackground = if (result.romAnalysis == 0) R.drawable.rounded_green_background else R.drawable.rounded_red_background
        val momentumBackground = if (result.romAnalysis == 0) R.drawable.rounded_green_background else R.drawable.rounded_red_background

        holder.gripScoreTextView.setBackgroundResource(gripBackground)
        holder.romScoreTextView.setBackgroundResource(romBackground)
        holder.momentumScoreTextView.setBackgroundResource(momentumBackground)

        val feedback = StringBuilder()
        feedback.append("Ini Feedback")
        holder.feedbackTextView.text = feedback.toString()
    }

    override fun getItemCount() = resultsList.size
}
