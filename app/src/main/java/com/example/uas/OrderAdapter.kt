package com.example.uas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.uas.databinding.ItemEmergencyOrderBinding
import java.text.NumberFormat
import java.util.Locale

class OrderAdapter(
    private var orders: List<EmergencyOrder>,
    private val onItemClick: (EmergencyOrder) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    inner class OrderViewHolder(val binding: ItemEmergencyOrderBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemEmergencyOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val item = orders[position]
        val context = holder.itemView.context

        with(holder.binding) {
            tvItemTipeMobil.text = item.tipeMobil
            tvItemPlatNomor.text = "\"${item.platNomor}\""
            tvItemBadgeJenis.text = item.jenisPenanganan
            tvItemKeluhanLokasi.text = "📍 ${item.kategoriMasalah} - ${item.lokasiMogok}"
            tvItemStatusTanggal.text = "${item.statusPenanganan} • ${item.tanggalWaktu}"

            // Format Rupiah (contoh: Rp 350.000)
            val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
            tvItemBiaya.text = "Rp ${formatter.format(item.estimasiBiaya)}"

            // Ubah warna badge: Merah untuk Derek ke Bengkel, Hijau untuk Servis di Lokasi
            val isDerek = item.jenisPenanganan.contains("Derek", ignoreCase = true)
            val badgeColorRes = if (isDerek) R.color.badge_sos_red else R.color.badge_service_green
            cardBadgeJenis.setCardBackgroundColor(ContextCompat.getColor(context, badgeColorRes))

            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount(): Int = orders.size

    fun updateData(newOrders: List<EmergencyOrder>) {
        orders = newOrders
        notifyDataSetChanged()
    }
}