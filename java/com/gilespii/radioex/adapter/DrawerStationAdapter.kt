package com.gilespii.radioex.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gilespii.radioex.R
import com.gilespii.radioex.RadioStation

class DrawerStationAdapter(
    private val stations: List<RadioStation>,
    private val onStationClick: (RadioStation) -> Unit
) : RecyclerView.Adapter<DrawerStationAdapter.VH>() {

    private var currentStationId = -1

    fun setCurrentStation(id: Int) {
        if (currentStationId == id) return
        val oldId = currentStationId
        currentStationId = id
        val oldPos = stations.indexOfFirst { it.id == oldId }
        val newPos = stations.indexOfFirst { it.id == id }
        if (oldPos >= 0) notifyItemChanged(oldPos)
        if (newPos >= 0) notifyItemChanged(newPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_drawer_station, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val station = stations[position]
        val isCurrent = station.id == currentStationId

        holder.name.text = station.name

        if (isCurrent) {
            holder.category.text = holder.itemView.context.getString(R.string.drawer_now_playing)
            val accent = resolveThemeColor(holder.itemView, R.attr.themeAccent)
            holder.category.setTextColor(accent)
            holder.name.setTextColor(accent)
            holder.logo.foreground = null
            holder.card.foreground = null
        } else {
            holder.category.text = holder.itemView.context.getString(R.string.radio_station)
            holder.category.setTextColor(resolveThemeColor(holder.itemView, R.attr.themeTextSecondary))
            holder.name.setTextColor(resolveThemeColor(holder.itemView, R.attr.themeTextPrimary))
            holder.logo.foreground = null
            holder.card.foreground = null
        }

        // Load station image resource
        holder.logo.setImageResource(station.imageResId)

        holder.itemView.setOnClickListener {
            onStationClick(station)
        }
    }

    private fun resolveThemeColor(view: View, attr: Int): Int {
        val tv = android.util.TypedValue()
        view.context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density + 0.5f).toInt()

    override fun getItemCount(): Int = stations.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.imgDrawerStation)
        val card: CardView = logo.parent as CardView
        val name: TextView = v.findViewById(R.id.tv_drawer_station_name)
        val category: TextView = v.findViewById(R.id.tv_drawer_station_category)
    }
}
