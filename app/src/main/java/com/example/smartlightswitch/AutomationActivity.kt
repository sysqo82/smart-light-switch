package com.example.smartlightswitch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartlightswitch.databinding.ActivityAutomationBinding
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.bean.DeviceBean

class AutomationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutomationBinding
    private lateinit var adapter: RuleAdapter
    private var devices: List<DeviceBean> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAutomationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Automations"

        requestNotificationPermission()

        adapter = RuleAdapter(
            onDelete = { rule ->
                AlertDialog.Builder(this)
                    .setTitle("Delete rule")
                    .setMessage("Delete \"${rule.name}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        AutomationRepository.deleteRule(this, rule.id)
                        refreshList()
                        AutomationService.start(this)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onEdit = { rule -> showEditRuleDialog(rule) },
            onToggle = { rule, enabled ->
                AutomationRepository.updateRule(this, rule.copy(enabled = enabled))
                AutomationService.start(this)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAddRule.setOnClickListener {
            if (devices.isEmpty()) {
                Toast.makeText(this, "No devices loaded yet", Toast.LENGTH_SHORT).show()
            } else {
                showAddRuleDialog()
            }
        }

        loadDevices()
    }

    private fun loadDevices() {
        val homeId = intent.getLongExtra(EXTRA_HOME_ID, -1L)
        if (homeId == -1L) { refreshList(); return }

        ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
            override fun onSuccess(bean: HomeBean?) {
                devices = bean?.deviceList ?: emptyList()
                runOnUiThread { refreshList() }
            }
            override fun onError(code: String?, error: String?) {
                runOnUiThread { refreshList() }
            }
        })
    }

    private fun buildRuleDialog(existingRule: AutomationRule? = null): Pair<android.view.View, () -> AutomationRule?> {
        val view = layoutInflater.inflate(R.layout.dialog_add_rule, null)
        val etName     = view.findViewById<EditText>(R.id.etRuleName)
        val spinDevice = view.findViewById<Spinner>(R.id.spinnerDevice)
        val etMinutes  = view.findViewById<EditText>(R.id.etMinutes)
        val tvHint     = view.findViewById<TextView>(R.id.tvHint)

        val deviceNames = devices.map { it.name ?: it.devId }
        spinDevice.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, deviceNames)

        tvHint.text = "When the switch turns ON, it will automatically turn OFF after the " +
                "specified time. If the switch is turned OFF manually before the timer expires, " +
                "the countdown is cancelled. Turning ON again always starts a fresh countdown."

        // Pre-fill fields when editing
        if (existingRule != null) {
            etName.setText(existingRule.name)
            etMinutes.setText(existingRule.delayMinutes.toString())
            val idx = devices.indexOfFirst { it.devId == existingRule.deviceId }
            if (idx >= 0) spinDevice.setSelection(idx)
        } else {
            etMinutes.setText("10")
        }

        val build: () -> AutomationRule? = {
            val name    = etName.text.toString().trim().ifEmpty { "Auto off" }
            val idx     = spinDevice.selectedItemPosition
            if (idx < 0 || idx >= devices.size) null
            else {
                val device  = devices[idx]
                val minutes = etMinutes.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 10
                AutomationRule(
                    id           = existingRule?.id ?: java.util.UUID.randomUUID().toString(),
                    name         = name,
                    deviceId     = device.devId,
                    deviceName   = device.name ?: device.devId,
                    delayMinutes = minutes,
                    enabled      = existingRule?.enabled ?: true
                )
            }
        }
        return Pair(view, build)
    }

    private fun showAddRuleDialog() {
        val (view, build) = buildRuleDialog()
        AlertDialog.Builder(this)
            .setTitle("New rule")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val rule = build() ?: return@setPositiveButton
                AutomationRepository.addRule(this, rule)
                refreshList()
                AutomationService.start(this)
                Toast.makeText(this, "Rule \"${rule.name}\" added", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditRuleDialog(existing: AutomationRule) {
        val (view, build) = buildRuleDialog(existing)
        AlertDialog.Builder(this)
            .setTitle("Edit rule")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val rule = build() ?: return@setPositiveButton
                AutomationRepository.updateRule(this, rule)
                refreshList()
                AutomationService.start(this)
                Toast.makeText(this, "Rule updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshList() {
        val rules = AutomationRepository.getRules(this)
        adapter.submitList(rules)
        binding.tvEmpty.visibility = if (rules.isEmpty())
            android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_HOME_ID = "home_id"
    }
}
