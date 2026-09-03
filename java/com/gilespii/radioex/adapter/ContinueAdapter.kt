package com.gilespii.radioex.adapter

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.R
import com.gilespii.radioex.RadioManager
import com.gilespii.radioex.RadioStation

import com.gilespii.radioex.util.StationLogoCache

class ContinueAdapter(
    private val onStationClick: (RadioStation) -> Unit
) : RecyclerView.Adapter<ContinueAdapter.VH>() {

    companion object {
        private val FOCUS_INTERPOLATOR = android.view.animation.DecelerateInterpolator(1.5f)
    }

    private var stations: List<RadioStation> = emptyList()
    var cardWidthPx: Int = 0
        set(value) {
            if (field != value && value > 0) {
                field = value
                notifyDataSetChanged()
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return if (position in stations.indices) stations[position].id.toLong() else RecyclerView.NO_ID
    }

    fun submitList(list: List<RadioStation>) {
        stations = list
        notifyDataSetChanged()
    }

    fun getIndexForStation(stationId: Int?): Int {
        if (stationId == null) return -1
        return stations.indexOfFirst { it.id == stationId }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_continue, parent, false)
        applyTwoColumnWidth(v, parent)
        return VH(v)
    }

    private fun applyTwoColumnWidth(view: View, parent: ViewGroup) {
        val density = view.resources.displayMetrics.density
        val marginEndPx = (12f * density).toInt()
        val cardWidth = if (cardWidthPx > 0) {
            cardWidthPx
        } else if (parent.measuredWidth > 0) {
            val colWidth = parent.measuredWidth / 7f
            (2 * colWidth - marginEndPx).toInt()
        } else {
            (225f * density).toInt()
        }

        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
            ?: ViewGroup.MarginLayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (lp.width != cardWidth || lp.marginEnd != marginEndPx) {
            lp.width = cardWidth
            lp.marginEnd = marginEndPx
            view.layoutParams = lp
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val st = stations[position]
        val ctx = holder.itemView.context
        val isCurrent = RadioManager.currentStation?.id == st.id
        val isPlaying = isCurrent && RadioManager.isPlaying()

        holder.name.text = st.name
        if (st.imageResId != 0) {
            val bmp = StationLogoCache.getBitmap(ctx, st.imageResId)
            if (bmp != null) {
                holder.logo.setImageBitmap(bmp)
            } else {
                holder.logo.setImageResource(st.imageResId)
            }
        } else {
            holder.logo.setImageDrawable(null)
        }

        if (isCurrent && isPlaying) {
            holder.eqBars.visibility = View.VISIBLE
            holder.ivPlay.visibility = View.GONE
            holder.tvStatus.text = ctx.getString(R.string.now_playing)
            val accentColor = resolveThemeColor(holder.itemView, R.attr.themeAccent)
            holder.tvStatus.setTextColor(accentColor)
        } else {
            holder.eqBars.visibility = View.GONE
            holder.ivPlay.visibility = View.VISIBLE
            holder.tvStatus.text = ctx.getString(R.string.continue_action)
            val dimColor = resolveThemeColor(holder.itemView, R.attr.themeTextMuted)
            holder.tvStatus.setTextColor(dimColor)
        }

        holder.itemView.setOnClickListener { onStationClick(st) }

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1f
            val density = v.resources.displayMetrics.density
            v.animate().scaleX(scale).scaleY(scale).setDuration(150)
                .setInterpolator(FOCUS_INTERPOLATOR)
                .start()
            v.translationZ = if (hasFocus) 8f * density else 0f
        }
    }

    override fun getItemCount(): Int = stations.size

    private fun resolveThemeColor(view: View, attr: Int): Int {
        val tv = TypedValue()
        view.context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.iv_continue_logo)
        val name: TextView = v.findViewById(R.id.tv_continue_name)
        val eqBars: LinearLayout = v.findViewById(R.id.eq_bars)
        val ivPlay: ImageView = v.findViewById(R.id.iv_continue_play)
        val tvStatus: TextView = v.findViewById(R.id.tv_continue_status)
    }
}
