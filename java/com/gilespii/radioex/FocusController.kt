package com.gilespii.radioex

import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.adapter.StationAdapter

class FocusController(private val activity: MainActivity) {

    enum class FocusZone {
        RAIL, CATEGORIES, CONTINUE, GRID, SEARCH, MINI_PLAYER, NONE
    }

    private val railItemIds = listOf(
        R.id.nav_stations,
        R.id.nav_history,
        R.id.nav_settings,
        R.id.nav_info,
        R.id.nav_support
    )

    private var previousZone: FocusZone = FocusZone.NONE

    fun handleKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action != KeyEvent.ACTION_DOWN) return false

        val zone = getCurrentFocusZone()
        val dbgName = getDebugFocusName(activity.currentFocus)
        android.util.Log.d("FocusDbg", "key=$keyCode zone=$zone focus=$dbgName")

        // Handle rail expand/collapse on zone transitions or if rail has focus but isn't expanded
        if (zone == FocusZone.RAIL && !activity.isRailExpanded) {
            activity.isUserNavigatingRail = true
            activity.expandRail()
        } else if (zone != FocusZone.RAIL && previousZone == FocusZone.RAIL) {
            activity.isUserNavigatingRail = false
            if (activity.isRailExpanded) activity.collapseRail()
        }
        previousZone = zone

        return when (zone) {
            FocusZone.RAIL -> handleRailKey(keyCode)
            FocusZone.CATEGORIES -> handleCategoriesKey(keyCode)
            FocusZone.CONTINUE -> handleContinueKey(keyCode)
            FocusZone.GRID -> handleGridKey(keyCode)
            FocusZone.SEARCH -> handleSearchKey(keyCode)
            FocusZone.MINI_PLAYER -> handleMiniPlayerKey(keyCode)
            FocusZone.NONE -> false
        }
    }

    // LEVO sa krajnje leve pozicije bilo kojeg reda otvara MenuRail.
    private fun openMenuRail(): Boolean {
        activity.isUserNavigatingRail = true
        activity.expandRail()
        activity.findViewById<View>(R.id.nav_stations).requestFocus()
        previousZone = FocusZone.RAIL
        return true
    }

    private fun handleRailKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val current = getCurrentRailIndex()
                if (current == railItemIds.lastIndex) {
                    if (activity.isMiniPlayerVisible()) {
                        activity.collapseRail()
                        requestMiniPlayerPlayFocus()
                        previousZone = FocusZone.MINI_PLAYER
                        return true
                    }
                    // granica: log dijagnostika za selektor na "Tamna tema"
                    activity.rvStations.post {
                        val f = activity.currentFocus
                        val name = getDebugFocusName(f)
                        android.util.Log.d(
                            "FocusDbg",
                            "railBottom focus=$name isFocused=${f?.isFocused} isShown=${f?.isShown}"
                        )
                    }
                    return true // granica, ostaje na Podrška
                }
                val next = current + 1
                activity.findViewById<View>(railItemIds[next]).requestFocus()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val current = getCurrentRailIndex()
                if (current == 0) return true // granica
                val prev = current - 1
                activity.findViewById<View>(railItemIds[prev]).requestFocus()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                activity.isUserNavigatingRail = false
                activity.collapseRail()
                activity.focusCategoryPill()
                previousZone = FocusZone.CATEGORIES
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!activity.isRailExpanded) {
                    activity.expandRail()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val current = getCurrentRailIndex()
                if (current in railItemIds.indices) {
                    activity.findViewById<View>(railItemIds[current])?.performClick()
                    return true
                }
            }
        }
        return false
    }

    private fun handleCategoriesKey(keyCode: Int): Boolean {
        val focus = activity.currentFocus
        val pos = focus?.let { activity.rvCategories.findContainingViewHolder(it)?.bindingAdapterPosition } ?: -1
        val lastPos = (activity.rvCategories.adapter?.itemCount ?: 0) - 1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                activity.focusSearchBar()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (activity.isContinueSectionVisible()) {
                    activity.focusContinueCard()
                } else {
                    activity.focusFirstGridItem()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (pos <= 0) return openMenuRail()
                return false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (pos == lastPos) {
                    activity.focusCategoryPill()
                    previousZone = FocusZone.CATEGORIES
                    return true
                }
                return false
            }
        }
        return false
    }

    companion object {
        private const val GRID_SPAN_COUNT = 7
    }

    private fun handleContinueKey(keyCode: Int): Boolean {
        val focus = activity.currentFocus
        val pos = focus?.let { activity.rvContinue.findContainingViewHolder(it)?.bindingAdapterPosition } ?: -1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                activity.focusCategoryPill()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Srazmerno mapiranje: svaka continue kartica pokriva 2 kolone u gridu
                if (pos >= 0) {
                    val targetCol = (pos * 2).coerceAtMost(GRID_SPAN_COUNT - 1)
                    activity.focusGridCell(targetCol)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (pos <= 0) return openMenuRail()
                return false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val count = activity.rvContinue.adapter?.itemCount ?: 0
                if (pos >= count - 1) {
                    return true // granica, ostaje na poslednjoj continue kartici
                }
                return false
            }
        }
        return false
    }

    private fun handleGridKey(keyCode: Int): Boolean {
        val pos = getCurrentGridPosition()
        if (pos < 0) return false
        val spanCount = GRID_SPAN_COUNT
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Meni se otvara isključivo sa prve kolone
                val col = pos % spanCount
                if (col == 0) return openMenuRail()
                return false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Poslednji red = granica: mini aktivan → Play/Pause, inače ostaje
                val itemCount = (activity.rvStations.adapter?.itemCount ?: 0)
                if (itemCount > 0 && pos + spanCount >= itemCount) {
                    if (activity.isMiniPlayerVisible()) {
                        requestMiniPlayerPlayFocus()
                    }
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val itemCount = activity.rvStations.adapter?.itemCount ?: 0
                val col = pos % spanCount
                val isLastColumn = col == spanCount - 1
                val isLastItem = pos >= itemCount - 1

                if (isLastColumn || isLastItem) {
                    if (activity.isMiniPlayerVisible()) {
                        requestMiniPlayerFullscreenFocus()
                    }
                    return true // granica: kada je mini aktivan prelazi na fullscreen dugme, inače ostaje na kartici
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (pos < spanCount) {
                    if (activity.isContinueSectionVisible()) {
                        // Srazmerno mapiranje: kolona pos / 2 odgovara kartici iznad
                        val targetCardIndex = pos / 2
                        activity.focusContinueCardAtIndex(targetCardIndex)
                    } else {
                        activity.focusCategoryPill()
                    }
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun handleSearchKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                return true // granica
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                activity.focusCategoryPill()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                return openMenuRail()
            }
        }
        return false
    }

    private fun handleMiniPlayerKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                activity.focusActiveStation()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                return true // granica
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val focus = activity.currentFocus ?: return false
                val idx = getMiniPlayerButtonIndex(focus)
                if (idx <= 0) return true // granica - already at first button
                return false // default left navigation
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val focus = activity.currentFocus ?: return false
                val idx = getMiniPlayerButtonIndex(focus)
                if (idx >= getMiniPlayerButtonCount() - 1) return true // granica
                return false // default right navigation
            }
        }
        return false
    }

    fun getCurrentFocusZone(): FocusZone {
        val focus = activity.currentFocus ?: return FocusZone.NONE
        return when (focus.id) {
            R.id.nav_stations, R.id.nav_history, R.id.nav_settings,
            R.id.nav_info, R.id.nav_support -> FocusZone.RAIL
            R.id.rv_categories -> FocusZone.CATEGORIES
            R.id.et_search -> FocusZone.SEARCH
            R.id.btn_mini_play, R.id.btn_mini_prev,
            R.id.btn_mini_next, R.id.btn_mini_favorite,
            R.id.btn_mini_fullscreen -> FocusZone.MINI_PLAYER
            else -> {
                val sidebar = activity.findViewById<View?>(R.id.sidebar_container)
                val miniView = activity.miniPlayerFragment?.view
                if (sidebar != null && isDescendantOf(focus, sidebar)) FocusZone.RAIL
                else if (isDescendantOf(focus, activity.rvStations)) FocusZone.GRID
                else if (isDescendantOf(focus, activity.rvCategories)) FocusZone.CATEGORIES
                else if (isDescendantOf(focus, activity.rvContinue)) FocusZone.CONTINUE
                else if (miniView != null && isDescendantOf(focus, miniView)) FocusZone.MINI_PLAYER
                else FocusZone.NONE
            }
        }
    }

    private fun isDescendantOf(view: View, parent: View): Boolean {
        var v: View? = view
        while (v != null) {
            if (v === parent) return true
            v = (v.parent as? View)
        }
        return false
    }

    private fun getCurrentRailIndex(): Int {
        val focus = activity.currentFocus ?: return 0
        for (i in railItemIds.indices) {
            val railView = activity.findViewById<View>(railItemIds[i])
            if (isDescendantOf(focus, railView)) return i
        }
        return 0
    }

    private fun getCurrentGridPosition(): Int {
        val focus = activity.currentFocus ?: return -1
        val holder = activity.rvStations.findContainingViewHolder(focus) ?: return -1
        return holder.bindingAdapterPosition
    }

    private fun requestMiniPlayerPlayFocus() {
        activity.miniPlayerFragment?.requestPlayFocus()
    }

    private fun requestMiniPlayerFullscreenFocus() {
        activity.miniPlayerFragment?.requestFullscreenFocus()
        previousZone = FocusZone.MINI_PLAYER
    }

    private fun getMiniPlayerButtonIndex(view: View): Int {
        return when (view.id) {
            R.id.btn_mini_prev -> 0
            R.id.btn_mini_play -> 1
            R.id.btn_mini_next -> 2
            R.id.btn_mini_favorite -> 3
            R.id.btn_mini_fullscreen -> 4
            else -> -1
        }
    }

    private fun getMiniPlayerButtonCount(): Int = 5

    private fun getDebugFocusName(focus: View?): String {
        if (focus == null) return "null"
        val id = focus.id
        if (id > 0) {
            try {
                return activity.resources.getResourceEntryName(id)
            } catch (_: Exception) { }
        }
        val gridPos = getCurrentGridPosition()
        if (gridPos >= 0) {
            val stationName = (activity.rvStations.adapter as? StationAdapter)?.currentList?.getOrNull(gridPos)?.name ?: "?"
            return "grid[$gridPos: $stationName]"
        }
        val catHolder = activity.rvCategories.findContainingViewHolder(focus)
        if (catHolder != null) {
            return "category[${catHolder.bindingAdapterPosition}]"
        }
        val contHolder = activity.rvContinue.findContainingViewHolder(focus)
        if (contHolder != null) {
            return "continue[${contHolder.bindingAdapterPosition}]"
        }
        return focus.javaClass.simpleName
    }
}
