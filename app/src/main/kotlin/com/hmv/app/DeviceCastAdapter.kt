package com.hmv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceCastAdapter(
    private val onDeviceClicked: (NsdHelper.DiscoveredDevice) -> Unit
) : RecyclerView.Adapter<DeviceCastAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<NsdHelper.DiscoveredDevice>()

    fun submit(newDevices: List<NsdHelper.DiscoveredDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = devices.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_selector, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.name.text = device.name
        holder.address.text = "${device.host}:${device.port}"
        holder.itemView.setOnClickListener { onDeviceClicked(device) }
    }

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.device_name)
        val address: TextView = view.findViewById(R.id.device_address)
    }
}
