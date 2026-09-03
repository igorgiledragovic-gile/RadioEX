package com.gilespii.radioex.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.R
import com.gilespii.radioex.util.Constants.ThemeConfig

class ThemeAdapter(
    private val context: Context,
    private val themes: List<ThemeConfig>,
    private val currentThemeId: String,
    private val onThemeSelected: (ThemeConfig) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    private var selectedPosition = themes.indexOfFirst { it.id == currentThemeId }.coerceAtLeast(0)

    var onNavigateLeft: (() -> Unit)? = null

    fun getSelectedPosition(): Int = selectedPosition

    companion object {
        val THEME_COLORS = mapOf(
            "midnightglass" to Pair(Color.parseColor("#5AA2FF"), Color.parseColor("#7FD4FF")),
            "neonwave" to Pair(Color.parseColor("#FF4FD8"), Color.parseColor("#29E0FF")),
            "aurora" to Pair(Color.parseColor("#34E0A1"), Color.parseColor("#56B8FF")),
            "retroanalog" to Pair(Color.parseColor("#E8973C"), Color.parseColor("#D9C08A")),
            "minimalmono" to Pair(Color.parseColor("#FFFFFF"), Color.parseColor("#C9C9D1")),
            "sunsetbento" to Pair(Color.parseColor("#FF7A59"), Color.parseColor("#FFB454")),
            "cybergrid" to Pair(Color.parseColor("#00FF9D"), Color.parseColor("#00C8FF")),
            "nordicfrost" to Pair(Color.parseColor("#2E7CC0"), Color.parseColor("#57A7D8")),
            "deepspace" to Pair(Color.parseColor("#8FA8FF"), Color.parseColor("#D5B3FF")),
            "vinylnoir" to Pair(Color.parseColor("#D9AC4A"), Color.parseColor("#F0D48A")),
            "pasteldream" to Pair(Color.parseColor("#C06BB5"), Color.parseColor("#7F8FE8")),
            "carbonfiber" to Pair(Color.parseColor("#E5484D"), Color.parseColor("#FF7A70")),
            "oceandepth" to Pair(Color.parseColor("#2DD4BF"), Color.parseColor("#56C8FF")),
            "solarflare" to Pair(Color.parseColor("#FF6A3D"), Color.parseColor("#FFC53D")),
            "forestmist" to Pair(Color.parseColor("#8FBF6F"), Color.parseColor("#D8B26E")),
            "urbanconcrete" to Pair(Color.parseColor("#46628C"), Color.parseColor("#A8663F")),
            "royalvelvet" to Pair(Color.parseColor("#E3B341"), Color.parseColor("#C77DFF")),
            "icecrystal" to Pair(Color.parseColor("#2B9FD8"), Color.parseColor("#7FD0F0")),
            "terracotta" to Pair(Color.parseColor("#C05F3C"), Color.parseColor("#A8763E")),
            "matrixpulse" to Pair(Color.parseColor("#00E05A"), Color.parseColor("#7DFFAB"))
        )
    }

    inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rootView: FrameLayout = itemView as FrameLayout
        val glowView: View = itemView.findViewById(R.id.view_glow)
        val primaryColorView: View = itemView.findViewById(R.id.view_color_primary)
        val secondaryColorView: View = itemView.findViewById(R.id.view_color_secondary)
        val themeName: TextView = itemView.findViewById(R.id.tv_theme_name)
        val selectedIndicator: ImageView = itemView.findViewById(R.id.iv_selected)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    selectTheme(position)
                }
            }

            itemView.setOnKeyListener { v, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                        keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        v.performClick()
                        return@setOnKeyListener true
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && (bindingAdapterPosition % 5 == 0)) {
                        onNavigateLeft?.invoke()
                        return@setOnKeyListener true
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        val pos = bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION && pos >= (itemCount - 5)) {
                            // On bottom row of grid: stay on current item, don't lose focus
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                updateFocusState(hasFocus)
            }
        }

        private fun updateFocusState(hasFocus: Boolean) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val theme = themes[position]
                val colors = THEME_COLORS[theme.id] ?: Pair(Color.GRAY, Color.LTGRAY)
                val accentColor = colors.first
                
                if (hasFocus) {
                    rootView.elevation = 2f
                    val focusedDrawable = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 16f
                        setStroke(4, accentColor)
                    }
                    rootView.background = focusedDrawable
                } else {
                    rootView.elevation = 0f
                    val isSelected = position == selectedPosition
                    val normalBorderColor = getThemeColor(R.attr.themeLine)
                    val drawable = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 16f
                        setColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.transparent))
                        setStroke(if (isSelected) 6 else 2, if (isSelected) accentColor else normalBorderColor)
                    }
                    rootView.background = drawable
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_theme_card, parent, false)
        return ThemeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val theme = themes[position]
        val colors = THEME_COLORS[theme.id] ?: Pair(Color.GRAY, Color.LTGRAY)

        holder.themeName.text = theme.name
        
        // Set glow effect behind preview circle
        val glowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(60, Color.red(colors.first), Color.green(colors.first), Color.blue(colors.first)))
        }
        holder.glowView.background = glowDrawable

        // Set primary color
        val primaryDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.first)
            setStroke(2, Color.WHITE)
        }
        holder.primaryColorView.background = primaryDrawable

        // Set secondary color
        val secondaryDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.second)
            setStroke(1, Color.WHITE)
        }
        holder.secondaryColorView.background = secondaryDrawable

        // Show/hide selected indicator
        holder.selectedIndicator.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE
        
        // Highlight selected item with accent border
        if (position == selectedPosition) {
            val accentColor = colors.first
            val selectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.transparent))
                setStroke(5, accentColor)
            }
            holder.rootView.background = selectedDrawable
            holder.themeName.setTextColor(accentColor)
        } else {
            val normalBorderColor = getThemeColor(R.attr.themeLine)
            val textColor = getThemeColor(R.attr.themeTextPrimary)
            val normalDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.transparent))
                setStroke(2, normalBorderColor)
            }
            holder.rootView.background = normalDrawable
            holder.themeName.setTextColor(textColor)
        }
    }

    override fun getItemCount(): Int = themes.size

    private fun getThemeColor(attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    private fun selectTheme(position: Int) {
        val previousSelected = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousSelected)
        notifyItemChanged(selectedPosition)
        onThemeSelected(themes[position])
    }
}
