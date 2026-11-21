package com.example.uas

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.uas.databinding.ActivityNotificationBinding
import com.example.uas.databinding.ItemNotificationBinding

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        binding.btnBackNotif.setOnClickListener { finish() }

        binding.tvClearNotif.setOnClickListener {
            val user = dbHelper.getCurrentUser() ?: return@setOnClickListener
            dbHelper.clearNotificationLogs(user.id)
            Toast.makeText(this, "Semua notifikasi telah dihapus", Toast.LENGTH_SHORT).show()
            loadNotifications()
        }

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        loadNotifications()
    }

    private fun loadNotifications() {
        val user = dbHelper.getCurrentUser()
        if (user == null) {
            finish()
            return
        }

        dbHelper.markNotificationsRead(user.id)
        val logs = dbHelper.getNotificationLogs(user.id)

        if (logs.isEmpty()) {
            binding.rvNotifications.visibility = View.GONE
            binding.layoutEmptyNotif.visibility = View.VISIBLE
            binding.tvClearNotif.visibility = View.GONE
        } else {
            binding.rvNotifications.visibility = View.VISIBLE
            binding.layoutEmptyNotif.visibility = View.GONE
            binding.tvClearNotif.visibility = View.VISIBLE
            binding.rvNotifications.adapter = NotifAdapter(logs)
        }
    }

    inner class NotifAdapter(private val items: List<String>) :
        RecyclerView.Adapter<NotifAdapter.NotifVH>() {

        inner class NotifVH(val itemBinding: ItemNotificationBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifVH {
            val b = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return NotifVH(b)
        }

        override fun onBindViewHolder(holder: NotifVH, position: Int) {
            val raw = items[position]
            val context = holder.itemView.context

            // Pisahkan format "[18:18] Pesan notifikasi..."
            val timeRegex = Regex("^\\[(.*?)\\]\\s*(.*)$")
            val match = timeRegex.find(raw)
            val timeText = match?.groupValues?.get(1) ?: "Baru saja"
            val msgText = match?.groupValues?.get(2) ?: raw

            with(holder.itemBinding) {
                tvNotifTime.text = timeText
                tvNotifMessage.text = msgText

                when {
                    msgText.contains("Montir Sudah Tiba", ignoreCase = true) -> {
                        tvNotifTitle.text = "Montir Sudah Tiba"
                        tvNotifTitle.setTextColor(ContextCompat.getColor(context, R.color.badge_service_green))
                        cardNotifIconBg.setCardBackgroundColor(ContextCompat.getColor(context, R.color.badge_service_green_bg))
                        ivNotifIcon.setColorFilter(ContextCompat.getColor(context, R.color.badge_service_green))
                    }
                    msgText.contains("Montir Menuju Lokasi", ignoreCase = true) -> {
                        tvNotifTitle.text = "Montir Menuju Lokasi"
                        tvNotifTitle.setTextColor(ContextCompat.getColor(context, R.color.primary_electric_blue))
                        cardNotifIconBg.setCardBackgroundColor(ContextCompat.getColor(context, R.color.primary_blue_container))
                        ivNotifIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary_electric_blue))
                    }
                    msgText.contains("Menunggu", ignoreCase = true) -> {
                        tvNotifTitle.text = "Menunggu Konfirmasi"
                        tvNotifTitle.setTextColor(Color.parseColor("#F59E0B"))
                        cardNotifIconBg.setCardBackgroundColor(Color.parseColor("#2B2212"))
                        ivNotifIcon.setColorFilter(Color.parseColor("#F59E0B"))
                    }
                    else -> {
                        tvNotifTitle.text = "Update AutoFix Center"
                        tvNotifTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        cardNotifIconBg.setCardBackgroundColor(ContextCompat.getColor(context, R.color.primary_blue_container))
                        ivNotifIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary_electric_blue))
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}