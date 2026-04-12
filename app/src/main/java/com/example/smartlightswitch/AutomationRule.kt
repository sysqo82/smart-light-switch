package com.example.smartlightswitch

import org.json.JSONObject
import java.util.UUID

data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val deviceId: String,
    val deviceName: String,
    // Trigger: fire when this DP changes to triggerValue
    val triggerDp: String = "1",
    val triggerValue: Boolean = true,
    // How long to wait before executing the action
    val delayMinutes: Int,
    // Action: set this DP to actionValue after the delay
    val actionDp: String = "1",
    val actionValue: Boolean = false,
    // Cancel: if this DP changes to cancelValue before the timer fires, cancel it
    val cancelDp: String = "1",
    val cancelValue: Boolean = false,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("deviceId", deviceId)
        put("deviceName", deviceName)
        put("triggerDp", triggerDp)
        put("triggerValue", triggerValue)
        put("delayMinutes", delayMinutes)
        put("actionDp", actionDp)
        put("actionValue", actionValue)
        put("cancelDp", cancelDp)
        put("cancelValue", cancelValue)
        put("enabled", enabled)
    }

    fun describe(): String {
        val trigger = if (triggerValue) "turns ON" else "turns OFF"
        val action = if (actionValue) "turn ON" else "turn OFF"
        return "When $deviceName $trigger → $action after $delayMinutes min"
    }

    companion object {
        fun fromJson(json: JSONObject) = AutomationRule(
            id          = json.getString("id"),
            name        = json.getString("name"),
            deviceId    = json.getString("deviceId"),
            deviceName  = json.getString("deviceName"),
            triggerDp   = json.optString("triggerDp", "1"),
            triggerValue = json.optBoolean("triggerValue", true),
            delayMinutes = json.getInt("delayMinutes"),
            actionDp    = json.optString("actionDp", "1"),
            actionValue  = json.optBoolean("actionValue", false),
            cancelDp    = json.optString("cancelDp", "1"),
            cancelValue  = json.optBoolean("cancelValue", false),
            enabled     = json.optBoolean("enabled", true)
        )
    }
}
