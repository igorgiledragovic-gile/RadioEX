package com.gilespii.radioex.adapter

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.Category
import com.gilespii.radioex.R

class CategoryAdapter(
    private val categories: List<Category>,
    private val onCategorySelected: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var currentSelectedId: String = "all"

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return categories[position].id.hashCode().toLong()
    }

    fun setSelectedId(id: String) {
        if (currentSelectedId == id) return
        val oldIndex = categories.indexOfFirst { it.id == currentSelectedId }
        val newIndex = categories.indexOfFirst { it.id == id }
        currentSelectedId = id
        if (oldIndex >= 0) notifyItemChanged(oldIndex, "selection")
        if (newIndex >= 0) notifyItemChanged(newIndex, "selection")
    }

    fun getSelectedIndex(): Int = categories.indexOfFirst { it.id == currentSelectedId }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = categories[position]
        holder.label.text = cat.label
        updateSelectionState(holder, cat.id == currentSelectedId)

        holder.itemView.setOnClickListener {
            if (currentSelectedId != cat.id) {
                val oldIndex = categories.indexOfFirst { it.id == currentSelectedId }
                currentSelectedId = cat.id
                if (oldIndex >= 0) notifyItemChanged(oldIndex, "selection")
                notifyItemChanged(position, "selection")
                onCategorySelected(cat.id)
            }
        }

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.1f else 1f
            val density = v.resources.displayMetrics.density
            v.animate().scaleX(scale).scaleY(scale).setDuration(150)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            v.translationZ = if (hasFocus) 8f * density else 0f
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            updateSelectionState(holder, categories[position].id == currentSelectedId)
        }
    }

    private fun updateSelectionState(holder: VH, isSelected: Boolean) {
        val context = holder.itemView.context
        val accentColor = getThemeColor(context, R.attr.themeAccent)
        val onAccentColor = getThemeColor(context, R.attr.themeOnAccent)
        val dimColor = getThemeColor(context, R.attr.themeTextMuted)

        if (isSelected) {
            holder.label.setTextColor(onAccentColor)
            holder.label.setBackgroundResource(R.drawable.bg_pill_accent)
            holder.label.isSelected = true
        } else {
            holder.label.setTextColor(dimColor)
            holder.label.setBackgroundResource(R.drawable.bg_pill)
            holder.label.isSelected = false
        }
    }

    override fun getItemCount(): Int = categories.size

    private fun getThemeColor(context: Context, attrId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.tv_category)
    }
}
