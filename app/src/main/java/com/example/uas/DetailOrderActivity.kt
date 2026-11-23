package com.example.uas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.uas.databinding.ActivityDetailOrderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

class DetailOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailOrderBinding
    private lateinit var dbHelper: DatabaseHelper
    private var currentOrder: EmergencyOrder? = null

    companion object {
        const val EXTRA_ORDER = "extra_order_detail"
        private const val WORKSHOP_WHATSAPP = "6281262933747"
        private const val CHANNEL_ID = "autofix_status_channel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        createNotificationChannel()
        loadOrderFromIntent()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        val orderId = currentOrder?.id ?: return
        val userId = currentOrder?.userId ?: return
        val updated = dbHelper.getOrdersByUser(userId).find { it.id == orderId }
        if (updated != null) {
            currentOrder = updated
            bindOrderToViews(updated)
        }
    }

    @Suppress("DEPRECATION")
    private fun loadOrderFromIntent() {
        val order = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_ORDER, EmergencyOrder::class.java)
        } else {
            intent.getSerializableExtra(EXTRA_ORDER) as? EmergencyOrder
        }

        if (order == null) {
            finish()
            return
        }
        currentOrder = order
        bindOrderToViews(order)
    }

    private fun bindOrderToViews(order: EmergencyOrder) {
        binding.tvDetailTipeMobil.text = order.tipeMobil
        binding.tvDetailPlatNomor.text = "• ${order.platNomor}"
        binding.tvDetailBadgeJenis.text = order.jenisPenanganan
        binding.tvBadgeStatusAtas.text = "${order.statusPenanganan} ▾"

        binding.tvDetailKategori.text = order.kategoriMasalah
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
        binding.tvDetailBiaya.text = "Rp ${formatter.format(order.estimasiBiaya)}"
        binding.tvDetailStatus.text = order.statusPenanganan
        binding.tvDetailTanggal.text = order.tanggalWaktu

        binding.tvDetailKeluhan.text = "Keluhan: ${order.deskripsiKeluhan}"
        binding.tvDetailLokasi.text = "Lokasi GPS: ${order.lokasiMogok}"

        // Warna teks berdasarkan 3 kondisi status
        val statusColor = when {
            order.statusPenanganan.equals("Menunggu", ignoreCase = true) ->
                Color.parseColor("#F59E0B") // Kuning Amber untuk Menunggu
            order.statusPenanganan.equals("Montir Menuju Lokasi", ignoreCase = true) ->
                ContextCompat.getColor(this, R.color.primary_electric_blue) // Biru untuk Menuju Lokasi
            else ->
                ContextCompat.getColor(this, R.color.badge_service_green) // Hijau untuk Montir Sudah Tiba
        }
        binding.tvBadgeStatusAtas.setTextColor(statusColor)
        binding.tvDetailStatus.setTextColor(statusColor)

        // Warna Badge Jenis Penanganan: Merah untuk Derek, Hijau untuk Servis Lokasi
        val isDerek = order.jenisPenanganan.contains("Derek", ignoreCase = true)
        val badgeColorRes = if (isDerek) R.color.badge_sos_red else R.color.badge_service_green
        binding.cardDetailBadgeJenis.setCardBackgroundColor(
            ContextCompat.getColor(this, badgeColorRes)
        )

        if (order.fotoKondisiUri.isNotEmpty()) {
            try {
                binding.ivDetailFotoMobil.setImageURI(Uri.parse(order.fotoKondisiUri))
            } catch (_: Exception) {
                binding.ivDetailFotoMobil.setImageResource(R.drawable.bg_hero_car)
            }
        } else {
            binding.ivDetailFotoMobil.setImageResource(R.drawable.bg_hero_car)
        }
    }

    private fun setupListeners() {
        binding.btnBackDetail.setOnClickListener {
            finish()
        }

        // Klik pada lencana status di kanan atas foto ATAU kotak Status untuk mengubah kondisi
        val statusClickListener = View.OnClickListener {
            showStatusPickerDialog()
        }
        binding.tvBadgeStatusAtas.setOnClickListener(statusClickListener)
        binding.tvDetailStatus.setOnClickListener(statusClickListener)

        binding.btnDetailEdit.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            val intent = Intent(this, FormOrderActivity::class.java)
            intent.putExtra(FormOrderActivity.EXTRA_EDIT_ORDER, order)
            startActivity(intent)
        }

        binding.btnDetailHapus.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        binding.tvBukaPeta.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            openLocationInMaps(order.lokasiMogok)
        }

        binding.btnHubungiWa.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            openWhatsAppToMechanic(order)
        }
    }

    private fun showStatusPickerDialog() {
        val order = currentOrder ?: return
        val statusOptions = arrayOf(
            "Menunggu",
            "Montir Menuju Lokasi",
            "Montir Sudah Tiba"
        )
        val currentIndex = statusOptions.indexOfFirst {
            it.equals(order.statusPenanganan, ignoreCase = true)
        }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Ubah Status Panggilan")
            .setSingleChoiceItems(statusOptions, currentIndex) { dialog, which ->
                val newStatus = statusOptions[which]
                dbHelper.updateOrderStatus(order.id, newStatus)

                val updatedOrder = order.copy(statusPenanganan = newStatus)
                currentOrder = updatedOrder
                bindOrderToViews(updatedOrder)

                // Simpan ke Lonceng Notifikasi Beranda & Tampilkan Notifikasi HP
                val notifText = "${order.tipeMobil} (${order.platNomor}): Status berubah menjadi \"$newStatus\""
                dbHelper.addNotificationLog(order.userId, notifText)
                sendSystemNotification("Update Status AutoFix Center", notifText)

                Toast.makeText(this, "Status diubah: $newStatus", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_batal), null)
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notifikasi Status Montir",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pemberitahuan perubahan status panggilan montir dan derek"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendSystemNotification(title: String, message: String) {
        try {
            val intent = Intent(this, NotificationActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: Exception) {
            // Abaikan jika izin notifikasi ditolak
        }
    }

    private fun showDeleteConfirmationDialog() {
        val order = currentOrder ?: return
        MaterialAlertDialogBuilder(this)
            .setIcon(android.R.drawable.ic_menu_delete)
            .setTitle(getString(R.string.dialog_hapus_order_title))
            .setMessage(
                getString(
                    R.string.dialog_hapus_order_msg,
                    order.tipeMobil,
                    order.platNomor
                )
            )
            .setNegativeButton(getString(R.string.btn_batal), null)
            .setPositiveButton(getString(R.string.btn_ya_hapus)) { _, _ ->
                dbHelper.deleteOrder(order.id)
                Toast.makeText(
                    this,
                    "Panggilan ${order.tipeMobil} (${order.platNomor}) berhasil dihapus",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            .show()
    }

    private fun openLocationInMaps(locationText: String) {
        try {
            val encodedQuery = Uri.encode(locationText)
            val gmmIntentUri = Uri.parse("geo:0,0?q=$encodedQuery")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            startActivity(mapIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Membuka lokasi: $locationText", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsAppToMechanic(order: EmergencyOrder) {
        try {
            val message = """
                Halo Admin *AutoFix Center*, saya butuh bantuan darurat:
                • Pemilik: ${order.namaPemilik}
                • Mobil: ${order.tipeMobil} (${order.platNomor})
                • Masalah: ${order.kategoriMasalah}
                • Penanganan: ${order.jenisPenanganan}
                • Status Saat Ini: ${order.statusPenanganan}
                • Keluhan: ${order.deskripsiKeluhan}
                • Lokasi: ${order.lokasiMogok}
            """.trimIndent()

            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val waUrl = "https://api.whatsapp.com/send?phone=$WORKSHOP_WHATSAPP&text=$encodedMsg"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak dapat membuka WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
}