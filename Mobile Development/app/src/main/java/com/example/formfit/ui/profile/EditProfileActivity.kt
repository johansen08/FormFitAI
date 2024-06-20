package com.example.formfit.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.formfit.R
import com.google.android.material.textfield.TextInputLayout

class EditProfileActivity : AppCompatActivity() {

    private lateinit var save_button: Button
    private val REQUEST_IMAGE_CODE = 101
    private val STORAGE_PERMISSION_CODE = 102
    private lateinit var avatar_image: ImageButton

    private val AVATAR_IMAGE_URI_KEY = "avatar_image_uri"
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_profile)
        save_button = findViewById(R.id.save_button)

        save_button.setOnClickListener {
            saveData()
        }

        avatar_image = findViewById(R.id.avatar_image)

        avatar_image.setOnClickListener {
            checkPermissionAndOpenGallery()
        }

        sharedPreferences = getSharedPreferences("userdata", Context.MODE_PRIVATE)
        val savedUri = sharedPreferences.getString(AVATAR_IMAGE_URI_KEY, null)
        if (savedUri != null) {
            avatar_image.setImageURI(Uri.parse(savedUri))
        }

    }

    private fun saveData() {
        val nameInput = findViewById<TextInputLayout>(R.id.text_input_name).editText?.text.toString()
        val weightInput = findViewById<TextInputLayout>(R.id.text_input_weight).editText?.text.toString()
        val heightInput = findViewById<TextInputLayout>(R.id.text_input_height).editText?.text.toString()
        val checkbox1 = findViewById<CheckBox>(R.id.checkbox_gender1).text.toString()

        val intent = Intent()
        intent.putExtra("name", nameInput)
        intent.putExtra("weight", weightInput)
        intent.putExtra("height", heightInput)
        intent.putExtra("gender", checkbox1)
        intent.putExtra("avatar_uri", sharedPreferences.getString(AVATAR_IMAGE_URI_KEY, null))

        setResult(RESULT_OK, intent)
        finish()
    }

    private fun checkPermissionAndOpenGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE)
        } else {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val selectedImage: Uri? = data.data
            avatar_image.setImageURI(selectedImage)
        }
    }
}