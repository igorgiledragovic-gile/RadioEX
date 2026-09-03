package com.gilespii.radioex

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.adapter.CategoryAdapter
import com.gilespii.radioex.adapter.ContinueAdapter
import com.gilespii.radioex.adapter.StationAdapter
import com.gilespii.radioex.ui.ExitDialogFragment
import com.gilespii.radioex.ui.MiniPlayerFragment
import com.gilespii.radioex.util.Constants
import com.gilespii.radioex.util.ThemeManager
import com.gilespii.radioex.util.FavoritesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.gilespii.radioex.util.AccessibilityManager
import com.gilespii.radioex.ui.SettingsDialogFragment

class MainActivity : AppCompatActivity() {

    internal lateinit var rvStations: VerticalGridView
    internal lateinit var rvCategories: RecyclerView
    internal lateinit var rvContinue: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var continueSection: LinearLayout
    private lateinit var miniPlayerContainer: FrameLayout

    // Clock
    private lateinit var tvClock: TextView

    private lateinit var tvClockDate: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Rail
    private lateinit var sidebarContainer: LinearLayout
    private lateinit var tvNavStations: TextView
    private lateinit var tvNavHistory: TextView
    private lateinit var tvNavSettings: TextView
    private lateinit var tvNavInfo: TextView
    private lateinit var tvNavSupport: TextView
    private lateinit var ivNavStations: View
    private lateinit var ivNavHistory: View
    private lateinit var ivNavSettings: View
    private lateinit var ivNavInfo: View
    private lateinit var ivNavSupport: View
    internal var isRailExpanded = false
    private var railAnimator: ValueAnimator? = null

    private var selectedCategoryId = "all"
    private var query = ""

    private val allStations: ArrayList<RadioStation> by lazy { RadioRepository.getStations() }
    private var shownStations: ArrayList<RadioStation> = arrayListOf()

    private lateinit var stationAdapter: StationAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var continueAdapter: ContinueAdapter
    internal var miniPlayerFragment: MiniPlayerFragment? = null

    private lateinit var focusController: FocusController

    private var isColdStart = true
    internal var isNavigatingToPlayer = false
    internal var isUserNavigatingRail = false

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val SEARCH_DEBOUNCE_MS = 300L
    private var lastStationClickTime = 0L
    private val CLICK_DEBOUNCE_MS = 600L

