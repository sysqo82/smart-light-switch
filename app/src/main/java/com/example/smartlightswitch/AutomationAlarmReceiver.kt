package com.example.smartlightswitch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IResultCallback
import org.json.JSONObject

/**
 * Receives alarms scheduled by [AutomationService] and executes the automation action.
 * Using AlarmManager ensures the timer fires even when the process is dead or in Doze mode.
 */
class AutomationAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutomationAlarmReceiver"
        private const val EXTRA_RULE_ID = "rule_id"

        /** Schedule an exact alarm for [rule] that will fire after [delayMs] milliseconds. */
        fun schedule(context: Context, rule: AutomationRule, delayMs: Long) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = buildPendingIntent(context, rule)
            val triggerAt = SystemClock.elapsedRealtime() + delayMs

            // On API 31+ exact alarms require the SCHEDULE_EXACT_ALARM permission to be granted
            // by the user via Settings. If it hasn't been, fall back to inexact (±5-15 min) so
            // the automation still fires rather than silently failing.
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                Log.d(TAG, "Exact alarm scheduled for rule '${rule.name}' in ${delayMs / 60_000} min")
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted – using inexact alarm for rule '${rule.name}'")
            }
        }

        /** Cancel a previously scheduled alarm for [rule]. */
        fun cancel(context: Context, rule: AutomationRule) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = buildPendingIntent(context, rule)
            am.cancel(pi)
            pi.cancel()
            Log.d(TAG, "Alarm cancelled for rule '${rule.name}'")
        }

        /** Returns whether an alarm is currently pending for [rule]. */
        fun isPending(context: Context, rule: AutomationRule): Boolean {
            return PendingIntent.getBroadcast(
                context,
                rule.id.hashCode(),
                Intent(context, AutomationAlarmReceiver::class.java).putExtra(EXTRA_RULE_ID, rule.id),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) != null
        }

        private fun buildPendingIntent(context: Context, rule: AutomationRule): PendingIntent {
            val intent = Intent(context, AutomationAlarmReceiver::class.java)
                .putExtra(EXTRA_RULE_ID, rule.id)
            return PendingIntent.getBroadcast(
                context,
                rule.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: run {
            Log.w(TAG, "Alarm fired with no rule_id"); return
        }
        val rule = AutomationRepository.getRules(context).find { it.id == ruleId } ?: run {
            Log.w(TAG, "Rule $ruleId not found – may have been deleted"); return
        }
        if (!rule.enabled) {
            Log.d(TAG, "Rule '${rule.name}' is disabled – skipping action"); return
        }

        Log.d(TAG, "Alarm fired for rule '${rule.name}' – executing action")

        val pendingResult = goAsync()
        val device = ThingHomeSdk.newDeviceInstance(rule.deviceId)
        val dpJson = JSONObject().put(rule.actionDp, rule.actionValue).toString()
        device.publishDps(dpJson, object : IResultCallback {
            override fun onSuccess() {
                Log.d(TAG, "Action for '${rule.name}' succeeded")
                pendingResult.finish()
            }
            override fun onError(code: String?, error: String?) {
                Log.e(TAG, "Action for '${rule.name}' failed: $error ($code)")
                pendingResult.finish()
            }
        })
    }
}
