package com.example.tradegatekeeper

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var addBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var maxRiskEdit: EditText
    private lateinit var delayEnabledCheck: CheckBox
    private lateinit var delaySecondsEdit: EditText

    private lateinit var adapter: SettingsChecklistAdapter
    private var items: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        recycler = findViewById(R.id.itemsRecycler)
        addBtn = findViewById(R.id.addItemBtn)
        saveBtn = findViewById(R.id.saveBtn)
        maxRiskEdit = findViewById(R.id.maxRiskEdit)
        delayEnabledCheck = findViewById(R.id.delayEnabledCheck)
        delaySecondsEdit = findViewById(R.id.delaySecondsEdit)

        recycler.layoutManager = LinearLayoutManager(this)

        items = Prefs.getChecklistItems(this)
        adapter = SettingsChecklistAdapter(
            items = items,
            onEdit = { idx -> showEditDialog(idx) },
            onDelete = { idx ->
                items.removeAt(idx)
                adapter.notifyItemRemoved(idx)
            },
        )
        recycler.adapter = adapter

        maxRiskEdit.setText(Prefs.getMaxRisk(this).toString())
        delayEnabledCheck.isChecked = Prefs.isDelayEnabled(this)
        delaySecondsEdit.setText(Prefs.getDelaySeconds(this).toString())

        addBtn.setOnClickListener { showAddDialog() }

        saveBtn.setOnClickListener {
            if (items.isEmpty()) {
                Toast.makeText(this, "Add at least 1 checklist item.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val maxRiskRaw = maxRiskEdit.text?.toString()?.trim().orEmpty()
            val maxRisk = maxRiskRaw.toDoubleOrNull()
            if (maxRisk == null || maxRisk <= 0.0) {
                Toast.makeText(this, "Max risk must be a number > 0.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val delaySec = delaySecondsEdit.text?.toString()?.trim().orEmpty().toIntOrNull()
            if (delayEnabledCheck.isChecked && (delaySec == null || delaySec < 0)) {
                Toast.makeText(this, "Delay seconds must be a valid number ≥ 0.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Prefs.setChecklistItems(this, items)
            Prefs.setMaxRisk(this, maxRisk)
            Prefs.setDelayEnabled(this, delayEnabledCheck.isChecked)
            if (delaySec != null) Prefs.setDelaySeconds(this, delaySec)

            // Any settings change should cancel an active countdown to prevent weird state.
            Prefs.clearActiveCountdown(this)

            Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = "Checklist item"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        AlertDialog.Builder(this)
            .setTitle("Add item")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) {
                    Toast.makeText(this, "Item cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                items.add(text)
                adapter.notifyItemInserted(items.lastIndex)
                recycler.scrollToPosition(items.lastIndex)
            }
            .show()
    }

    private fun showEditDialog(index: Int) {
        val current = items.getOrNull(index) ?: return
        val input = EditText(this).apply {
            setText(current)
            setSelection(current.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        AlertDialog.Builder(this)
            .setTitle("Edit item")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) {
                    Toast.makeText(this, "Item cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                items[index] = text
                adapter.notifyItemChanged(index)
            }
            .show()
    }
}
