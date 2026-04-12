package com.example.smartlightswitch

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartlightswitch.databinding.ActivityHomeBinding
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.sdk.api.IThingDevice
import com.thingclips.smart.sdk.bean.DeviceBean

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var adapter: DeviceAdapter
    private var currentHomeId: Long = -1L
    private val deviceInstances = HashMap<String, IThingDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Push FAB above the navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddDevice) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = view.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            val margin16dp = (16 * resources.displayMetrics.density).toInt()
            lp.bottomMargin = margin16dp + navBar.bottom
            view.layoutParams = lp
            insets
        }

        adapter = DeviceAdapter(
            onClick = { device -> openSwitchControl(device) },
            onLongClick = { device -> showRenameDialog(device) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAddDevice.setOnClickListener {
            if (currentHomeId != -1L) {
                startActivityForResult(
                    Intent(this, PairingActivity::class.java)
                        .putExtra(PairingActivity.EXTRA_HOME_ID, currentHomeId),
                    REQUEST_PAIRING
                )
            } else {
                Toast.makeText(this, "No home found. Please wait…", Toast.LENGTH_SHORT).show()
            }
        }

        loadHomes()
    }

    private fun loadHomes() {
        setLoading(true)
        ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
            override fun onSuccess(homeBeans: MutableList<HomeBean>?) {
                val home = homeBeans?.firstOrNull()
                if (home == null) {
                    createDefaultHome()
                    return
                }
                currentHomeId = home.homeId
                loadDevices(home.homeId)
            }

            override fun onError(errorCode: String?, error: String?) {
                runOnUiThread {
                    setLoading(false)
                    showError("Failed to load homes: $error")
                }
            }
        })
    }

    private fun createDefaultHome() {
        ThingHomeSdk.getHomeManagerInstance().createHome(
            "My Home", 0.0, 0.0, "Home", emptyList(),
            object : IThingHomeResultCallback {
                override fun onSuccess(homeBean: HomeBean?) { loadHomes() }
                override fun onError(code: String?, error: String?) {
                    runOnUiThread {
                        setLoading(false)
                        showEmpty("Could not create home: $error")
                    }
                }
            }
        )
    }

    private fun loadDevices(homeId: Long) {
        ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
            override fun onSuccess(homeBean: HomeBean?) {
                val devices = homeBean?.deviceList ?: emptyList()
                runOnUiThread {
                    setLoading(false)
                    if (devices.isEmpty()) {
                        showEmpty("No devices found. Tap + to add a device.")
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        adapter.submitList(devices)
                        registerDeviceListeners(devices)
                    }
                    // Start automation service now that devices are known
                    AutomationService.start(this@HomeActivity)
                }
            }

            override fun onError(errorCode: String?, error: String?) {
                runOnUiThread {
                    setLoading(false)
                    showError("Failed to load devices: $error")
                }
            }
        })
    }

    private fun showRenameDialog(device: DeviceBean) {
        val input = EditText(this).apply {
            setText(device.name)
            selectAll()
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename device")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                ThingHomeSdk.newDeviceInstance(device.devId)
                    .renameDevice(newName, object : IResultCallback {
                        override fun onSuccess() {
                            runOnUiThread {
                                Toast.makeText(this@HomeActivity, "Renamed to \"$newName\"", Toast.LENGTH_SHORT).show()
                                loadDevices(currentHomeId)
                            }
                        }
                        override fun onError(code: String?, error: String?) {
                            runOnUiThread { Toast.makeText(this@HomeActivity, "Rename failed: $error", Toast.LENGTH_SHORT).show() }
                        }
                    })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openSwitchControl(device: DeviceBean) {
        startActivity(
            Intent(this, SwitchControlActivity::class.java)
                .putExtra(SwitchControlActivity.EXTRA_DEV_ID, device.devId)
                .putExtra(SwitchControlActivity.EXTRA_DEV_NAME, device.name)
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_automations -> {
                startActivity(
                    Intent(this, AutomationActivity::class.java)
                        .putExtra(AutomationActivity.EXTRA_HOME_ID, currentHomeId)
                )
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            R.id.action_refresh -> {
                if (currentHomeId != -1L) {
                    setLoading(true)
                    loadDevices(currentHomeId)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout() {
        ThingHomeSdk.getUserInstance().logout(object : com.thingclips.smart.android.user.api.ILogoutCallback {
            override fun onSuccess() {
                runOnUiThread {
                    startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                    finishAffinity()
                }
            }
            override fun onError(code: String?, error: String?) {
                runOnUiThread { Toast.makeText(this@HomeActivity, "Logout failed: $error", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PAIRING && resultCode == RESULT_OK) {
            // Refresh device list after successful pairing
            loadDevices(currentHomeId)
        }
    }

    private fun registerDeviceListeners(devices: List<DeviceBean>) {
        deviceInstances.values.forEach { it.unRegisterDevListener() }
        deviceInstances.clear()
        devices.forEach { bean ->
            val instance = ThingHomeSdk.newDeviceInstance(bean.devId)
            instance.registerDevListener(object : IDevListener {
                override fun onDpUpdate(devId: String, dpStr: String) {
                    runOnUiThread { refreshDeviceInList(devId) }
                }
                override fun onStatusChanged(devId: String, online: Boolean) {
                    runOnUiThread { refreshDeviceInList(devId) }
                }
                override fun onRemoved(devId: String) {
                    runOnUiThread { loadDevices(currentHomeId) }
                }
                override fun onNetworkStatusChanged(devId: String, status: Boolean) {}
                override fun onDevInfoUpdate(devId: String) {
                    runOnUiThread { refreshDeviceInList(devId) }
                }
            })
            deviceInstances[bean.devId] = instance
        }
    }

    private fun refreshDeviceInList(devId: String) {
        val fresh = ThingHomeSdk.getDataInstance().getDeviceBean(devId) ?: return
        val updated = adapter.currentList.toMutableList()
        val idx = updated.indexOfFirst { it.devId == devId }
        if (idx >= 0) {
            updated[idx] = fresh
            adapter.submitList(updated.toList())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceInstances.values.forEach { it.unRegisterDevListener() }
        deviceInstances.clear()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showEmpty(message: String) {
        binding.tvEmpty.text = message
        binding.tvEmpty.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_PAIRING = 1001
    }
}
