package com.example.smartlightswitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IThingDevice
import org.json.JSONObject

/**
 * Foreground service that monitors Tuya device DP events and runs local automation rules.
 *
 * Key behaviour:
 *  - When trigger fires → cancel any existing timer for that rule → start a fresh countdown
 *  - When cancel condition fires before the timer expires → cancel the timer
 *  - Timers survive process death and Doze mode (backed by AlarmManager)
 *  - Active alarm IDs are tracked in-memory so the notification counter stays accurate
 */
class AutomationService : Service() {

    // ruleId → true when an alarm is pending (used only for notification counter)
    private val pendingTimers = HashSet<String>()

    // devId   →  IThingDevice instance with a registered listener
    private val deviceInstances = HashMap<String, IThingDevice>()

    companion object {
        private const val TAG = "AutomationService"
        private const val CHANNEL_ID = "automation_channel"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AutomationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, AutomationService::class.java))
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        setupListeners()
    }

    /** Called on start AND whenever rules change (caller restarts the service). */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupListeners()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingTimers.clear()
        deviceInstances.values.forEach { runCatching { it.unRegisterDevListener() } }
        deviceInstances.clear()
    }

    // ── Listener setup ─────────────────────────────────────────────────────────

    private fun setupListeners() {
        // Tear down old listeners before re-registering
        deviceInstances.values.forEach { runCatching { it.unRegisterDevListener() } }
        deviceInstances.clear()

        val enabledRules = AutomationRepository.getRules(this).filter { it.enabled }
        val watchedDevIds = enabledRules.map { it.deviceId }.toSet()

        for (devId in watchedDevIds) {
            val instance = ThingHomeSdk.newDeviceInstance(devId)
            instance.registerDevListener(object : IDevListener {
                override fun onDpUpdate(id: String?, dpStr: String?) {
                    if (id != null && dpStr != null) handleDpUpdate(id, dpStr)
                }
                override fun onRemoved(id: String?) {}
                override fun onStatusChanged(id: String?, online: Boolean) {}
                override fun onNetworkStatusChanged(id: String?, status: Boolean) {}
                override fun onDevInfoUpdate(id: String?) {}
            })
            deviceInstances[devId] = instance
            Log.d(TAG, "Watching device $devId")
        }

        updateNotification(enabledRules.size, pendingTimers.size)
    }

    // ── DP event handling ──────────────────────────────────────────────────────

    private fun handleDpUpdate(devId: String, dpStr: String) {
        val dpMap: Map<String, Any> = try {
            val obj = JSONObject(dpStr)
            obj.keys().asSequence().associateWith { obj.get(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse dpStr: $dpStr"); return
        }

        val rules = AutomationRepository.getRules(this)
            .filter { it.enabled && it.deviceId == devId }

        for (rule in rules) {
            val rawValue = dpMap[rule.triggerDp] ?: continue
            val dpBool = rawValue.asBooleanOrNull() ?: continue

            when {
                // ── Trigger fired → restart timer ──────────────────────────
                dpBool == rule.triggerValue -> {
                    cancelTimer(rule)
                    val delayMs = rule.delayMinutes * 60_000L
                    Log.d(TAG, "Rule '${rule.name}' triggered. Timer: ${rule.delayMinutes} min")
                    AutomationAlarmReceiver.schedule(this, rule, delayMs)
                    pendingTimers.add(rule.id)
                    updateNotification(
                        AutomationRepository.getRules(this).count { it.enabled },
                        pendingTimers.size
                    )
                }

                // ── Cancel condition met → stop timer ──────────────────────
                dpBool == rule.cancelValue && rule.cancelDp == rule.triggerDp
                        && pendingTimers.contains(rule.id) -> {
                    Log.d(TAG, "Rule '${rule.name}' cancelled (device changed before timer)")
                    cancelTimer(rule)
                    updateNotification(
                        AutomationRepository.getRules(this).count { it.enabled },
                        pendingTimers.size
                    )
                }
            }
        }
    }

    private fun cancelTimer(rule: AutomationRule) {
        AutomationAlarmReceiver.cancel(this, rule)
        pendingTimers.remove(rule.id)
    }

    // ── Notification ───────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Automations",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Running local automation rules" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(activeRules: Int = 0, activeTimers: Int = 0): Notification {
        val text = when {
            activeTimers > 0 -> "$activeRules rule(s) active · $activeTimers timer(s) running"
            activeRules > 0  -> "$activeRules rule(s) active"
            else             -> "No active rules"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Automations")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(activeRules: Int, activeTimers: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(activeRules, activeTimers))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun Any.asBooleanOrNull(): Boolean? = when (this) {
        is Boolean -> this
        is String  -> when (lowercase()) { "true" -> true; "false" -> false; else -> null }
        else       -> null
    }
}
