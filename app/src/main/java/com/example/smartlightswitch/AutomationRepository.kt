package com.example.smartlightswitch

import android.content.Context
import org.json.JSONArray

object AutomationRepository {

    private const val PREFS = "automations"
    private const val KEY_RULES = "rules"

    fun getRules(context: Context): List<AutomationRule> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RULES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { AutomationRule.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun saveRules(context: Context, rules: List<AutomationRule>) {
        val arr = JSONArray().also { a -> rules.forEach { a.put(it.toJson()) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun addRule(context: Context, rule: AutomationRule) =
        saveRules(context, getRules(context) + rule)

    fun deleteRule(context: Context, ruleId: String) =
        saveRules(context, getRules(context).filter { it.id != ruleId })

    fun updateRule(context: Context, rule: AutomationRule) =
        saveRules(context, getRules(context).map { if (it.id == rule.id) rule else it })
}
