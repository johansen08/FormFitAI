package com.example.formfit.ui.info

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.formfit.R
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import com.example.formfit.ui.infoPullup.InfoPullupActivity
import com.example.formfit.ui.infoPushup.InfoPushupActivity
import com.example.formfit.ui.infoSquat.InfoSquatActivity


class InfoFragment : Fragment() {

    private lateinit var toolbar: Toolbar
    private lateinit var cardPullUp: CardView
    private lateinit var cardPushUp: CardView
    private lateinit var cardSquat: CardView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).getSupportActionBar()?.setDisplayShowTitleEnabled(false)

        cardPullUp = view.findViewById(R.id.card_pull_up)
        cardPullUp.setOnClickListener {
            val intent = Intent(activity, InfoPullupActivity::class.java)
            startActivity(intent)
        }

        cardPushUp = view.findViewById(R.id.card_push_up)
        cardPushUp.setOnClickListener {
            val intent = Intent(activity, InfoPushupActivity::class.java)
            startActivity(intent)
        }

        cardSquat = view.findViewById(R.id.card_squat)
        cardSquat.setOnClickListener {
            val intent = Intent(activity, InfoSquatActivity::class.java)
            startActivity(intent)
        }
    }

}