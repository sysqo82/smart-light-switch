package com.example.smartlightswitch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartlightswitch.databinding.ItemDeviceBinding
import com.thingclips.smart.sdk.bean.DeviceBean

class DeviceAdapter(
    private val onClick: (DeviceBean) -> Unit,
    private val onLongClick: (DeviceBean) -> Unit = {}
) : ListAdapter<DeviceBean, DeviceAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DeviceBean) {
            binding.tvDeviceName.text = device.name
            binding.tvDeviceCategory.text = device.category ?: "Unknown"

            val isOn = device.dps?.get("1") as? Boolean ?: false
            binding.tvSwitchState.text = if (device.isOnline) {
                if (isOn) "ON" else "OFF"
            } else {
                "Offline"
            }

            val stateColor = when {
                !device.isOnline -> R.color.state_offline
                isOn             -> R.color.state_on
                else             -> R.color.state_off
            }
            binding.viewStatusDot.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, stateColor)
            )

            binding.root.setOnClickListener { onClick(device) }
            binding.root.setOnLongClickListener { onLongClick(device); true }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DeviceBean>() {
            override fun areItemsTheSame(old: DeviceBean, new: DeviceBean) = old.devId == new.devId
            // DeviceBean is a mutable SDK object mutated in-place, so the old and new
            // references always point to the same object with identical field values by
            // the time DiffUtil checks. Always return false so every submitList() rebinds.
            override fun areContentsTheSame(old: DeviceBean, new: DeviceBean) = false
        }
    }
}
