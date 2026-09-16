package com.hmv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val onDeviceClicked: (NsdHelper.DiscoveredDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private val devices = mutableListOf<NsdHelper.DiscoveredDevice>()

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

        fun bind(device: NsdHelper.DiscoveredDevice) {
            nameText.text = device.name
            addressText.text = "${device.host}:${device.port}"
            itemView.setOnClickListener { onDeviceClicked(device) }
        }
    }
}
