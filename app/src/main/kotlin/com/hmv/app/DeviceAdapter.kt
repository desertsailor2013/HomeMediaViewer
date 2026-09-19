package com.hmv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val onDeviceClicked: (NsdHelper.DiscoveredDevice) -> Unit,
    private val onFavoriteClicked: (NsdHelper.DiscoveredDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private val devices = mutableListOf<NsdHelper.DiscoveredDevice>()
    private var favoritesManager: DeviceFavoritesManager? = null

    fun setFavoritesManager(manager: DeviceFavoritesManager) {
        favoritesManager = manager
        notifyDataSetChanged()
    }

    fun addDevice(device: NsdHelper.DiscoveredDevice) {
        if (devices.none { it.name == device.name }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun removeDevice(device: NsdHelper.DiscoveredDevice) {
        val index = devices.indexOfFirst { it.name == device.name }
        if (index >= 0) {
            devices.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun getDevices(): List<NsdHelper.DiscoveredDevice> = devices.toList()

    fun getFavoritesFirst(): List<NsdHelper.DiscoveredDevice> {
        val favs = devices.filter { favoritesManager?.isFavorite(it.name) == true }
        val nonFavs = devices.filter { favoritesManager?.isFavorite(it.name) != true }
        return favs + nonFavs
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount(): Int = devices.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.device_name)
        private val addressText: TextView = itemView.findViewById(R.id.device_address)
        private val favoriteBtn: ImageButton = itemView.findViewById(R.id.btn_favorite)

        fun bind(device: NsdHelper.DiscoveredDevice) {
            val alias = favoritesManager?.getAlias(device.name) ?: ""
            nameText.text = alias.ifEmpty { device.name }
            addressText.text = if (alias.isNotEmpty()) {
                "${device.name} · ${device.host}:${device.port}"
            } else {
                "${device.host}:${device.port}"
            }

            val isFav = favoritesManager?.isFavorite(device.name) == true
            favoriteBtn.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            favoriteBtn.visibility = View.VISIBLE

            itemView.setOnClickListener { onDeviceClicked(device) }
            favoriteBtn.setOnClickListener { onFavoriteClicked(device) }
        }
    }
}
