package com.example.uas

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.uas.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = DatabaseHelper(this)

        // Cek sesi login otomatis (Syarat 3.1: tidak perlu login ulang setiap membuka aplikasi)
        if (dbHelper.getCurrentUser() != null) {
            navigateToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            handleLogin()
        }

        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun handleLogin() {
        val email = binding.etEmailLogin.text.toString().trim()
        val password = binding.etPasswordLogin.text.toString()

        binding.tilEmailLogin.error = null
        binding.tilPasswordLogin.error = null

        if (email.isEmpty()) {
            binding.tilEmailLogin.error = getString(R.string.error_field_kosong)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailLogin.error = getString(R.string.error_email_tidak_valid)
            return
        }
        if (password.isEmpty()) {
            binding.tilPasswordLogin.error = getString(R.string.error_field_kosong)
            return
        }

        val user = dbHelper.loginUser(email, password)
        if (user != null) {
            Toast.makeText(this, "Selamat datang, ${user.fullName}!", Toast.LENGTH_SHORT).show()
            navigateToMain()
        } else {
            binding.tilPasswordLogin.error = getString(R.string.error_login_gagal)
            Toast.makeText(this, getString(R.string.error_login_gagal), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}