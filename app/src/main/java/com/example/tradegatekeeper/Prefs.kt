package com.example.tradegatekeeper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val PREFS = "trade_gatekeeper_prefs"

    private const val KEY_CHECKLIST_ITEMS_JSON = "checklist_items_json"
    private const val KEY_MAX_RISK = "max_risk"
    private const val KEY_DELAY_ENABLED = "delay_enabled"
    private const val KEY_DELAY_SECONDS = "delay_seconds"

    private const val KEY_ACTIVE_COUNTDOWN_END_MS = "active_countdown_end_ms"

    private const val KEY_TRADE_LOG_JSON = "trade_log_json"

    fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getChecklistItems(context: Context): MutableList<String> {
        val raw = sp(context).getString(KEY_CHECKLIST_ITEMS_JSON, null)
        if (raw.isNullOrBlank()) {
            return mutableListOf(
                "Higher timeframe bias confirmed",
                "Setup matches my strategy rules",
                "Entry location is valid (PD array / level / FVG)",
                "Stop loss is defined",
                "Risk ≤ predefined %",
                "News checked (no high-impact news)",
                "Emotional state is calm (no revenge trading)",
            )
        }
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { idx -> arr.getString(idx) }
        } catch (_: Exception) {
            mutableListOf(
                "Higher timeframe bias confirmed",
                "Setup matches my strategy rules",
                "Entry location is valid (PD array / level / FVG)",
                "Stop loss is defined",
                "Risk ≤ predefined %",
                "News checked (no high-impact news)",
                "Emotional state is calm (no revenge trading)",
            )
        }
    }

    fun setChecklistItems(context: Context, items: List<String>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        sp(context).edit().putString(KEY_CHECKLIST_ITEMS_JSON, arr.toString()).apply()
    }

    fun getMaxRisk(context: Context): Double = sp(context).getFloat(KEY_MAX_RISK, 1.0f).toDouble()

    fun setMaxRisk(context: Context, value: Double) {
        sp(context).edit().putFloat(KEY_MAX_RISK, value.toFloat()).apply()
    }

    fun isDelayEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_DELAY_ENABLED, true)

    fun setDelayEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_DELAY_ENABLED, enabled).apply()
    }

    fun getDelaySeconds(context: Context): Int = sp(context).getInt(KEY_DELAY_SECONDS, 30)

    fun setDelaySeconds(context: Context, seconds: Int) {
        sp(context).edit().putInt(KEY_DELAY_SECONDS, seconds.coerceAtLeast(0)).apply()
    }

    fun getActiveCountdownEndMs(context: Context): Long = sp(context).getLong(KEY_ACTIVE_COUNTDOWN_END_MS, 0L)

    fun setActiveCountdownEndMs(context: Context, endMs: Long) {
        sp(context).edit().putLong(KEY_ACTIVE_COUNTDOWN_END_MS, endMs).apply()
    }

    fun clearActiveCountdown(context: Context) {
        sp(context).edit().remove(KEY_ACTIVE_COUNTDOWN_END_MS).apply()
    }

    fun appendTradeLog(context: Context, timestampMs: Long, riskPct: Double, reason: String) {
        val raw = sp(context).getString(KEY_TRADE_LOG_JSON, null)
        val arr = try {
            if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }

        val obj = JSONObject()
            .put("timestampMs", timestampMs)
            .put("riskPct", riskPct)
            .put("reason", reason)

        arr.put(obj)
        sp(context).edit().putString(KEY_TRADE_LOG_JSON, arr.toString()).apply()
    }
}
