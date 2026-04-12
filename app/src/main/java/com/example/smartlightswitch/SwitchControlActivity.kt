package com.example.smartlightswitch

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.smartlightswitch.databinding.ActivitySwitchControlBinding
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.sdk.api.IThingDevice
import org.json.JSONObject

class SwitchControlActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySwitchControlBinding
    private lateinit var device: IThingDevice
    private val devId   by lazy { intent.getStringExtra(EXTRA_DEV_ID)!!   }
    private val devName by lazy { intent.getStringExtra(EXTRA_DEV_NAME) ?: "Switch" }

    // True while we're waiting for the DP acknowledgement, to prevent button spam
    private var isPublishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySwitchControlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = devName

        device = ThingHomeSdk.newDeviceInstance(devId)

        // Register real-time listener so the UI updates when the physical switch is toggled
        device.registerDevListener(object : IDevListener {
            override fun onDpUpdate(devId: String, dpStr: String) {
                runOnUiThread { applyDpString(dpStr) }
            }
            override fun onRemoved(devId: String) {
                runOnUiThread {
                    Toast.makeText(this@SwitchControlActivity, "Device removed", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            override fun onStatusChanged(devId: String, online: Boolean) {
                runOnUiThread { updateOnlineState(online) }
            }
            override fun onNetworkStatusChanged(devId: String, status: Boolean) { /* no-op */ }
            override fun onDevInfoUpdate(devId: String) { /* no-op */ }
        })

        // Seed the UI from the locally-cached device bean
        val bean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
        val isOnline = bean?.isOnline ?: false
        updateOnlineState(isOnline)
        if (isOnline) {
            val isOn = bean?.dps?.get("1") as? Boolean ?: false
            applySwitchState(isOn)
        }

        binding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!isPublishing) publishSwitch(isChecked)
        }
    }

    // ── Switch control ────────────────────────────────────────────────────────

    private fun publishSwitch(turnOn: Boolean) {
        isPublishing = true
        setLoading(true)

        // DP 1 is the standard on/off data-point for Tuya switches
        val dpJson = JSONObject().put("1", turnOn).toString()

        device.publishDps(dpJson, object : IResultCallback {
            override fun onError(code: String?, error: String?) {
                runOnUiThread {
                    isPublishing = false
                    setLoading(false)
                    // Revert the toggle to its previous state on error
                    binding.switchToggle.isChecked = !turnOn
                    Toast.makeText(
                        this@SwitchControlActivity,
                        "Command failed: $error",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onSuccess() {
                runOnUiThread {
                    isPublishing = false
                    setLoading(false)
                    // Actual state update arrives via onDpUpdate; nothing to do here
                }
            }
        })
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun applyDpString(dpStr: String) {
        try {
            val json = JSONObject(dpStr)
            if (json.has("1")) {
                applySwitchState(json.getBoolean("1"))
            }
        } catch (_: Exception) { /* malformed DP, ignore */ }
    }

    private fun applySwitchState(isOn: Boolean) {
        // Temporarily disable the listener to avoid re-triggering publishSwitch
        isPublishing = true
        binding.switchToggle.isChecked = isOn
        isPublishing = false
        binding.tvSwitchState.text = if (isOn) "ON" else "OFF"
        binding.tvSwitchState.setTextColor(
            getColor(if (isOn) R.color.state_on else R.color.state_off)
        )
    }

    private fun updateOnlineState(online: Boolean) {
        binding.tvOnlineStatus.text = if (online) "Online" else "Offline"
        binding.tvOnlineStatus.setTextColor(
            getColor(if (online) R.color.state_on else R.color.state_offline)
        )
        binding.switchToggle.isEnabled = online
        binding.tvOfflineHint.visibility = if (online) View.GONE else View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        device.unRegisterDevListener()
        device.onDestroy()
    }

    companion object {
        const val EXTRA_DEV_ID   = "extra_dev_id"
        const val EXTRA_DEV_NAME = "extra_dev_name"
    }
}
