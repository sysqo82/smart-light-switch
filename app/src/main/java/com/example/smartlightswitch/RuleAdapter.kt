package com.example.smartlightswitch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartlightswitch.databinding.ItemRuleBinding

class RuleAdapter(
    private val onDelete: (AutomationRule) -> Unit,
    private val onEdit: (AutomationRule) -> Unit,
    private val onToggle: (AutomationRule, Boolean) -> Unit
) : ListAdapter<AutomationRule, RuleAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val b: ItemRuleBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(rule: AutomationRule) {
            b.tvRuleName.text = rule.name
            b.tvRuleDesc.text = rule.describe()
            // Prevent the listener firing during bind
            b.switchEnabled.setOnCheckedChangeListener(null)
            b.switchEnabled.isChecked = rule.enabled
            b.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(rule, checked) }
            b.btnEdit.setOnClickListener { onEdit(rule) }
            b.btnDelete.setOnClickListener { onDelete(rule) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AutomationRule>() {
            override fun areItemsTheSame(a: AutomationRule, b: AutomationRule) = a.id == b.id
            override fun areContentsTheSame(a: AutomationRule, b: AutomationRule) = a == b
        }
    }
}
