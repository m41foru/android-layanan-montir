package com.example.uas

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.uas.databinding.ActivityFormOrderBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FormOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFormOrderBinding
    private lateinit var dbHelper: DatabaseHelper

    private var editingOrder: EmergencyOrder? = null
    private var selectedJenisPenanganan: String = "Servis di Lokasi"
    private var selectedEstimasiBiaya: Int = 150000
    private var selectedPhotoUriString: String = ""

    companion object {
        const val EXTRA_EDIT_ORDER = "extra_edit_order"
        const val EXTRA_PRESELECT_TYPE = "extra_preselect_type"
    }

    // Launcher Kamera untuk Foto Kondisi Mobil
    private val takeCarPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                val uri = saveBitmapToInternalStorage(bitmap)
                if (uri != null) {
                    selectedPhotoUriString = uri.toString()
                    updatePhotoPreview()
                }
            }
        }

    // Launcher Izin Kamera Runtime
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takeCarPhotoLauncher.launch(null)
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera ditolak. Anda tetap bisa memilih foto dari Galeri.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    // Launcher Galeri
    private val pickCarPhotoGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val savedUri = copyUriToInternalStorage(uri)
                if (savedUri != null) {
                    selectedPhotoUriString = savedUri.toString()
                    updatePhotoPreview()
                }
            }
        }

    // Launcher Izin Lokasi GPS Runtime (Syarat 3.5)
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                fetchCurrentGpsLocation()
            } else {
                Toast.makeText(
                    this,
                    "Izin lokasi ditolak. Silakan ketik patokan lokasi secara manual.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFormOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        setupCategoryDropdown()
        setupServiceTypeCards()
        setupListeners()
        checkIntentData()
    }

    private fun setupCategoryDropdown() {
        val categories = arrayOf(
            "Aki Tekor / Kelistrikan",
            "Mesin Mati Total",
            "Mesin Overheat",
            "Ban Pecah / Bocor",
            "Rem / Transmisi Bermasalah"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvKategoriMasalah.setAdapter(adapter)
    }

    private fun setupServiceTypeCards() {
        binding.cardPilihServisLokasi.setOnClickListener {
            selectServiceType("Servis di Lokasi")
        }
        binding.cardPilihDerekBengkel.setOnClickListener {
            selectServiceType("Derek ke Bengkel")
        }
    }

    private fun selectServiceType(type: String) {
        val isDerek = type.contains("Derek", ignoreCase = true)
        selectedJenisPenanganan = if (isDerek) "Derek ke Bengkel" else "Servis di Lokasi"
        selectedEstimasiBiaya = if (isDerek) 350000 else 150000

        val blue = ContextCompat.getColor(this, R.color.primary_electric_blue)
        val frosted = ContextCompat.getColor(this, R.color.surface_frosted)
        val slateStroke = ContextCompat.getColor(this, R.color.stroke_slate)
        val greenBg = ContextCompat.getColor(this, R.color.badge_service_green_bg)
        val greenText = ContextCompat.getColor(this, R.color.badge_service_green)
        val redBg = ContextCompat.getColor(this, R.color.badge_sos_red_bg)
        val redText = ContextCompat.getColor(this, R.color.badge_sos_red)
        val primaryText = ContextCompat.getColor(this, R.color.text_primary)
        val secondaryText = ContextCompat.getColor(this, R.color.text_secondary)
        val hintText = ContextCompat.getColor(this, R.color.text_hint)
        val density = resources.displayMetrics.density

        if (!isDerek) {
            binding.cardPilihServisLokasi.setCardBackgroundColor(greenBg)
            binding.cardPilihServisLokasi.strokeColor = blue
            binding.cardPilihServisLokasi.strokeWidth = (1.8f * density).toInt()
            binding.tvTitlePilihLokasi.setTextColor(primaryText)
            binding.tvHargaPilihLokasi.setTextColor(greenText)

            binding.cardPilihDerekBengkel.setCardBackgroundColor(frosted)
            binding.cardPilihDerekBengkel.strokeColor = slateStroke
            binding.cardPilihDerekBengkel.strokeWidth = (1f * density).toInt()
            binding.tvTitlePilihDerek.setTextColor(secondaryText)
            binding.tvHargaPilihDerek.setTextColor(hintText)
        } else {
            binding.cardPilihDerekBengkel.setCardBackgroundColor(redBg)
            binding.cardPilihDerekBengkel.strokeColor = blue
            binding.cardPilihDerekBengkel.strokeWidth = (1.8f * density).toInt()
            binding.tvTitlePilihDerek.setTextColor(primaryText)
            binding.tvHargaPilihDerek.setTextColor(redText)

            binding.cardPilihServisLokasi.setCardBackgroundColor(frosted)
            binding.cardPilihServisLokasi.strokeColor = slateStroke
            binding.cardPilihServisLokasi.strokeWidth = (1f * density).toInt()
            binding.tvTitlePilihLokasi.setTextColor(secondaryText)
            binding.tvHargaPilihLokasi.setTextColor(hintText)
        }
    }

    @Suppress("DEPRECATION")
    private fun checkIntentData() {
        val preselect = intent.getStringExtra(EXTRA_PRESELECT_TYPE)
        if (!preselect.isNullOrEmpty()) {
            selectServiceType(preselect)
        }

        val order = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_EDIT_ORDER, EmergencyOrder::class.java)
        } else {
            intent.getSerializableExtra(EXTRA_EDIT_ORDER) as? EmergencyOrder
        }

        if (order != null) {
            editingOrder = order
            binding.tvFormHeaderTitle.text = getString(R.string.title_edit_panggilan)
            binding.btnSubmitOrder.text = getString(R.string.btn_simpan_perubahan)

            binding.etFormTipeMobil.setText(order.tipeMobil)
            binding.etFormPlatNomor.setText(order.platNomor)
            binding.actvKategoriMasalah.setText(order.kategoriMasalah, false)
            selectServiceType(order.jenisPenanganan)
            binding.etFormDeskripsi.setText(order.deskripsiKeluhan)
            binding.etFormLokasi.setText(order.lokasiMogok)

            selectedPhotoUriString = order.fotoKondisiUri
            updatePhotoPreview()
        }
    }

    private fun setupListeners() {
        binding.btnBackForm.setOnClickListener {
            finish()
        }

        binding.btnAmbilGps.setOnClickListener {
            checkAndRequestGps()
        }

        binding.cardUploadFoto.setOnClickListener {
            showPhotoPickerDialog()
        }

        binding.btnSubmitOrder.setOnClickListener {
            handleSaveOrder()
        }
    }

    private fun checkAndRequestGps() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            fetchCurrentGpsLocation()
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentGpsLocation() {
        Toast.makeText(this, "Mengambil titik lokasi & membuka Google Maps...", Toast.LENGTH_SHORT).show()
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val lat = location?.latitude ?: 5.1801
                val lng = location?.longitude ?: 97.1507

                val latStr = String.format(Locale.US, "%.4f", lat)
                val lngStr = String.format(Locale.US, "%.4f", lng)
                val streetName = resolveAddressName(lat, lng)

                val formattedLocation = if (streetName.isNotEmpty()) {
                    "$streetName ($latStr, $lngStr)"
                } else {
                    "Titik GPS ($latStr, $lngStr)"
                }

                // 1. Isi otomatis kolom Titik Lokasi Mogok di Form
                binding.etFormLokasi.setText(formattedLocation)

                // 2. Langsung buka aplikasi Google Maps ke titik koordinat tersebut
                openGoogleMapsPin(lat, lng, formattedLocation)
            }
            .addOnFailureListener {
                val defaultLat = 5.1801
                val defaultLng = 97.1507
                val fallbackText = "Jl. Medan - Banda Aceh, Lhokseumawe (5.1801, 97.1507)"
                binding.etFormLokasi.setText(fallbackText)
                openGoogleMapsPin(defaultLat, defaultLng, fallbackText)
            }
    }

    private fun openGoogleMapsPin(lat: Double, lng: Double, label: String) {
        try {
            val encodedLabel = Uri.encode(label)
            val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                startActivity(fallbackIntent)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Titik koordinat berhasil diambil", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveAddressName(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale("id", "ID"))
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: ""
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun showPhotoPickerDialog() {
        val options = arrayOf(
            getString(R.string.opsi_kamera),
            getString(R.string.opsi_galeri),
            "Hapus Foto / Gunakan Default"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_pilih_foto_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            takeCarPhotoLauncher.launch(null)
                        } else {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> pickCarPhotoGalleryLauncher.launch("image/*")
                    2 -> {
                        selectedPhotoUriString = ""
                        updatePhotoPreview()
                    }
                }
            }
            .show()
    }

    private fun updatePhotoPreview() {
        if (selectedPhotoUriString.isNotEmpty()) {
            try {
                binding.ivPreviewFotoMobil.setImageURI(Uri.parse(selectedPhotoUriString))
                binding.ivPreviewFotoMobil.visibility = View.VISIBLE
                binding.badgeUbahFoto.visibility = View.VISIBLE
                binding.layoutPlaceholderFoto.visibility = View.GONE
                return
            } catch (_: Exception) {
                // Fallback ke placeholder
            }
        }
        binding.ivPreviewFotoMobil.visibility = View.GONE
        binding.badgeUbahFoto.visibility = View.GONE
        binding.layoutPlaceholderFoto.visibility = View.VISIBLE
    }

    private fun handleSaveOrder() {
        val currentUser = dbHelper.getCurrentUser()
        if (currentUser == null) {
            finish()
            return
        }

        val tipeMobil = binding.etFormTipeMobil.text.toString().trim()
        val platNomor = binding.etFormPlatNomor.text.toString().trim().uppercase()
        val kategori = binding.actvKategoriMasalah.text.toString().trim().ifEmpty { "Mesin Mati Total" }
        val deskripsi = binding.etFormDeskripsi.text.toString().trim()
        val lokasi = binding.etFormLokasi.text.toString().trim()

        binding.tilFormTipeMobil.error = null
        binding.tilFormPlatNomor.error = null
        binding.tilFormDeskripsi.error = null

        if (tipeMobil.isEmpty()) {
            binding.tilFormTipeMobil.error = getString(R.string.error_field_kosong)
            return
        }
        if (platNomor.isEmpty()) {
            binding.tilFormPlatNomor.error = getString(R.string.error_field_kosong)
            return
        }
        if (deskripsi.isEmpty()) {
            binding.tilFormDeskripsi.error = getString(R.string.error_field_kosong)
            return
        }
        if (lokasi.isEmpty()) {
            Toast.makeText(
                this,
                "Silakan klik Ambil GPS atau isi titik lokasi mogok",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val existing = editingOrder
        if (existing == null) {
            // Mode CREATE: Status awal selalu "Menunggu"
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            val today = dateFormat.format(Date())
            val statusAwal = "Menunggu"

            val newOrder = EmergencyOrder(
                userId = currentUser.id,
                namaPemilik = currentUser.fullName,
                tipeMobil = tipeMobil,
                platNomor = platNomor,
                kategoriMasalah = kategori,
                jenisPenanganan = selectedJenisPenanganan,
                deskripsiKeluhan = deskripsi,
                lokasiMogok = lokasi,
                fotoKondisiUri = selectedPhotoUriString,
                estimasiBiaya = selectedEstimasiBiaya,
                statusPenanganan = statusAwal,
                tanggalWaktu = today
            )
            dbHelper.insertOrder(newOrder)
            dbHelper.addNotificationLog(
                currentUser.id,
                "Panggilan baru: $tipeMobil ($platNomor) • Status: Menunggu"
            )
            Toast.makeText(this, "Panggilan darurat berhasil dikirim (Status: Menunggu)!", Toast.LENGTH_SHORT).show()
        } else {
            // Mode UPDATE (Edit Panggilan Darurat)
            val updatedOrder = existing.copy(
                tipeMobil = tipeMobil,
                platNomor = platNomor,
                kategoriMasalah = kategori,
                jenisPenanganan = selectedJenisPenanganan,
                deskripsiKeluhan = deskripsi,
                lokasiMogok = lokasi,
                fotoKondisiUri = selectedPhotoUriString,
                estimasiBiaya = selectedEstimasiBiaya
            )
            dbHelper.updateOrder(updatedOrder)
            dbHelper.addNotificationLog(
                currentUser.id,
                "Data diperbarui: $tipeMobil ($platNomor) • $selectedJenisPenanganan"
            )
            Toast.makeText(this, "Data panggilan berhasil diperbarui!", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun saveBitmapToInternalStorage(bitmap: Bitmap): Uri? {
        return try {
            val file = File(filesDir, "order_car_${System.currentTimeMillis()}.jpg")
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
            val file = File(filesDir, "order_car_${System.currentTimeMillis()}.jpg")
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
}