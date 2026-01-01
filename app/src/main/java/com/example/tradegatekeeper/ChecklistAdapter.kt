package com.example.tradegatekeeper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView

class ChecklistAdapter(
    private var items: List<String>,
    private val checked: BooleanArray,
    private val onChanged: () -> Unit,
) : RecyclerView.Adapter<ChecklistAdapter.VH>() {

    init {
        require(checked.size == items.size) { "checked[] size must match items size" }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val check: CheckBox = itemView.findViewById(R.id.check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_checklist, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.check.setOnCheckedChangeListener(null)
        holder.check.text = items[position]
        holder.check.isChecked = checked[position]
        holder.check.setOnCheckedChangeListener { _, isChecked ->
            checked[position] = isChecked
            onChanged()
        }
    }

    fun update(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }
}