    private val railLabels by lazy {
        listOf(
            tvNavStations,
            tvNavHistory,
            tvNavSettings,
            tvNavInfo,
            tvNavSupport
        )
    }
    private val railIcons by lazy {
        listOf(ivNavStations, ivNavHistory, ivNavSettings, ivNavInfo, ivNavSupport)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AccessibilityManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AccessibilityManager.initLocale(this)
        ThemeManager.init(this)
        setTheme(ThemeManager.getCurrentThemeResId(this))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        FavoritesManager.init(this)
        setContentView(R.layout.activity_main)

        initViews()
        setupStationsList()
        if (savedInstanceState != null) {
            selectedCategoryId = savedInstanceState.getString("SELECTED_CATEGORY", "all")
            query = savedInstanceState.getString("SEARCH_QUERY", "")
        }
        setupCategories()
        setupContinue()
        setupMiniPlayer()
        setupRail()
        setupClickListeners()
        setupSearch()

        applyFilters()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        focusController = FocusController(this)
        setupColdStartFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("SELECTED_CATEGORY", selectedCategoryId)
        outState.putString("SEARCH_QUERY", query)
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToPlayer = false
        isUserNavigatingRail = false
        collapseRail(force = true)
        android.util.Log.d("RadioFocus", "onResume -> restoring focus, rail locked (FOCUS_BLOCK_DESCENDANTS)")
        startClock()
        refreshFavoritesState()
        applyFilters()
        refreshPlaybackIndicators()
        miniPlayerFragment?.refresh()
        updateMiniPlayerVisibility()
        val reopenTab = pendingReopenSettingsTab
        if (reopenTab != null) {
            pendingReopenSettingsTab = null
            val dialog = com.gilespii.radioex.ui.SettingsDialogFragment.newInstance(
                initialTab = reopenTab
            )
            dialog.show(supportFragmentManager, com.gilespii.radioex.ui.SettingsDialogFragment.TAG)
        } else {
            val reopenThemeId = pendingReopenThemeSettings
            if (reopenThemeId != null) {
                pendingReopenThemeSettings = null
                val dialog = com.gilespii.radioex.ui.SettingsDialogFragment.newInstance(
                    initialTab = com.gilespii.radioex.ui.SettingsDialogFragment.SettingsTab.THEME,
                    initialFocusThemeId = reopenThemeId
                )
                dialog.show(supportFragmentManager, com.gilespii.radioex.ui.SettingsDialogFragment.TAG)
            } else {
                collapseRail(force = true)
                val history = HistoryRepository.getHistory(this)
                val lastHistoryStationId = history.firstOrNull()?.stationId
                val targetStationId = RadioManager.currentStation?.id ?: lastHistoryStationId

                rvContinue.post {
                    android.util.Log.d("RadioFocus", "onResume post -> focusing target station $targetStationId")
                    if (isContinueSectionVisible()) {
                        if (targetStationId != null) {
                            focusContinueStationById(targetStationId)
                        } else {
                            focusContinueCard(preferActive = true)
                        }
                    } else {
                        if (targetStationId != null) {
                            val pos = shownStations.indexOfFirst { it.id == targetStationId }
                            if (pos >= 0) {
                                rvStations.setSelectedPosition(pos)
                                rvStations.requestFocus()
                            } else {
                                focusActiveStation()
                            }
                        } else {
                            focusActiveStation()
                        }
                    }
                    collapseRail(force = true)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopClock()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClock()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    private fun initViews() {
        rvStations = findViewById(R.id.rv_stations)
        rvCategories = findViewById(R.id.rv_categories)
        rvContinue = findViewById(R.id.rv_continue)
        etSearch = findViewById(R.id.et_search)
        etSearch.isFocusable = false
        etSearch.isFocusableInTouchMode = false
        continueSection = findViewById(R.id.continue_section)
        miniPlayerContainer = findViewById(R.id.mini_player_fragment)

        tvClock = findViewById(R.id.tv_clock)
        tvClockDate = findViewById(R.id.tv_clock_date)

        sidebarContainer = findViewById(R.id.sidebar_container)
        sidebarContainer.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        tvNavStations = findViewById(R.id.tv_nav_stations)
        tvNavHistory = findViewById(R.id.tv_nav_history)
        tvNavSettings = findViewById(R.id.tv_nav_settings)
        tvNavInfo = findViewById(R.id.tv_nav_info)
        tvNavSupport = findViewById(R.id.tv_nav_support)
        ivNavStations = findViewById(R.id.iv_nav_stations)
        ivNavHistory = findViewById(R.id.iv_nav_history)
        ivNavSettings = findViewById(R.id.iv_nav_settings)
        ivNavInfo = findViewById(R.id.iv_nav_info)
        ivNavSupport = findViewById(R.id.iv_nav_support)

        etSearch.setOnFocusChangeListener { v, hasFocus ->
            if (isColdStart && hasFocus) {
                isColdStart = false
                if (isContinueSectionVisible()) {
                    focusContinueCard()
                } else {
                    focusActiveStation()
                }
                return@setOnFocusChangeListener
            }
            updateSearchFocusVisuals(hasFocus)
            v.animate()
                .scaleX(if (hasFocus) 1.04f else 1f)
                .scaleY(if (hasFocus) 1.04f else 1f)
                .setDuration(150)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            v.translationZ = if (hasFocus) 12f else 0f
        }
        updateSearchFocusVisuals(false)
    }

    private fun updateSearchFocusVisuals(hasFocus: Boolean) {
        val shouldShowDetails = hasFocus || etSearch.text.isNotEmpty()
        etSearch.hint = if (shouldShowDetails) getString(R.string.search_hint) else ""
        val drawables = etSearch.compoundDrawablesRelative
        drawables[2]?.mutate()?.alpha = if (shouldShowDetails) 255 else 0
    }

    private fun setupStationsList() {
        shownStations = ArrayList(allStations)
        stationAdapter = StationAdapter { position ->
            val now = System.currentTimeMillis()
            if (now - lastStationClickTime < CLICK_DEBOUNCE_MS || isNavigatingToPlayer) {
                return@StationAdapter
            }
            lastStationClickTime = now
            isNavigatingToPlayer = true
            isUserNavigatingRail = false
            collapseRail(force = true)
            RadioManager.stationList = stationAdapter.getStationsList()
            RadioManager.currentIndex = position
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_STATION_INDEX, position)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
        }
        rvStations.setNumColumns(7)
        rvStations.setHasFixedSize(true)
        rvStations.setItemViewCacheSize(25)
        rvStations.recycledViewPool.setMaxRecycledViews(0, 35)
        rvStations.adapter = stationAdapter
        rvStations.itemAnimator = null

        // Fiksna pozicija fokusa (window alignment u ravni prvog reda odmah ispod kategorija)
        rvStations.windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
        rvStations.windowAlignmentOffset = 0
        rvStations.windowAlignmentOffsetPercent = 0f
        rvStations.itemAlignmentOffset = 0
        rvStations.itemAlignmentOffsetPercent = 0f
        rvStations.isItemAlignmentOffsetWithPadding = true

        stationAdapter.submitList(shownStations)
    }

    private fun setupCategories() {
        categoryAdapter = CategoryAdapter(Constants.getCategories(this)) { categoryId ->
            selectedCategoryId = categoryId
            applyFilters()
        }
        categoryAdapter.setSelectedId(selectedCategoryId)
        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvCategories.adapter = categoryAdapter
    }

    private fun setupContinue() {
        continueAdapter = ContinueAdapter { station ->
            val now = System.currentTimeMillis()
            if (now - lastStationClickTime < CLICK_DEBOUNCE_MS || isNavigatingToPlayer) {
                return@ContinueAdapter
            }
            lastStationClickTime = now
            isNavigatingToPlayer = true
            isUserNavigatingRail = false
            collapseRail(force = true)
            android.util.Log.d("RadioFocus", "Continue card clicked (${station.name}) -> isNavigatingToPlayer = true, rail locked")
            RadioManager.stationList = ArrayList(allStations)
            val idx = allStations.indexOfFirst { it.id == station.id }
            RadioManager.currentIndex = idx.coerceAtLeast(0)
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_STATION_INDEX, idx.coerceAtLeast(0))
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
        }
        rvContinue.setHasFixedSize(true)
        rvContinue.setItemViewCacheSize(10)
        rvContinue.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvContinue.adapter = continueAdapter
        rvContinue.post { updateContinueCardWidth() }
    }

    private fun updateContinueCardWidth() {
        if (!::continueAdapter.isInitialized) return
        val density = resources.displayMetrics.density
        val totalWidth = rvStations.measuredWidth.takeIf { it > 0 }
            ?: rvContinue.measuredWidth.takeIf { it > 0 }
            ?: run {
                val screenW = resources.displayMetrics.widthPixels
                val reserved = ((64 + 32 + 32) * density).toInt()
                screenW - reserved
            }
        val colWidth = totalWidth / 7f
        val marginEndPx = (12f * density).toInt()
        val cardWidth = (2 * colWidth - marginEndPx).toInt()
        continueAdapter.cardWidthPx = cardWidth
    }

    private fun setupMiniPlayer() {
        val existing = supportFragmentManager.findFragmentById(R.id.mini_player_fragment)
        if (existing is MiniPlayerFragment) {
            miniPlayerFragment = existing
        } else {
            val fragment = MiniPlayerFragment()
            miniPlayerFragment = fragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.mini_player_fragment, fragment)
                .commit()
        }
    }

    internal fun isViewInRail(view: View): Boolean {
        var current: android.view.ViewParent? = view.parent
        while (current != null) {
            if (current === sidebarContainer) return true
            current = current.parent
        }
        return false
    }

    private fun setupRail() {
        // Jedinstven focus efekat: scale 1.06 + elevation na rail ikonicama
        val density = resources.displayMetrics.density
        listOf(R.id.nav_stations, R.id.nav_history, R.id.nav_settings,
            R.id.nav_info, R.id.nav_support).forEach { id ->
            findViewById<View>(id).setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.06f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                v.translationZ = if (hasFocus) 8f * density else 0f
                if (!isColdStart && !isNavigatingToPlayer && hasFocus && !isRailExpanded && isUserNavigatingRail) {
                    expandRail()
                }
            }
        }

        // Globalni focus listener: automatski otvara rail samo kada je korisnik namerno usao u rail, i zatvara kada fokus izadje
        sidebarContainer.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (isColdStart || isNavigatingToPlayer) return@addOnGlobalFocusChangeListener
            val hasRailFocus = newFocus != null && isViewInRail(newFocus)
            if (hasRailFocus && !isRailExpanded) {
                if (isUserNavigatingRail) {
                    expandRail()
                } else {
                    // Slucajan / sistemski fallback fokus na stavku menija dok je meni zatvoren:
                    // odmah preusmeriti fokus nazad na sadrzaj i drzati meni zatvorenim!
                    if (isContinueSectionVisible()) {
                        focusContinueCard(preferActive = true)
                    } else {
                        focusActiveStation()
                    }
                    collapseRail(force = true)
                }
            } else if (!hasRailFocus && isRailExpanded) {
                isUserNavigatingRail = false
                collapseRail()
            }
        }

