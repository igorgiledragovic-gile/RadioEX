package com.gilespii.radioex.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.R
import com.gilespii.radioex.RadioManager
import com.gilespii.radioex.RadioStation

import com.gilespii.radioex.util.StationLogoCache

class StationAdapter(
    private val onStationClick: (position: Int) -> Unit
) : ListAdapter<RadioStation, StationAdapter.VH>(StationDiffCallback()) {

    companion object {
        private val FOCUS_INTERPOLATOR = android.view.animation.DecelerateInterpolator(1.5f)
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLong()
    }

    fun getStationsList(): ArrayList<RadioStation> = ArrayList(currentList)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val st = getItem(position)
        val ctx = holder.itemView.context

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

        val isCurrent = RadioManager.currentStation?.id == st.id
        val isPlayingThis = isCurrent && RadioManager.isPlaying()

        if (isCurrent) {
            holder.liveChip.visibility = View.VISIBLE
            holder.tvLive.text = if (isPlayingThis) "LIVE" else "PAUZA"
        } else {
            holder.liveChip.visibility = View.GONE
        }

        if (isPlayingThis) {
            holder.playingOverlay.visibility = View.VISIBLE
            startVisualizerAnimation(holder)
        } else {
            holder.playingOverlay.visibility = View.GONE
            stopVisualizerAnimation(holder)
        }

        holder.favIcon.visibility = if (st.isFavorite) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onStationClick(position) }

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1f
            val density = v.resources.displayMetrics.density
            v.animate().scaleX(scale).scaleY(scale).setDuration(150)
                .setInterpolator(FOCUS_INTERPOLATOR)
                .start()
            v.translationZ = if (hasFocus) 8f * density else 0f
        }
    }

    private fun startVisualizerAnimation(holder: VH) {
        val bars = listOf(holder.bar1, holder.bar2, holder.bar3, holder.bar4)
        bars.forEachIndexed { index, bar ->
            bar.clearAnimation()
            val duration = 400L + (index * 100)
            val anim = android.view.animation.ScaleAnimation(
                1f, 1f, 0.4f, 1.0f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 1f
            )
            anim.duration = duration
            anim.repeatMode = android.view.animation.Animation.REVERSE
            anim.repeatCount = android.view.animation.Animation.INFINITE
            bar.startAnimation(anim)
        }
    }

    private fun stopVisualizerAnimation(holder: VH) {
        listOf(holder.bar1, holder.bar2, holder.bar3, holder.bar4).forEach { it.clearAnimation() }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        stopVisualizerAnimation(holder)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.iv_station)
        val name: TextView = v.findViewById(R.id.tv_name)
        val playingOverlay: View = v.findViewById(R.id.overlay_playing)
        val favIcon: ImageView = v.findViewById(R.id.iv_favorite)
        val liveChip: LinearLayout = v.findViewById(R.id.live_chip)
        val tvLive: TextView = v.findViewById(R.id.tv_live)

        val bar1: View = v.findViewById(R.id.bar1)
        val bar2: View = v.findViewById(R.id.bar2)
        val bar3: View = v.findViewById(R.id.bar3)
        val bar4: View = v.findViewById(R.id.bar4)
    }

    class StationDiffCallback : DiffUtil.ItemCallback<RadioStation>() {
        override fun areItemsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean {
            return oldItem.name == newItem.name &&
                   oldItem.isFavorite == newItem.isFavorite &&
                   oldItem.imageResId == newItem.imageResId &&
                   oldItem.streamUrl == newItem.streamUrl
        }
    }
}
