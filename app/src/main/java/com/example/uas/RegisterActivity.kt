package com.example.uas

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.uas.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        binding.btnRegister.setOnClickListener {
            handleRegister()
        }

        binding.tvGoToLogin.setOnClickListener {
            finish()
        }
    }

    private fun handleRegister() {
        val nama = binding.etNamaRegister.text.toString().trim()
        val email = binding.etEmailRegister.text.toString().trim()
        val wa = binding.etWaRegister.text.toString().trim()
        val password = binding.etPasswordRegister.text.toString()

        binding.tilNamaRegister.error = null
        binding.tilEmailRegister.error = null
        binding.tilWaRegister.error = null
        binding.tilPasswordRegister.error = null

        if (nama.isEmpty()) {
            binding.tilNamaRegister.error = getString(R.string.error_field_kosong)
            return
        }
        if (email.isEmpty()) {
            binding.tilEmailRegister.error = getString(R.string.error_field_kosong)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailRegister.error = getString(R.string.error_email_tidak_valid)
            return
        }
        if (wa.isEmpty()) {
            binding.tilWaRegister.error = getString(R.string.error_field_kosong)
            return
        }
        if (password.length < 6) {
            binding.tilPasswordRegister.error = getString(R.string.error_password_pendek)
            return
        }
        if (dbHelper.isEmailExists(email)) {
            binding.tilEmailRegister.error = "Email ini sudah terdaftar, silakan masuk"
            return
        }

        val id = dbHelper.registerUser(nama, email, wa, password)
        if (id != -1L) {
            Toast.makeText(this, "Akun berhasil dibuat!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Gagal mendaftarkan akun", Toast.LENGTH_SHORT).show()
        }
    }
}