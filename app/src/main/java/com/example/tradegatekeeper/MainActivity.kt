package com.example.tradegatekeeper

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    // MetaTrader 5 Android package
    private val mt5Package = "net.metaquotes.metatrader5"

    private var pendingRestoreChecked: BooleanArray? = null

    private lateinit var checklistRecycler: RecyclerView
    private lateinit var riskEdit: EditText
    private lateinit var reasonEdit: EditText
    private lateinit var countdownText: TextView
    private lateinit var openBtn: Button
    private lateinit var settingsBtn: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    private var checklistItems: MutableList<String> = mutableListOf()
    private var checked: BooleanArray = BooleanArray(0)
    private var adapter: ChecklistAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pendingRestoreChecked = savedInstanceState?.getBooleanArray(STATE_CHECKED)

        checklistRecycler = findViewById(R.id.checklistRecycler)
        riskEdit = findViewById(R.id.riskEdit)
        reasonEdit = findViewById(R.id.reasonEdit)
        countdownText = findViewById(R.id.countdownText)
        openBtn = findViewById(R.id.openMt5Btn)
        settingsBtn = findViewById(R.id.btnSettings)

        // Prevent bypass via back button.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally do nothing.
                Toast.makeText(
                    this@MainActivity,
                    "Back disabled. Finish the checklist.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        checklistRecycler.layoutManager = LinearLayoutManager(this)

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        openBtn.setOnClickListener {
            val risk = parseRiskPct() ?: return@setOnClickListener
            val reason = reasonEdit.text?.toString()?.trim().orEmpty()
            if (!isReasonValid(reason)) {
                Toast.makeText(this, "Reason must be at least 20 characters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Store log before launching.
            Prefs.appendTradeLog(
                context = this,
                timestampMs = System.currentTimeMillis(),
                riskPct = risk,
                reason = reason,
            )

            Prefs.clearActiveCountdown(this)
            stopTicker()

            launchMt5OrShowError()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // If user edits something during countdown, we re-check and (if needed) cancel.
                reevaluateGate()
            }
        }
        riskEdit.addTextChangedListener(watcher)
        reasonEdit.addTextChangedListener(watcher)

        // Restore text inputs on rotation.
        savedInstanceState?.getString(STATE_RISK)?.let { riskEdit.setText(it) }
        savedInstanceState?.getString(STATE_REASON)?.let { reasonEdit.setText(it) }
    }

    override fun onResume() {
        super.onResume()

        val latestItems = Prefs.getChecklistItems(this)
        val shouldReset = checklistItems.isEmpty() || latestItems.size != checklistItems.size || latestItems != checklistItems

        if (shouldReset) {
            checklistItems = latestItems
            checked = BooleanArray(checklistItems.size) { false }
            Prefs.clearActiveCountdown(this)
        } else if (checked.size != checklistItems.size) {
            checked = BooleanArray(checklistItems.size) { false }
            Prefs.clearActiveCountdown(this)
        }

        // Apply rotation restore if sizes match.
        pendingRestoreChecked?.let { restored ->
            if (restored.size == checked.size) {
                for (i in restored.indices) checked[i] = restored[i]
            }
            pendingRestoreChecked = null
        }

        adapter = ChecklistAdapter(checklistItems, checked) { reevaluateGate() }
        checklistRecycler.adapter = adapter

        // Restore countdown if it was running while app minimized/rotated.
        val endMs = Prefs.getActiveCountdownEndMs(this)
        if (endMs > System.currentTimeMillis()) {
            startTicker(endMs)
        } else {
            Prefs.clearActiveCountdown(this)
            stopTicker()
            countdownText.text = ""
        }

        reevaluateGate()
    }

    override fun onPause() {
        super.onPause()
        // Keep ticker running logically via endMs (stored). Runnable can be stopped to avoid leaks.
        stopTicker()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBooleanArray(STATE_CHECKED, checked)
        outState.putString(STATE_RISK, riskEdit.text?.toString())
        outState.putString(STATE_REASON, reasonEdit.text?.toString())
    }

    private fun allChecklistChecked(): Boolean = checked.isNotEmpty() && checked.all { it }

    private fun parseRiskPct(): Double? {
        val raw = riskEdit.text?.toString()?.trim().orEmpty()
        if (raw.isBlank()) {
            Toast.makeText(this, "Risk is required.", Toast.LENGTH_SHORT).show()
            return null
        }
        val v = raw.toDoubleOrNull()
        if (v == null || v.isNaN() || v.isInfinite()) {
            Toast.makeText(this, "Risk must be a number.", Toast.LENGTH_SHORT).show()
            return null
        }
        if (v < 0.0) {
            Toast.makeText(this, "Risk cannot be negative.", Toast.LENGTH_SHORT).show()
            return null
        }
        val max = Prefs.getMaxRisk(this)
        if (v > max) {
            Toast.makeText(this, "Risk must be ≤ ${formatPct(max)}.", Toast.LENGTH_SHORT).show()
            return null
        }
        return v
    }

    private fun isReasonValid(reason: String): Boolean = reason.trim().length >= 20

    private fun conditionsMetWithoutCountdown(): Boolean {
        val riskOk = run {
            val raw = riskEdit.text?.toString()?.trim().orEmpty()
            val v = raw.toDoubleOrNull() ?: return@run false
            v >= 0.0 && v <= Prefs.getMaxRisk(this)
        }
        val reasonOk = isReasonValid(reasonEdit.text?.toString()?.trim().orEmpty())
        val checklistOk = allChecklistChecked()
        return riskOk && reasonOk && checklistOk
    }

    private fun reevaluateGate() {
        val delayEnabled = Prefs.isDelayEnabled(this)
        val endMs = Prefs.getActiveCountdownEndMs(this)

        // If user invalidates something mid-countdown, cancel the countdown.
        if (!conditionsMetWithoutCountdown()) {
            Prefs.clearActiveCountdown(this)
            stopTicker()
            countdownText.text = ""
            openBtn.isEnabled = false
            return
        }

        if (!delayEnabled) {
            Prefs.clearActiveCountdown(this)
            stopTicker()
            countdownText.text = ""
            openBtn.isEnabled = true
            return
        }

        val now = System.currentTimeMillis()
        if (endMs <= now) {
            // Start a fresh countdown when all conditions first become valid.
            val seconds = Prefs.getDelaySeconds(this)
            val newEnd = now + seconds * 1000L
            Prefs.setActiveCountdownEndMs(this, newEnd)
            startTicker(newEnd)
            openBtn.isEnabled = false
        } else {
            // Countdown in progress.
            startTicker(endMs)
            openBtn.isEnabled = false
        }
    }

    private fun startTicker(endMs: Long) {
        if (ticker != null) return

        val r = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val remainingMs = (endMs - now).coerceAtLeast(0L)
                val remainingSec = (remainingMs + 999L) / 1000L

                if (remainingMs <= 0L) {
                    countdownText.text = "Ready."
                    Prefs.clearActiveCountdown(this@MainActivity)
                    stopTicker()
                    // Only enable if conditions still met.
                    openBtn.isEnabled = conditionsMetWithoutCountdown()
                    return
                }

                countdownText.text = "Wait: ${remainingSec}s"
                openBtn.isEnabled = false
                mainHandler.postDelayed(this, 250L)
            }
        }

        ticker = r
        mainHandler.post(r)
    }

    private fun stopTicker() {
        ticker?.let { mainHandler.removeCallbacks(it) }
        ticker = null
    }

    private fun launchMt5OrShowError() {
        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage(mt5Package)

        if (launchIntent != null) {
            // Explicit intent to MT5 app.
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            return
        }

        // Fallback: try Play Store.
        Toast.makeText(this, "MetaTrader 5 is not installed.", Toast.LENGTH_LONG).show()
        try {
            val uri = Uri.parse("market://details?id=$mt5Package")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            // No Play Store: do nothing else.
        }
    }

    private fun formatPct(v: Double): String {
        return if (v % 1.0 == 0.0) {
            "${v.toInt()}%"
        } else {
            "${String.format("%.2f", v)}%"
        }
    }

    private companion object {
        private const val STATE_CHECKED = "state_checked"
        private const val STATE_RISK = "state_risk"
        private const val STATE_REASON = "state_reason"
    }
}
