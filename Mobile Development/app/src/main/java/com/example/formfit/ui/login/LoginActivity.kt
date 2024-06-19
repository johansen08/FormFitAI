package com.example.formfit.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.formfit.MainActivity
import com.example.formfit.R
import com.example.formfit.data.remote.ApiService
import com.example.formfit.ui.register.RegisterActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {

    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var textRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        textRegister = findViewById(R.id.textRegister)

        buttonLogin.setOnClickListener {
            val email = editTextEmail.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            // Validate input fields
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this@LoginActivity, "Email dan Password harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Perform login API call
            loginUser(email, password)
        }

        textRegister.setOnClickListener {
            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            finish()
        }
    }

    private fun loginUser(email: String, password: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://formfit-backend-api-firestore-jgyozsb3oq-uc.a.run.app")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(ApiService::class.java)
        val call = service.loginUser(LoginRequest(email, password))

        call.enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    // Save JWT token to Shared Preferences or other secure storage
                    val token = response.body()?.token
                    Toast.makeText(this@LoginActivity, "Login berhasil", Toast.LENGTH_SHORT).show()

                    // Example: Start MainActivity
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Login gagal, coba lagi", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Toast.makeText(this@LoginActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
