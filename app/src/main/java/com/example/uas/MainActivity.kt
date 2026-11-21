package com.example.uas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uas.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var orderAdapter: OrderAdapter

    private var currentFilterType: String = "Semua"
    private var currentSearchQuery: String = ""
    private var selectedProfilePhotoUri: String = ""

    // Launcher Kamera untuk Foto Profil
    private val takeProfilePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                val savedUri = saveBitmapToInternalStorage(bitmap)
                if (savedUri != null) {
                    selectedProfilePhotoUri = savedUri.toString()
                    displayAvatarImage(binding.ivAvatarProfil, selectedProfilePhotoUri, 20)
                }
            }
        }

    // Launcher Izin Kamera Runtime (Syarat 3.5)
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takeProfilePictureLauncher.launch(null)
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera ditolak. Anda tetap bisa memilih foto dari Galeri.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    // Launcher Izin Notifikasi untuk Android 13+ (agar muncul di panel atas HP)
    private val requestNotifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private fun askNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Launcher Galeri untuk Foto Profil
    private val pickGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val savedUri = copyUriToInternalStorage(uri)
                if (savedUri != null) {
                    selectedProfilePhotoUri = savedUri.toString()
                    displayAvatarImage(binding.ivAvatarProfil, selectedProfilePhotoUri, 20)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        val currentUser = dbHelper.getCurrentUser()
        if (currentUser == null) {
            navigateToLogin()
            return
        }

        seedInitialOrdersIfEmpty(currentUser)

        setupRecyclerView()
        setupSearchAndFilter()
        setupBottomNavigation()
        setupBerandaButtons()
        setupProfileSection()
        loadUserDataToViews()
        askNotificationPermissionIfNeeded()
        refreshOrdersList()
    }

    override fun onResume() {
        super.onResume()
        loadUserDataToViews()
        refreshOrdersList()
    }

    private fun seedInitialOrdersIfEmpty(user: UserAccount) {
        val existing = dbHelper.getOrdersByUser(user.id)
        if (existing.isEmpty()) {
            dbHelper.insertOrder(
                EmergencyOrder(
                    userId = user.id,
                    namaPemilik = user.fullName,
                    tipeMobil = "Toyota Avanza",
                    platNomor = "BL 1234 AA",
                    kategoriMasalah = "Mesin Mati Total",
                    jenisPenanganan = "Derek ke Bengkel",
                    deskripsiKeluhan = "Mesin tiba-tiba mati total dan keluar asap dari kap depan.",
                    lokasiMogok = "Jl. T. Nyak Arief",
                    fotoKondisiUri = "",
                    estimasiBiaya = 350000,
                    statusPenanganan = "Montir Menuju Lokasi",
                    tanggalWaktu = "17 Nov 2026"
                )
            )
            dbHelper.insertOrder(
                EmergencyOrder(
                    userId = user.id,
                    namaPemilik = user.fullName,
                    tipeMobil = "Honda Brio",
                    platNomor = "BL 8891 Z",
                    kategoriMasalah = "Aki Tekor",
                    jenisPenanganan = "Servis di Lokasi",
                    deskripsiKeluhan = "Mobil tidak bisa distarter, butuh jumper aki di lokasi.",
                    lokasiMogok = "Simpang Lamnyong",
                    fotoKondisiUri = "",
                    estimasiBiaya = 150000,
                    statusPenanganan = "Menunggu Montir",
                    tanggalWaktu = "17 Nov 2026"
                )
            )
        }
    }

    private fun loadUserDataToViews() {
        val currentUser = dbHelper.getCurrentUser() ?: return
        val shortName = currentUser.fullName.trim().split(" ").lastOrNull() ?: currentUser.fullName
        binding.tvGreetingName.text = getString(R.string.greeting_user, shortName)

        selectedProfilePhotoUri = currentUser.photoUri
        displayAvatarImage(binding.ivUserAvatarMini, currentUser.photoUri, 9)
        displayAvatarImage(binding.ivAvatarProfil, currentUser.photoUri, 20)

        binding.etProfilNama.setText(currentUser.fullName)
        binding.etProfilEmail.setText(currentUser.email)
        binding.etProfilWa.setText(currentUser.whatsapp)
        binding.etProfilPassword.setText("")

        // Buat ikon lonceng menyala biru jika ada notifikasi status baru
        val bellColor = if (dbHelper.hasUnreadNotification(currentUser.id)) {
            ContextCompat.getColor(this, R.color.primary_electric_blue)
        } else {
            ContextCompat.getColor(this, R.color.text_secondary)
        }
        binding.ivBell.setColorFilter(bellColor)
    }

    private fun displayAvatarImage(imageView: ImageView, photoUriString: String, defaultPaddingDp: Int) {
        val density = resources.displayMetrics.density
        if (photoUriString.isNotEmpty()) {
            try {
                imageView.setPadding(0, 0, 0, 0)
                imageView.imageTintList = null
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageURI(Uri.parse(photoUriString))
                return
            } catch (_: Exception) {
                // Jika file gagal dimuat, kembali ke gambar default
            }
        }
        val padPx = (defaultPaddingDp * density).toInt()
        imageView.setPadding(padPx, padPx, padPx, padPx)
        imageView.setImageResource(android.R.drawable.ic_menu_myplaces)
        imageView.setColorFilter(ContextCompat.getColor(this, R.color.primary_electric_blue))
    }

    private fun setupProfileSection() {
        val openPhotoPicker = View.OnClickListener {
            showPhotoSourceDialog()
        }
        binding.frameAvatarProfil.setOnClickListener(openPhotoPicker)
        binding.btnBadgeKameraProfil.setOnClickListener(openPhotoPicker)
        binding.tvUbahFotoProfil.setOnClickListener(openPhotoPicker)

        // Simpan Perubahan Profil
        binding.btnSimpanProfil.setOnClickListener {
            handleSaveProfile()
        }

        // Keluar dari Akun (Logout)
        binding.btnLogoutProfil.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_logout_title))
                .setMessage(getString(R.string.dialog_logout_msg))
                .setNegativeButton(getString(R.string.btn_batal), null)
                .setPositiveButton("Ya, Keluar") { _, _ ->
                    dbHelper.clearSession()
                    navigateToLogin()
                }
                .show()
        }

        // Hapus Akun Permanen
        binding.btnHapusAkunProfil.setOnClickListener {
            val user = dbHelper.getCurrentUser() ?: return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_hapus_akun_title))
                .setMessage(getString(R.string.dialog_hapus_akun_msg))
                .setNegativeButton(getString(R.string.btn_batal), null)
                .setPositiveButton(getString(R.string.btn_ya_hapus)) { _, _ ->
                    dbHelper.deleteUserAccount(user.id)
                    Toast.makeText(this, "Akun berhasil dihapus permanen", Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
                .show()
        }
    }

    private fun showPhotoSourceDialog() {
        val options = arrayOf(
            getString(R.string.opsi_kamera),
            getString(R.string.opsi_galeri),
            getString(R.string.opsi_foto_default)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_pilih_foto_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            takeProfilePictureLauncher.launch(null)
                        } else {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> pickGalleryLauncher.launch("image/*")
                    2 -> {
                        selectedProfilePhotoUri = ""
                        displayAvatarImage(binding.ivAvatarProfil, "", 20)
                    }
                }
            }
            .show()
    }

    private fun handleSaveProfile() {
        val currentUser = dbHelper.getCurrentUser() ?: return
        val nama = binding.etProfilNama.text.toString().trim()
        val email = binding.etProfilEmail.text.toString().trim()
        val wa = binding.etProfilWa.text.toString().trim()
        val newPass = binding.etProfilPassword.text.toString()

        binding.tilProfilNama.error = null
        binding.tilProfilEmail.error = null
        binding.tilProfilWa.error = null
        binding.tilProfilPassword.error = null

        if (nama.isEmpty()) {
            binding.tilProfilNama.error = getString(R.string.error_field_kosong)
            return
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilProfilEmail.error = getString(R.string.error_email_tidak_valid)
            return
        }
        if (dbHelper.isEmailExists(email, excludeUserId = currentUser.id)) {
            binding.tilProfilEmail.error = "Email ini sudah dipakai akun lain"
            return
        }
        if (wa.isEmpty()) {
            binding.tilProfilWa.error = getString(R.string.error_field_kosong)
            return
        }
        if (newPass.isNotEmpty() && newPass.length < 6) {
            binding.tilProfilPassword.error = getString(R.string.error_password_pendek)
            return
        }

        val success = dbHelper.updateUserProfile(
            userId = currentUser.id,
            fullName = nama,
            email = email,
            whatsapp = wa,
            newPassword = if (newPass.isEmpty()) null else newPass,
            photoUri = selectedProfilePhotoUri
        )

        if (success) {
            Toast.makeText(this, getString(R.string.msg_profil_diperbarui), Toast.LENGTH_SHORT).show()
            loadUserDataToViews()
        }
    }

    private fun saveBitmapToInternalStorage(bitmap: Bitmap): Uri? {
        return try {
            val file = File(filesDir, "avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }

    private fun copyUriToInternalStorage(sourceUri: Uri): Uri? {
        return try {
            val file = File(filesDir, "avatar_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }

    private fun setupRecyclerView() {
        orderAdapter = OrderAdapter(emptyList()) { clickedOrder ->
            val intent = Intent(this, DetailOrderActivity::class.java)
            intent.putExtra(DetailOrderActivity.EXTRA_ORDER, clickedOrder)
            startActivity(intent)
        }
        binding.rvRiwayatOrders.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = orderAdapter
        }
    }

    private fun setupSearchAndFilter() {
        binding.etSearchRiwayat.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                refreshOrdersList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipFilterSemua.setOnClickListener { updateFilterSelection("Semua") }
        binding.chipFilterLokasi.setOnClickListener { updateFilterSelection("Servis di Lokasi") }
        binding.chipFilterDerek.setOnClickListener { updateFilterSelection("Derek ke Bengkel") }
    }

    private fun updateFilterSelection(selectedType: String) {
        currentFilterType = selectedType
        val blue = ContextCompat.getColor(this, R.color.primary_electric_blue)
        val frosted = ContextCompat.getColor(this, R.color.surface_frosted)
        val slateStroke = ContextCompat.getColor(this, R.color.stroke_slate)
        val white = ContextCompat.getColor(this, R.color.white)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)

        listOf(
            Triple(binding.chipFilterSemua, binding.tvChipSemua, "Semua"),
            Triple(binding.chipFilterLokasi, binding.tvChipLokasi, "Servis di Lokasi"),
            Triple(binding.chipFilterDerek, binding.tvChipDerek, "Derek ke Bengkel")
        ).forEach { (card, tv, type) ->
            val isSelected = type == selectedType
            card.setCardBackgroundColor(if (isSelected) blue else frosted)
            card.strokeColor = if (isSelected) blue else slateStroke
            tv.setTextColor(if (isSelected) white else secondary)
            tv.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }
        refreshOrdersList()
    }

    private fun refreshOrdersList() {
        val currentUser = dbHelper.getCurrentUser() ?: return
        val orders = dbHelper.getOrdersByUser(
            userId = currentUser.id,
            searchQuery = currentSearchQuery,
            filterType = currentFilterType
        )
        orderAdapter.updateData(orders)

        if (orders.isEmpty()) {
            binding.rvRiwayatOrders.visibility = View.GONE
            binding.layoutEmptyRiwayat.visibility = View.VISIBLE
        } else {
            binding.rvRiwayatOrders.visibility = View.VISIBLE
            binding.layoutEmptyRiwayat.visibility = View.GONE
        }
    }

    private fun setupBottomNavigation() {
        binding.navTabBeranda.setOnClickListener { switchTab(1) }
        binding.navTabRiwayat.setOnClickListener { switchTab(2) }
        binding.navTabProfil.setOnClickListener { switchTab(3) }
        binding.cardAvatarMini.setOnClickListener { switchTab(3) }
    }

    private fun switchTab(tabIndex: Int) {
        binding.scrollBeranda.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.layoutRiwayat.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        binding.scrollProfil.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE

        val blue = ContextCompat.getColor(this, R.color.primary_electric_blue)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)

        binding.ivNavBeranda.setColorFilter(if (tabIndex == 1) blue else secondary)
        binding.tvNavBeranda.setTextColor(if (tabIndex == 1) blue else secondary)
        binding.tvNavBeranda.setTypeface(null, if (tabIndex == 1) Typeface.BOLD else Typeface.NORMAL)

        binding.ivNavRiwayat.setColorFilter(if (tabIndex == 2) blue else secondary)
        binding.tvNavRiwayat.setTextColor(if (tabIndex == 2) blue else secondary)
        binding.tvNavRiwayat.setTypeface(null, if (tabIndex == 2) Typeface.BOLD else Typeface.NORMAL)

        binding.ivNavProfil.setColorFilter(if (tabIndex == 3) blue else secondary)
        binding.tvNavProfil.setTextColor(if (tabIndex == 3) blue else secondary)
        binding.tvNavProfil.setTypeface(null, if (tabIndex == 3) Typeface.BOLD else Typeface.NORMAL)

        if (tabIndex == 2) refreshOrdersList()
        if (tabIndex == 3) loadUserDataToViews()
    }

    private fun setupBerandaButtons() {
        binding.btnBuatPanggilanSos.setOnClickListener {
            startActivity(Intent(this, FormOrderActivity::class.java))
        }
        binding.cardTarifServisLokasi.setOnClickListener {
            val intent = Intent(this, FormOrderActivity::class.java)
            intent.putExtra(FormOrderActivity.EXTRA_PRESELECT_TYPE, "Servis di Lokasi")
            startActivity(intent)
        }
        binding.cardTarifDerekBengkel.setOnClickListener {
            val intent = Intent(this, FormOrderActivity::class.java)
            intent.putExtra(FormOrderActivity.EXTRA_PRESELECT_TYPE, "Derek ke Bengkel")
            startActivity(intent)
        }

        // Klik Ikon Lonceng di Pojok Kanan Atas Beranda
        binding.ivBell.setOnClickListener {
            binding.ivBell.setOnClickListener {
                startActivity(Intent(this, NotificationActivity::class.java))
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}