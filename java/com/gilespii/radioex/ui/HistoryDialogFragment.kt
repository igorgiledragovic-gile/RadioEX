package com.gilespii.radioex.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.ListeningHistoryItem
import com.gilespii.radioex.HistoryRepository
import com.gilespii.radioex.PlayerActivity
import com.gilespii.radioex.R
import com.gilespii.radioex.RadioManager
import com.gilespii.radioex.RadioRepository
import com.gilespii.radioex.getCategoryId

class HistoryDialogFragment : DialogFragment() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var emptyState: View
    private lateinit var btnClearHistory: Button
    private lateinit var btnClose: ImageButton

    private lateinit var adapter: HistoryDialogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_history, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val params = attributes
            params.dimAmount = 0.85f
            attributes = params
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvHistory = view.findViewById(R.id.rv_history)
        emptyState = view.findViewById(R.id.empty_state)
        btnClearHistory = view.findViewById(R.id.btn_clear_history)
        btnClose = view.findViewById(R.id.btn_close)

        btnClose.setOnClickListener {
            dismiss()
        }

        btnClearHistory.setOnClickListener {
            context?.let { ctx ->
                HistoryRepository.clearHistory(ctx)
                loadHistory()
            }
        }

        setupRecyclerView()
        loadHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryDialogAdapter(emptyList()) { historyItem ->
            playFromHistory(historyItem.stationId)
        }
        rvHistory.layoutManager = LinearLayoutManager(context)
        rvHistory.adapter = adapter
    }

    private fun loadHistory() {
        val context = context ?: return
        val allHistory = HistoryRepository.getHistory(context)
        // Poslednjih 10 stanica kako stoji u podnaslovu
        val history = allHistory.take(10)

        adapter.updateList(history)

        if (history.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
            btnClearHistory.visibility = View.GONE
            btnClose.requestFocus()
        } else {
            emptyState.visibility = View.GONE
            rvHistory.visibility = View.VISIBLE
            btnClearHistory.visibility = View.VISIBLE
            rvHistory.post {
                val holder = rvHistory.findViewHolderForAdapterPosition(0)
                holder?.itemView?.requestFocus()
            }
        }
    }

    private fun playFromHistory(stationId: Int) {
        val ctx = context ?: return
        val stations = RadioRepository.getStations()
        val index = stations.indexOfFirst { it.id == stationId }

        if (index >= 0) {
            RadioManager.stationList = stations
            RadioManager.currentIndex = index
            val station = stations[index]
            RadioManager.playStation(ctx, station)

            startActivity(Intent(ctx, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_STATION_INDEX, index)
            })
            activity?.overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
            dismiss()
        }
    }

    companion object {
        const val TAG = "HistoryDialogFragment"
    }

    // ============================================================================
    // Unutrašnji adapter za prikaz stavki u stilu TALAS overlay-a
    // ============================================================================
    private class HistoryDialogAdapter(
        private var items: List<ListeningHistoryItem>,
        private val onItemClick: (ListeningHistoryItem) -> Unit
    ) : RecyclerView.Adapter<HistoryDialogAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvRank: TextView = itemView.findViewById(R.id.tv_rank)
            val ivStationLogo: ImageView = itemView.findViewById(R.id.iv_station_logo)
            val tvStationName: TextView = itemView.findViewById(R.id.tv_station_name)
            val tvStationSubtitle: TextView = itemView.findViewById(R.id.tv_station_subtitle)
            val tvTimeAgo: TextView = itemView.findViewById(R.id.tv_time_ago)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_dialog, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.tvRank.text = (position + 1).toString()
            holder.tvStationName.text = item.stationName
            holder.tvStationSubtitle.text = getSubtitle(item)
            holder.tvTimeAgo.text = formatRelTime(holder.itemView.context, item.timestamp)

            if (item.stationImageResId != 0) {
                holder.ivStationLogo.setImageResource(item.stationImageResId)
            } else {
                holder.ivStationLogo.setImageResource(R.drawable.logo_app)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<ListeningHistoryItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        private fun getSubtitle(item: ListeningHistoryItem): String {
            val station = RadioRepository.getStations().find {
                it.id == item.stationId || it.name.equals(item.stationName, ignoreCase = true)
            }
            return when (station?.getCategoryId()) {
                "naxi" -> if (station.id == 97) "Radio TRI" else "Naxi radio"
                "hitfm" -> "TDI / Hit FM"
                "radios" -> "S Radio"
                "scg" -> "Radio Srbija Grad"
                "play" -> "Play Radio"
                else -> {
                    val nameLower = item.stationName.lowercase()
                    when {
                        nameLower.contains("balkan dance") -> "Radio Balkan Dance"
                        nameLower.contains("tri") -> "Radio TRI"
                        nameLower.contains("karolina") -> "Radio Karolina"
                        else -> "Radio stanica"
                    }
                }
            }
        }

        private fun formatRelTime(context: android.content.Context, timestamp: Long): String {
            val diff = Math.max(0L, System.currentTimeMillis() - timestamp)
            val min = (diff / 60000L).toInt()
            return when {
                min < 1 -> context.getString(R.string.time_just_now)
                min < 60 -> context.getString(R.string.time_minutes_ago, min)
                min < 1440 -> context.getString(R.string.time_hours_ago, min / 60)
                else -> context.getString(R.string.time_days_ago, min / 1440)
            }
        }
    }
}
