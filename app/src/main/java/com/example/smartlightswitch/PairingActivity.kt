package com.example.smartlightswitch

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.smartlightswitch.databinding.ActivityPairingBinding
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.builder.ActivatorBuilder
import com.thingclips.smart.sdk.api.IThingActivator
import com.thingclips.smart.sdk.api.IThingActivatorGetToken
import com.thingclips.smart.sdk.api.IThingSmartActivatorListener
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thingclips.smart.sdk.enums.ActivatorModelEnum

/**
 * Pairs a new switch via Wi-Fi EZ mode — the same flow shown when you tap
 * "Add Device" in the SmartLife app. The switch must already be in pairing mode
 * (hold the physical button until it blinks rapidly).
 */
class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding
    private var activator: IThingActivator? = null
    private val homeId by lazy { intent.getLongExtra(EXTRA_HOME_ID, -1L) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Add Device"

        // Pre-fill with the connected Wi-Fi SSID if permission is already granted
        preFillSsid()

        binding.btnStartPairing.setOnClickListener {
            if (checkLocationPermission()) fetchTokenThenPair() else requestLocationPermission()
        }
    }

    // ── Pairing ───────────────────────────────────────────────────────────────

    private fun fetchTokenThenPair() {
        val ssid     = binding.etSsid.text.toString().trim()
        val password = binding.etWifiPassword.text.toString()

        if (ssid.isEmpty()) {
            binding.etSsid.error = "Enter your 2.4 GHz Wi-Fi name"
            return
        }
        if (homeId == -1L) {
            Toast.makeText(this, "No home ID — go back and try again.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        updateStatus("Getting pairing token…")

        ThingHomeSdk.getActivatorInstance().getActivatorToken(homeId,
            object : IThingActivatorGetToken {
                override fun onSuccess(token: String) {
                    runOnUiThread { startPairing(ssid, password, token) }
                }
                override fun onFailure(errorCode: String?, errorMsg: String?) {
                    runOnUiThread {
                        setLoading(false)
                        updateStatus("Failed to get token: $errorMsg")
                    }
                }
            }
        )
    }

    private fun startPairing(ssid: String, password: String, token: String) {
        updateStatus("Searching for nearby devices…")

        val builder = ActivatorBuilder()
            .setSsid(ssid)
            .setContext(this)
            .setPassword(password)
            .setActivatorModel(ActivatorModelEnum.THING_EZ)
            .setTimeOut(PAIRING_TIMEOUT_SECONDS.toLong())
            .setToken(token)
            .setListener(object : IThingSmartActivatorListener {
                override fun onError(errorCode: String?, errorMsg: String?) {
                    runOnUiThread {
                        setLoading(false)
                        updateStatus("Pairing failed: $errorMsg")
                        Toast.makeText(this@PairingActivity, "Error $errorCode: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onActiveSuccess(devResp: DeviceBean?) {
                    runOnUiThread {
                        setLoading(false)
                        updateStatus("✓ Device added: ${devResp?.name}")
                        Toast.makeText(this@PairingActivity, "Device added successfully!", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                }

                override fun onStep(step: String?, data: Any?) {
                    runOnUiThread { updateStatus(step ?: "…") }
                }
            })

        activator = ThingHomeSdk.getActivatorInstance().newMultiActivator(builder) as? IThingActivator
        activator?.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun preFillSsid() {
        if (!checkLocationPermission()) return
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ssid = wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
        if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
            binding.etSsid.setText(ssid)
        }
    }

    private fun updateStatus(msg: String) {
        binding.tvPairingStatus.text = msg
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility  = if (loading) View.VISIBLE else View.GONE
        binding.btnStartPairing.isEnabled = !loading
    }

    private fun checkLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            preFillSsid()
            fetchTokenThenPair()
        } else {
            Toast.makeText(this, "Location permission is required for Wi-Fi pairing", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        super.onDestroy()
        activator?.stop()
        activator?.onDestroy()
    }

    companion object {
        const val EXTRA_HOME_ID          = "extra_home_id"
        private const val PAIRING_TIMEOUT_SECONDS = 100
        private const val REQ_LOCATION            = 2001
    }
}
