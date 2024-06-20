package com.example.formfit.ui.profile

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.formfit.R

class ProfileFragment : Fragment() {

    private lateinit var editProfileButton: Button
    private val REQUEST_CODE_EDIT_PROFILE = 101
    private lateinit var username: TextView
    private lateinit var tv_gender: TextView
    private lateinit var tv_weight: TextView
    private lateinit var tv_height: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        editProfileButton = view.findViewById(R.id.editProfileButton)
        username = view.findViewById(R.id.username)
        tv_gender = view.findViewById(R.id.tv_gender)
        tv_weight = view.findViewById(R.id.tv_weight)
        tv_height = view.findViewById(R.id.tv_height)


        editProfileButton.setOnClickListener {
            val intent = Intent(activity, EditProfileActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_EDIT_PROFILE)
        }

        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_EDIT_PROFILE && resultCode == RESULT_OK) {
            val name = data?.getStringExtra("name") ?: ""
            val weight = data?.getStringExtra("weight") ?: ""
            val height = data?.getStringExtra("height") ?: ""
            val gender = data?.getStringExtra("gender") ?: ""

            username.text = name
            tv_gender.text = gender
            tv_weight.text = "$weight kg"
            tv_height.text = "$height cm"


        }
    }

}