        // Rail nav clicks
        findViewById<View>(R.id.nav_stations).setOnClickListener {
            if (!isRailExpanded) {
                expandRail()
            } else {
                focusFirstGridItem()
            }
        }
        findViewById<View>(R.id.nav_history).setOnClickListener {
            val dialog = com.gilespii.radioex.ui.HistoryDialogFragment()
            dialog.show(supportFragmentManager, com.gilespii.radioex.ui.HistoryDialogFragment.TAG)
        }
        findViewById<View>(R.id.nav_settings).setOnClickListener {
            val dialog = com.gilespii.radioex.ui.SettingsDialogFragment()
            dialog.show(supportFragmentManager, com.gilespii.radioex.ui.SettingsDialogFragment.TAG)
        }
        findViewById<View>(R.id.nav_info).setOnClickListener {
            val dialog = com.gilespii.radioex.ui.AboutDialogFragment()
            dialog.show(supportFragmentManager, com.gilespii.radioex.ui.AboutDialogFragment.TAG)
        }
        findViewById<View>(R.id.nav_support).setOnClickListener {
            val dialog = com.gilespii.radioex.ui.SupportDialogFragment()
            dialog.show(supportFragmentManager, com.gilespii.radioex.ui.SupportDialogFragment.TAG)
        }
    }

    internal fun expandRail() {
        sidebarContainer.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        android.util.Log.d("RadioFocus", "expandRail -> unlocking rail (FOCUS_AFTER_DESCENDANTS)")
        val endWidth = dpToPx(190)
        if (isRailExpanded && sidebarContainer.width >= endWidth) return
        isRailExpanded = true
        railAnimator?.cancel()
        val startWidth = sidebarContainer.width.takeIf { it > 0 } ?: dpToPx(64)
        railAnimator = ValueAnimator.ofInt(startWidth, endWidth).apply {
            duration = 240
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val value = anim.animatedValue as Int
                val lp = sidebarContainer.layoutParams
                lp.width = value
                sidebarContainer.layoutParams = lp
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isRailExpanded) {
                        railLabels.forEach {
                            it.visibility = View.VISIBLE
                            it.alpha = 1f
                        }
                    }
                }
            })
            start()
        }
        // Fade in labels
        railLabels.forEach { label ->
            label.animate().cancel()
            label.visibility = View.VISIBLE
            label.alpha = 0f
            label.animate().alpha(1f).setDuration(180).setStartDelay(50).start()
        }
    }

    internal fun collapseRail(force: Boolean = false) {
        sidebarContainer.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        android.util.Log.d("RadioFocus", "collapseRail(force=$force) -> locking rail (FOCUS_BLOCK_DESCENDANTS)")
        val endWidth = dpToPx(64)
        if (!force && !isRailExpanded && sidebarContainer.width <= endWidth) return
        isRailExpanded = false
        railAnimator?.cancel()
        if (force) {
            railLabels.forEach { label ->
                label.animate().cancel()
                label.visibility = View.GONE
                label.alpha = 0f
            }
            val lp = sidebarContainer.layoutParams
            if (lp.width != endWidth) {
                lp.width = endWidth
                sidebarContainer.layoutParams = lp
            }
            return
        }
        val startWidth = sidebarContainer.width.takeIf { it > 0 } ?: dpToPx(190)
        railAnimator = ValueAnimator.ofInt(startWidth, endWidth).apply {
            duration = 240
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val value = anim.animatedValue as Int
                val lp = sidebarContainer.layoutParams
                lp.width = value
                sidebarContainer.layoutParams = lp
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!isRailExpanded) {
                        railLabels.forEach { label ->
                            label.visibility = View.GONE
                            label.alpha = 0f
                        }
                    }
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (!isRailExpanded) {
                        railLabels.forEach { label ->
                            label.visibility = View.GONE
                            label.alpha = 0f
                        }
                    }
                }
            })
            start()
        }
        // Fade out labels
        railLabels.forEach { label ->
            label.animate().cancel()
            label.animate().alpha(0f).setDuration(100).withEndAction {
                if (!isRailExpanded) {
                    label.visibility = View.GONE
                }
            }.start()
        }
    }

    private fun setupClickListeners() {
    }

    private fun setupSearch() {
        updateSearchFocusVisuals(etSearch.hasFocus())
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSearchFocusVisuals(etSearch.hasFocus())
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { applyFilters() }.also {
                    searchHandler.postDelayed(it, SEARCH_DEBOUNCE_MS)
                }
            }
        })
    }

    private fun setupColdStartFocus() {
        collapseRail(force = true)
        val history = HistoryRepository.getHistory(this)
        val lastHistoryStationId = history.firstOrNull()?.stationId
        val targetStationId = lastHistoryStationId ?: RadioManager.currentStation?.id

        if (isContinueSectionVisible()) {
            val targetIdx = if (targetStationId != null) {
                continueAdapter.getIndexForStation(targetStationId).coerceAtLeast(0)
            } else 0
            rvContinue.post {
                android.util.Log.d("RadioFocus", "setupColdStartFocus -> focusing continue station $targetStationId (idx=$targetIdx)")
                focusContinueCardAtIndex(targetIdx)
                collapseRail(force = true)
                rvContinue.postDelayed({
                    isColdStart = false
                    etSearch.isFocusable = true
                    etSearch.isFocusableInTouchMode = true
                }, 300)
            }
        } else {
            val targetGridIdx = if (targetStationId != null) {
                shownStations.indexOfFirst { it.id == targetStationId }.coerceAtLeast(0)
            } else 0
            rvStations.post {
                android.util.Log.d("RadioFocus", "setupColdStartFocus -> focusing grid station $targetStationId (idx=$targetGridIdx)")
                if (shownStations.isNotEmpty()) {
                    rvStations.setSelectedPosition(targetGridIdx)
                    rvStations.requestFocus()
                }
                collapseRail(force = true)
                rvStations.postDelayed({
                    isColdStart = false
                    etSearch.isFocusable = true
                    etSearch.isFocusableInTouchMode = true
                }, 300)
            }
        }
    }

    internal fun isMiniPlayerVisible(): Boolean {
        return miniPlayerContainer.visibility == View.VISIBLE &&
            RadioManager.currentStation != null &&
            (miniPlayerFragment?.view?.visibility == View.VISIBLE)
    }

    internal fun focusActiveStation() {
        val activeId = RadioManager.currentStation?.id
        val position = if (activeId != null) shownStations.indexOfFirst { it.id == activeId } else 0
        val targetPos = if (position >= 0) position else 0
        if (shownStations.isEmpty()) return

        rvStations.setSelectedPosition(targetPos)
        rvStations.requestFocus()
    }

    internal fun focusCategoryPill() {
        rvCategories.post {
            val index = categoryAdapter.getSelectedIndex().coerceAtLeast(0)
            (rvCategories.layoutManager as? LinearLayoutManager)?.scrollToPosition(index)
            rvCategories.post {
                val target = rvCategories.findViewHolderForAdapterPosition(index)?.itemView
                    ?: rvCategories.findViewHolderForAdapterPosition(0)?.itemView
                target?.requestFocus() ?: rvCategories.requestFocus()
            }
        }
    }

    internal fun isContinueSectionVisible(): Boolean {
        return continueSection.visibility == View.VISIBLE &&
            (::continueAdapter.isInitialized && continueAdapter.itemCount > 0)
    }

    internal fun focusSearchBar() {
        isColdStart = false
        etSearch.isFocusable = true
        etSearch.isFocusableInTouchMode = true
        etSearch.requestFocus()
    }

    internal fun focusContinueCardAtIndex(targetIndex: Int) {
        if (!isContinueSectionVisible()) {
            focusCategoryPill()
            return
        }
        val count = continueAdapter.itemCount
        if (count == 0) {
            focusCategoryPill()
            return
        }
        val target = targetIndex.coerceIn(0, count - 1)
        rvContinue.scrollToPosition(target)
        rvContinue.post {
            val holder = rvContinue.findViewHolderForAdapterPosition(target)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else {
                rvContinue.post {
                    rvContinue.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
                        ?: rvContinue.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                        ?: rvContinue.requestFocus()
                }
            }
        }
    }

    internal fun focusContinueCard(preferActive: Boolean = true) {
        if (!isContinueSectionVisible()) {
            focusCategoryPill()
            return
        }
        val activeId = RadioManager.currentStation?.id
        val index = if (preferActive && activeId != null) continueAdapter.getIndexForStation(activeId) else 0
        focusContinueCardAtIndex(if (index >= 0) index else 0)
    }

    internal fun focusContinueStationById(stationId: Int?) {
        if (!isContinueSectionVisible()) {
            focusActiveStation()
            return
        }
        val index = if (stationId != null && ::continueAdapter.isInitialized) {
            continueAdapter.getIndexForStation(stationId).coerceAtLeast(0)
        } else 0
        focusContinueCardAtIndex(index)
    }

    internal fun focusFirstGridItem() {
        if (shownStations.isEmpty()) {
            rvStations.requestFocus()
            return
        }
        rvStations.setSelectedPosition(0)
        rvStations.requestFocus()
    }

    internal fun focusGridCell(column: Int) {
        if (shownStations.isEmpty()) return
        val target = column.coerceIn(0, shownStations.size - 1)
        rvStations.setSelectedPosition(target)
        rvStations.requestFocus()
    }

    private fun refreshFavoritesState() {
        allStations.forEach { it.isFavorite = FavoritesManager.isFavorite(it.id) }
        stationAdapter.notifyDataSetChanged()
    }

    private fun applyFilters() {
        var list = when (selectedCategoryId) {
            "all" -> allStations
            "favorites" -> allStations.filter { FavoritesManager.isFavorite(it.id) }
            else -> allStations.filter { it.getCategoryId() == selectedCategoryId }
        }
        if (query.isNotEmpty()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }
        shownStations = ArrayList(list)
        stationAdapter.submitList(shownStations)

        if (::categoryAdapter.isInitialized) {
            categoryAdapter.setSelectedId(selectedCategoryId)
        }

        updateContinueSection()
    }

    private fun updateContinueSection() {
        try {
            val history = HistoryRepository.getHistory(this)
            val historyStations = history.mapNotNull { item ->
                allStations.firstOrNull { it.id == item.stationId }
            }
            val current = RadioManager.currentStation
            val combined = if (current != null) {
                listOf(current) + historyStations.filter { it.id != current.id }
            } else {
                historyStations
            }
            val continueStations = if (combined.isNotEmpty()) {
                combined.distinctBy { it.id }.take(6)
            } else {
                RadioRepository.getPopularDefaultStations().take(6)
            }

            if (continueStations.isNotEmpty() && selectedCategoryId == "all" && query.isEmpty()) {
                updateContinueCardWidth()
                continueSection.visibility = View.VISIBLE
                continueAdapter.submitList(continueStations)
            } else {
                continueSection.visibility = View.GONE
            }
        } catch (_: Exception) {
            continueSection.visibility = View.GONE
        }
    }

    // ============================================================================
    // CLOCK
    // ============================================================================

    private fun updateClock() {
        val now = Date()
        tvClock.text = timeFormat.format(now)

        val cal = Calendar.getInstance()
        val days = arrayOf("ned", "pon", "uto", "sre", "čet", "pet", "sub")
        val months = arrayOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "avg", "sep", "okt", "nov", "dec")
        tvClockDate.text = "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]} · ${cal.get(Calendar.DAY_OF_MONTH)}. ${months[cal.get(Calendar.MONTH)]}"
    }

    private fun startClock() {
        clockHandler.post(clockRunnable)
    }

    private fun stopClock() {
        clockHandler.removeCallbacks(clockRunnable)
    }

    // ============================================================================
    // D-PAD NAVIGATION
    // ============================================================================

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (focusController.handleKeyEvent(keyCode, event)) return true

        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (isRailExpanded) {
                    isUserNavigatingRail = false
                    collapseRail()
                } else {
                    isUserNavigatingRail = true
                    expandRail()
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                handleBackPress()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private var lastBackPressTime = 0L
    private val DOUBLE_BACK_THRESHOLD_MS = 2000L
    private val BACK_DEBOUNCE_MS = 300L

    private fun handleBackPress() {
        if (isRailExpanded) {
            isUserNavigatingRail = false
            collapseRail()
            if (isContinueSectionVisible()) {
                focusContinueCard(preferActive = true)
            } else {
                focusActiveStation()
            }
            return
        }

        val currentTime = System.currentTimeMillis()
        val delta = currentTime - lastBackPressTime

        // Ignoriši hardverski bounce / dupli sistemski poziv (< 300ms)
        if (delta < BACK_DEBOUNCE_MS) {
            return
        }

        if (delta <= DOUBLE_BACK_THRESHOLD_MS) {
            lastBackPressTime = 0L
            ExitDialogFragment.show(supportFragmentManager)
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(this, getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================================
    // PLAYBACK LISTENER & MINI PLAYER
    // ============================================================================

    private val playbackListener = object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshPlaybackIndicators()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshPlaybackIndicators()
        }
    }

    private val stationChangeListener: (RadioStation, Int) -> Unit = { _, _ ->
        refreshPlaybackIndicators()
    }

    private var lastPlayingStationId: Int? = null

    internal fun refreshPlaybackIndicators() {
        runOnUiThread {
            val currentStationId = if (RadioManager.isPlaying()) RadioManager.currentStation?.id else null
            if (::stationAdapter.isInitialized) {
                val list = stationAdapter.currentList
                val oldPos = if (lastPlayingStationId != null) list.indexOfFirst { it.id == lastPlayingStationId } else -1
                val newPos = if (currentStationId != null) list.indexOfFirst { it.id == currentStationId } else -1
                if (oldPos != -1) stationAdapter.notifyItemChanged(oldPos)
                if (newPos != -1 && newPos != oldPos) stationAdapter.notifyItemChanged(newPos)
                if (oldPos == -1 && newPos == -1) {
                    stationAdapter.notifyDataSetChanged()
                }
            }
            lastPlayingStationId = currentStationId
            if (!isNavigatingToPlayer && ::continueAdapter.isInitialized) {
                continueAdapter.notifyDataSetChanged()
            }
            if (!isNavigatingToPlayer) {
                updateContinueSection()
            }
            updateMiniPlayerVisibility()
        }
    }

    override fun onStart() {
        super.onStart()
        try { RadioManager.getPlayer(this).addListener(playbackListener) } catch (_: Exception) {}
        RadioManager.addStationChangeListener(stationChangeListener)
        refreshPlaybackIndicators()
    }

    override fun onStop() {
        super.onStop()
        try { RadioManager.getPlayer(this).removeListener(playbackListener) } catch (_: Exception) {}
        RadioManager.removeStationChangeListener(stationChangeListener)
    }

    private fun updateMiniPlayerVisibility() {
        if (isNavigatingToPlayer) return
        val hasStation = RadioManager.currentStation != null
        val hadFocus = miniPlayerContainer.hasFocus()
        if (!hasStation && hadFocus) {
            android.util.Log.d("RadioFocus", "MiniPlayer had focus and is hiding -> redirecting focus to continue[0]")
            if (isContinueSectionVisible()) {
                focusContinueCard(preferActive = false)
                rvContinue.post { focusContinueCard(preferActive = false) }
            } else {
                focusActiveStation()
                rvStations.post { focusActiveStation() }
            }
        }
        miniPlayerContainer.visibility = if (hasStation) View.VISIBLE else View.GONE
        if (hasStation) miniPlayerFragment?.show() else miniPlayerFragment?.hide()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    companion object {
        var pendingReopenThemeSettings: String? = null
        var pendingReopenSettingsTab: SettingsDialogFragment.SettingsTab? = null
    }
}
