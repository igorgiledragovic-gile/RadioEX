package com.gilespii.radioex.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.BuildConfig
import com.gilespii.radioex.MainActivity
import com.gilespii.radioex.R
import com.gilespii.radioex.RadioManager
import com.gilespii.radioex.adapter.ThemeAdapter
import com.gilespii.radioex.audio.EqualizerManager
import com.gilespii.radioex.util.AccessibilityManager
import com.gilespii.radioex.util.Constants
import com.gilespii.radioex.util.FavoritesManager
import com.gilespii.radioex.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class SettingsDialogFragment : DialogFragment() {

    enum class SettingsTab {
        THEME,
        EQUALIZER,
        BACKUP,
        ACCESSIBILITY
    }

    private var currentTab = SettingsTab.THEME
    private var themeChanged = false

    // Tabs
    private lateinit var tabTheme: LinearLayout
    private lateinit var tabEqualizer: LinearLayout
    private lateinit var tabBackup: LinearLayout
    private lateinit var tabAccessibility: LinearLayout
    private lateinit var ivTabTheme: ImageView
    private lateinit var ivTabEqualizer: ImageView
    private lateinit var ivTabBackup: ImageView
    private lateinit var ivTabAccessibility: ImageView
    private lateinit var tvTabThemeTitle: TextView
    private lateinit var tvTabEqualizerTitle: TextView
    private lateinit var tvTabBackupTitle: TextView
    private lateinit var tvTabAccessibilityTitle: TextView

    // Panels
    private lateinit var panelTheme: LinearLayout
    private lateinit var panelEqualizer: LinearLayout
    private lateinit var panelBackup: LinearLayout
    private lateinit var panelAccessibility: LinearLayout

    // Accessibility Panel Views
    private lateinit var switchReducedMotion: SwitchCompat
    private lateinit var btnLangSr: TextView
    private lateinit var btnLangEn: TextView

    // Theme Panel Views
    private lateinit var rvThemes: RecyclerView
    private lateinit var tvCurrentThemeBadge: TextView
    private var themeAdapter: ThemeAdapter? = null

    // Theme Confirmation Views
    private lateinit var themeConfirmOverlay: FrameLayout
    private lateinit var tvConfirmTitle: TextView
    private lateinit var tvConfirmSubtitle: TextView
    private lateinit var tvConfirmMessage: TextView
    private lateinit var viewConfirmColorPrimary: View
    private lateinit var viewConfirmColorSecondary: View
    private lateinit var tvConfirmThemeName: TextView
    private lateinit var tvConfirmModeBadge: TextView
    private lateinit var btnConfirmYes: Button
    private lateinit var btnConfirmNo: Button
    private var pendingTheme: Constants.ThemeConfig? = null
    private var lastFocusedThemePosition: Int = -1

    // Equalizer Panel Views
    private lateinit var switchEq: SwitchCompat
    private lateinit var scrollPresetButtons: HorizontalScrollView
    private lateinit var presetButtonsContainer: LinearLayout
    private lateinit var eqBandsContainer: LinearLayout
    private val bandSeekBars = mutableListOf<SeekBar>()
    private val bandContainers = mutableListOf<LinearLayout>()
    private val presetButtons = mutableListOf<Button>()
    private var currentFocusedBandIndex = -1

    // Backup Panel Views
    private lateinit var tvBackupFavCount: TextView
    private lateinit var btnExportCard: LinearLayout
    private lateinit var btnImportCard: LinearLayout
    private lateinit var rvBackupFiles: RecyclerView
    private lateinit var tvBackupStatus: TextView
    private lateinit var tvBackupFilesTitle: TextView
    private var backupFilesAdapter: BackupFilesAdapter? = null

    // Colors
    private var accentColor: Int = Color.CYAN
    private var ringColor: Int = Color.WHITE
    private var onAccentColor: Int = Color.BLACK
    private var dimColor: Int = Color.GRAY

    // Storage permission launcher for API < 29
    private var pendingExport = false
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (pendingExport) {
                pendingExport = false
                performExport()
            }
        } else {
            Toast.makeText(context, getString(R.string.settings_backup_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_settings, container, false)
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

        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                if (::themeConfirmOverlay.isInitialized && themeConfirmOverlay.visibility == View.VISIBLE) {
                    cancelThemeConfirmation()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        resolveThemeColors()
        initHeaderAndTabs(view)
        initThemePanel(view)
        initEqualizerPanel(view)
        initBackupPanel(view)
        initAccessibilityPanel(view)
        initThemeConfirmation(view)

        // Select initial tab from arguments if provided
        val tabName = arguments?.getString(ARG_INITIAL_TAB)
        val initialTab = if (tabName != null) {
            try { SettingsTab.valueOf(tabName) } catch (_: Exception) { SettingsTab.THEME }
        } else {
            SettingsTab.THEME
        }
        selectTab(initialTab)

        // Request initial focus if not focusing a theme card directly
        val focusThemeId = arguments?.getString(ARG_FOCUS_THEME_ID)
        if (focusThemeId == null) {
            when (initialTab) {
                SettingsTab.ACCESSIBILITY -> tabAccessibility.post { tabAccessibility.requestFocus() }
                SettingsTab.EQUALIZER -> tabEqualizer.post { tabEqualizer.requestFocus() }
                SettingsTab.BACKUP -> tabBackup.post { tabBackup.requestFocus() }
                else -> tabTheme.post { tabTheme.requestFocus() }
            }
        }
    }

    private fun resolveThemeColors() {
        val typedValue = TypedValue()
        val th = requireContext().theme
        if (th.resolveAttribute(R.attr.themeAccent, typedValue, true)) accentColor = typedValue.data
        if (th.resolveAttribute(R.attr.themeFocusBorder, typedValue, true)) ringColor = typedValue.data
        if (th.resolveAttribute(R.attr.themeOnAccent, typedValue, true)) onAccentColor = typedValue.data
        if (th.resolveAttribute(R.attr.themeTextSecondary, typedValue, true)) dimColor = typedValue.data
    }

    private fun initHeaderAndTabs(view: View) {
        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            dismiss()
        }

        tabTheme = view.findViewById(R.id.tab_theme)
        tabEqualizer = view.findViewById(R.id.tab_equalizer)
        tabBackup = view.findViewById(R.id.tab_backup)
        tabAccessibility = view.findViewById(R.id.tab_accessibility)

        ivTabTheme = view.findViewById(R.id.iv_tab_theme)
        ivTabEqualizer = view.findViewById(R.id.iv_tab_equalizer)
        ivTabBackup = view.findViewById(R.id.iv_tab_backup)
        ivTabAccessibility = view.findViewById(R.id.iv_tab_accessibility)

        tvTabThemeTitle = view.findViewById(R.id.tv_tab_theme_title)
        tvTabEqualizerTitle = view.findViewById(R.id.tv_tab_equalizer_title)
        tvTabBackupTitle = view.findViewById(R.id.tv_tab_backup_title)
        tvTabAccessibilityTitle = view.findViewById(R.id.tv_tab_accessibility_title)

        panelTheme = view.findViewById(R.id.panel_theme)
        panelEqualizer = view.findViewById(R.id.panel_equalizer)
        panelBackup = view.findViewById(R.id.panel_backup)
        panelAccessibility = view.findViewById(R.id.panel_accessibility)

        tabTheme.setOnClickListener { selectTab(SettingsTab.THEME) }
        tabEqualizer.setOnClickListener { selectTab(SettingsTab.EQUALIZER) }
        tabBackup.setOnClickListener { selectTab(SettingsTab.BACKUP) }
        tabAccessibility.setOnClickListener { selectTab(SettingsTab.ACCESSIBILITY) }

        // Automatically switch panel when tab gains focus on TV
        tabTheme.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectTab(SettingsTab.THEME)
        }
        tabEqualizer.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectTab(SettingsTab.EQUALIZER)
        }
        tabBackup.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectTab(SettingsTab.BACKUP)
        }
        tabAccessibility.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectTab(SettingsTab.ACCESSIBILITY)
        }

        // D-pad routing for tabs (RIGHT moves into panel)
        tabTheme.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                rvThemes.post {
                    val pos = themeAdapter?.getSelectedPosition() ?: 0
                    val target = rvThemes.layoutManager?.findViewByPosition(pos)
                        ?: rvThemes.layoutManager?.findViewByPosition(0)
                        ?: rvThemes.getChildAt(0)
                    target?.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        tabEqualizer.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                switchEq.requestFocus()
                return@setOnKeyListener true
            }
            false
        }

        tabBackup.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                btnExportCard.requestFocus()
                return@setOnKeyListener true
            }
            false
        }

        tabAccessibility.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                switchReducedMotion.requestFocus()
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun selectTab(tab: SettingsTab) {
        currentTab = tab

        tabTheme.isSelected = (tab == SettingsTab.THEME)
        tabEqualizer.isSelected = (tab == SettingsTab.EQUALIZER)
        tabBackup.isSelected = (tab == SettingsTab.BACKUP)
        tabAccessibility.isSelected = (tab == SettingsTab.ACCESSIBILITY)

        ivTabTheme.imageTintList = ColorStateList.valueOf(if (tab == SettingsTab.THEME) accentColor else dimColor)
        ivTabEqualizer.imageTintList = ColorStateList.valueOf(if (tab == SettingsTab.EQUALIZER) accentColor else dimColor)
        ivTabBackup.imageTintList = ColorStateList.valueOf(if (tab == SettingsTab.BACKUP) accentColor else dimColor)
        ivTabAccessibility.imageTintList = ColorStateList.valueOf(if (tab == SettingsTab.ACCESSIBILITY) accentColor else dimColor)

        panelTheme.visibility = if (tab == SettingsTab.THEME) View.VISIBLE else View.GONE
        panelEqualizer.visibility = if (tab == SettingsTab.EQUALIZER) View.VISIBLE else View.GONE
        panelBackup.visibility = if (tab == SettingsTab.BACKUP) View.VISIBLE else View.GONE
        panelAccessibility.visibility = if (tab == SettingsTab.ACCESSIBILITY) View.VISIBLE else View.GONE

        if (tab == SettingsTab.BACKUP) {
            refreshBackupFavCount()
            scanAndDisplayBackupFiles()
        }
    }

    // ============================================================================
    // PANEL 1: TEMA
    // ============================================================================

    private fun initThemePanel(view: View) {
        rvThemes = view.findViewById(R.id.rv_themes)
        tvCurrentThemeBadge = view.findViewById(R.id.tv_current_theme_badge)

        val currentThemeId = ThemeManager.getCurrentThemeId(requireContext())
        val currentThemeName = Constants.THEMES.firstOrNull { it.id == currentThemeId }?.name ?: currentThemeId
        tvCurrentThemeBadge.text = getString(R.string.settings_theme_active_badge, currentThemeName)

        rvThemes.layoutManager = GridLayoutManager(requireContext(), 5)
        themeAdapter = ThemeAdapter(requireContext(), Constants.THEMES, currentThemeId) { selectedTheme ->
            val position = Constants.THEMES.indexOfFirst { it.id == selectedTheme.id }
            showThemeConfirmation(selectedTheme, position)
        }.apply {
            onNavigateLeft = { tabTheme.requestFocus() }
        }
        rvThemes.adapter = themeAdapter

        val focusThemeId = arguments?.getString(ARG_FOCUS_THEME_ID)
        if (focusThemeId != null) {
            val targetPos = Constants.THEMES.indexOfFirst { it.id == focusThemeId }.coerceAtLeast(0)
            rvThemes.post {
                rvThemes.scrollToPosition(targetPos)
                rvThemes.postDelayed({
                    val targetView = rvThemes.layoutManager?.findViewByPosition(targetPos)
                        ?: rvThemes.getChildAt(targetPos)
                    targetView?.requestFocus()
                }, 120)
            }
        }
    }

    // ============================================================================
    // PANEL 2: EKVILAJZER
    // ============================================================================

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initEqualizerPanel(view: View) {
        switchEq = view.findViewById(R.id.switch_eq)
        scrollPresetButtons = view.findViewById(R.id.scroll_preset_buttons)
        presetButtonsContainer = view.findViewById(R.id.preset_buttons_container)
        eqBandsContainer = view.findViewById(R.id.eq_bands_container)

        // Init Equalizer with existing session if not yet initialized for this session
        val sessionId = RadioManager.player?.audioSessionId ?: EqualizerManager.getCurrentSessionId()
        if (sessionId != 0 && EqualizerManager.getCurrentSessionId() != sessionId) {
            EqualizerManager.init(requireContext(), sessionId)
        }

        switchEq.isChecked = EqualizerManager.isEnabled()

        switchEq.nextFocusLeftId = R.id.tab_equalizer
        scrollPresetButtons.nextFocusLeftId = R.id.tab_equalizer

        switchEq.setOnCheckedChangeListener { _, isChecked ->
            EqualizerManager.setEnabled(requireContext(), isChecked)
            updateEqBandsEnabled(isChecked)
        }

        setupPresetButtons()
        createBandSliders()
        updateEqBandsEnabled(switchEq.isChecked)
    }

    private fun setupPresetButtons() {
        presetButtonsContainer.removeAllViews()
        presetButtons.clear()

        val presets = EqualizerManager.presets
        val currentPreset = EqualizerManager.getCurrentPresetIndex()

        for (i in presets.indices) {
            val button = Button(requireContext()).apply {
                text = presets[i].name
                textSize = 13f
                setTextColor(dimColor)
                isFocusable = switchEq.isChecked
                isFocusableInTouchMode = switchEq.isChecked
                isEnabled = switchEq.isChecked
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(38)
                ).apply {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
                setPadding(dpToPx(14), 0, dpToPx(14), 0)

                setOnClickListener {
                    selectPreset(i)
                }

                onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    updatePresetButtonAppearance(this, hasFocus, i == EqualizerManager.getCurrentPresetIndex())
                }
            }
            presetButtons.add(button)
            presetButtonsContainer.addView(button)
        }

        updatePresetButtonsHighlight(currentPreset)
    }

    private fun selectPreset(index: Int) {
        if (!switchEq.isChecked) return
        EqualizerManager.applyPreset(requireContext(), index)
        updateSliderValues()
        updatePresetButtonsHighlight(index)
        Toast.makeText(context, getString(R.string.settings_eq_preset_applied, EqualizerManager.presets[index].name), Toast.LENGTH_SHORT).show()
    }

    private fun updatePresetButtonsHighlight(selectedIndex: Int) {
        presetButtons.forEachIndexed { idx, btn ->
            updatePresetButtonAppearance(btn, btn.hasFocus(), idx == selectedIndex)
        }
    }

    private fun updatePresetButtonAppearance(button: Button, hasFocus: Boolean, isSelected: Boolean) {
        val drawable = GradientDrawable().apply {
            cornerRadius = 999f
            when {
                hasFocus -> {
                    setColor(accentColor)
                    setStroke(3, ringColor)
                }
                isSelected -> {
                    setColor(accentColor and 0x40FFFFFF)
                    setStroke(2, accentColor)
                }
                else -> {
                    setColor(Color.TRANSPARENT)
                    setStroke(1, Color.parseColor("#44FFFFFF"))
                }
            }
        }
        button.background = drawable
        button.setTextColor(if (hasFocus || isSelected) onAccentColor else dimColor)
    }

    private fun createBandSliders() {
        eqBandsContainer.removeAllViews()
        bandSeekBars.clear()
        bandContainers.clear()

        val numBands = EqualizerManager.getNumberOfBands().toInt().coerceIn(1, 10)
        val (minLevel, maxLevel) = EqualizerManager.getBandLevelRange()
        val range = (maxLevel - minLevel).coerceAtLeast(1)

        for (i in 0 until numBands) {
            val band = i.toShort()
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                isFocusable = switchEq.isChecked
                isFocusableInTouchMode = switchEq.isChecked
                isEnabled = switchEq.isChecked
                tag = i

                val bg = GradientDrawable().apply {
                    cornerRadius = dpToPx(10).toFloat()
                    setColor(Color.TRANSPARENT)
                }
                background = bg
            }

            val level = EqualizerManager.getBandLevel(band)
            val dbLabel = TextView(requireContext()).apply {
                text = formatDb(level)
                textSize = 11f
                setTextColor(dimColor)
                gravity = android.view.Gravity.CENTER
            }
            container.addView(dbLabel)

            val seekBar = SeekBar(requireContext()).apply {
                max = range.coerceAtLeast(1)
                progress = (level - minLevel).coerceIn(0, range.coerceAtLeast(1))
                rotation = 270f
                layoutParams = LinearLayout.LayoutParams(dpToPx(110), 0, 1f).apply {
                    setMargins(0, dpToPx(12), 0, dpToPx(12))
                }
                progressTintList = ColorStateList.valueOf(accentColor)
                thumbTintList = ColorStateList.valueOf(accentColor)
                isFocusable = false

                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val newLevel = (progress + minLevel).toShort()
                            EqualizerManager.setBandLevel(requireContext(), band, newLevel)
                            dbLabel.text = formatDb(newLevel)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            bandSeekBars.add(seekBar)
            container.addView(seekBar)

            val freqLabel = TextView(requireContext()).apply {
                text = EqualizerManager.getFrequencyLabel(band)
                textSize = 11f
                setTextColor(accentColor)
                gravity = android.view.Gravity.CENTER
            }
            container.addView(freqLabel)

            container.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                val bgDrawable = GradientDrawable().apply {
                    cornerRadius = dpToPx(10).toFloat()
                    if (hasFocus && switchEq.isChecked) {
                        setColor(accentColor and 0x25FFFFFF)
                        setStroke(3, ringColor)
                    } else {
                        setColor(Color.TRANSPARENT)
                        setStroke(0, Color.TRANSPARENT)
                    }
                }
                container.background = bgDrawable
                if (hasFocus) {
                    currentFocusedBandIndex = i
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            adjustBandValue(i, 100, dbLabel)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            adjustBandValue(i, -100, dbLabel)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            // Reset band to 0dB
                            resetBandToZero(i, dbLabel)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (i == 0) {
                                tabEqualizer.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }

            bandContainers.add(container)
            eqBandsContainer.addView(container)
        }
    }

    private fun adjustBandValue(bandIndex: Int, delta: Int, dbLabel: TextView) {
        if (!switchEq.isChecked) return
        val seekBar = bandSeekBars[bandIndex]
        val (minLevel, maxLevel) = EqualizerManager.getBandLevelRange()
        val currentLevel = EqualizerManager.getBandLevel(bandIndex.toShort())
        val newLevel = (currentLevel + delta).coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()

        EqualizerManager.setBandLevel(requireContext(), bandIndex.toShort(), newLevel)
        seekBar.progress = newLevel - minLevel
        dbLabel.text = formatDb(newLevel)
    }

    private fun resetBandToZero(bandIndex: Int, dbLabel: TextView) {
        if (!switchEq.isChecked) return
        val (minLevel, _) = EqualizerManager.getBandLevelRange()
        EqualizerManager.setBandLevel(requireContext(), bandIndex.toShort(), 0.toShort())
        bandSeekBars[bandIndex].progress = 0 - minLevel
        dbLabel.text = "0dB"
        Toast.makeText(context, getString(R.string.settings_eq_reset_toast), Toast.LENGTH_SHORT).show()
    }

    private fun updateSliderValues() {
        val (minLevel, _) = EqualizerManager.getBandLevelRange()
        for (i in bandSeekBars.indices) {
            val level = EqualizerManager.getBandLevel(i.toShort())
            bandSeekBars[i].progress = level - minLevel
            val container = bandContainers.getOrNull(i)
            val dbLabel = container?.getChildAt(0) as? TextView
            dbLabel?.text = formatDb(level)
        }
    }

    private fun updateEqBandsEnabled(enabled: Boolean) {
        eqBandsContainer.alpha = if (enabled) 1f else 0.35f
        presetButtonsContainer.alpha = if (enabled) 1f else 0.35f
        
        presetButtons.forEach {
            it.isEnabled = enabled
            it.isFocusable = enabled
            it.isFocusableInTouchMode = enabled
        }
        scrollPresetButtons.isEnabled = enabled
        scrollPresetButtons.isFocusable = false
        scrollPresetButtons.isFocusableInTouchMode = false
        
        bandContainers.forEach {
            it.isEnabled = enabled
            it.isFocusable = enabled
            it.isFocusableInTouchMode = enabled
            if (!enabled) {
                it.clearFocus()
                it.background = GradientDrawable().apply {
                    cornerRadius = dpToPx(10).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(0, Color.TRANSPARENT)
                }
            }
        }
        bandSeekBars.forEach {
            it.isEnabled = enabled
            it.isFocusable = false
        }
        
        if (!enabled) {
            switchEq.nextFocusDownId = R.id.switch_eq
            currentFocusedBandIndex = -1
        } else {
            switchEq.nextFocusDownId = View.NO_ID
        }
    }

    private fun formatDb(level: Short): String {
        val db = level / 100.0
        val text = if (level % 100 == 0) {
            String.format(java.util.Locale.US, "%.0f", db)
        } else {
            String.format(java.util.Locale.US, "%.1f", db)
        }
        return if (db > 0) "+${text}dB" else "${text}dB"
    }

    // ============================================================================
    // PANEL 3: BEKAP (EXPORT & IMPORT)
    // ============================================================================

    private fun initBackupPanel(view: View) {
        tvBackupFavCount = view.findViewById(R.id.tv_backup_fav_count)
        btnExportCard = view.findViewById(R.id.btn_export_card)
        btnImportCard = view.findViewById(R.id.btn_import_card)
        rvBackupFiles = view.findViewById(R.id.rv_backup_files)
        tvBackupStatus = view.findViewById(R.id.tv_backup_status)
        tvBackupFilesTitle = view.findViewById(R.id.tv_backup_files_title)

        rvBackupFiles.layoutManager = LinearLayoutManager(requireContext())
        backupFilesAdapter = BackupFilesAdapter(emptyList()) { fileItem ->
            confirmRestoreFile(fileItem)
        }
        rvBackupFiles.adapter = backupFilesAdapter

        btnExportCard.nextFocusLeftId = R.id.tab_backup
        btnExportCard.nextFocusRightId = R.id.btn_import_card
        btnImportCard.nextFocusLeftId = R.id.btn_export_card

        btnExportCard.setOnClickListener {
            handleExportClick()
        }

        btnImportCard.setOnClickListener {
            scanAndDisplayBackupFiles(showFeedback = true)
        }
    }

    private fun refreshBackupFavCount() {
        val count = FavoritesManager.getFavoriteCount()
        tvBackupFavCount.text = getString(R.string.settings_backup_fav_count_format, count)
    }

    private fun handleExportClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performExport()
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                performExport()
            } else {
                pendingExport = true
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun performExport() {
        val context = context ?: return
        val count = FavoritesManager.getFavoriteCount()
        if (count == 0) {
            Toast.makeText(context, getString(R.string.settings_backup_no_favs), Toast.LENGTH_SHORT).show()
            return
        }

        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val fileName = "Bekap_RadioEX_$timeStamp.json"
        val jsonContent = FavoritesManager.getFavoritesFullJson()

        lifecycleScope.launch(Dispatchers.IO) {
            var savedSuccess = false
            var savedPath = ""

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // MediaStore API for Android 10+ (Scoped Storage)
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri: Uri? = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            OutputStreamWriter(os).use { writer ->
                                writer.write(jsonContent)
                            }
                        }
                        savedSuccess = true
                        savedPath = "Download/$fileName"
                    }
                } else {
                    // Legacy External Storage for API < 29
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val targetFile = File(downloadsDir, fileName)
                    targetFile.writeText(jsonContent)
                    savedSuccess = true
                    savedPath = targetFile.absolutePath
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to app external files dir
                try {
                    val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    val fallbackFile = File(fallbackDir, fileName)
                    fallbackFile.writeText(jsonContent)
                    savedSuccess = true
                    savedPath = fallbackFile.absolutePath
                } catch (err: Exception) {
                    err.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                if (!isAdded || isRemoving) return@withContext
                if (savedSuccess) {
                    tvBackupStatus.text = getString(R.string.settings_backup_save_status, fileName, count)
                    Toast.makeText(context, getString(R.string.settings_backup_save_success, fileName), Toast.LENGTH_LONG).show()
                    scanAndDisplayBackupFiles()
                } else {
                    tvBackupStatus.text = getString(R.string.settings_backup_save_error)
                    Toast.makeText(context, getString(R.string.settings_backup_save_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    data class BackupFileItem(
        val file: File?,
        val name: String,
        val formattedDate: String,
        val sizeFormatted: String,
        val uri: Uri? = null
    )

    private fun scanAndDisplayBackupFiles(showFeedback: Boolean = false) {
        val context = context ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<BackupFileItem>()
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

            // 1. Scan filesystem Downloads directory
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDownloads.exists() && publicDownloads.isDirectory) {
                    val foundFiles = publicDownloads.listFiles { f ->
                        f.isFile && (f.name.startsWith("Bekap_RadioEX", ignoreCase = true) || f.name.endsWith(".json", ignoreCase = true))
                    }
                    foundFiles?.forEach { f ->
                        val date = dateFormat.format(Date(f.lastModified()))
                        val size = formatFileSize(f.length())
                        list.add(BackupFileItem(f, f.name, date, size))
                    }
                }
            } catch (_: Exception) {}

            // 2. Scan app external downloads dir fallback
            try {
                val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (fallbackDir != null && fallbackDir.exists() && fallbackDir.isDirectory) {
                    val found = fallbackDir.listFiles { f ->
                        f.isFile && f.name.endsWith(".json", ignoreCase = true)
                    }
                    found?.forEach { f ->
                        if (list.none { it.name == f.name }) {
                            val date = dateFormat.format(Date(f.lastModified()))
                            val size = formatFileSize(f.length())
                            list.add(BackupFileItem(f, f.name, date, size))
                        }
                    }
                }
            } catch (_: Exception) {}

            // Sort latest first
            list.sortByDescending { it.file?.lastModified() ?: 0L }

            withContext(Dispatchers.Main) {
                if (!isAdded || isRemoving) return@withContext
                backupFilesAdapter?.updateList(list)

                if (list.isNotEmpty()) {
                    tvBackupFilesTitle.visibility = View.VISIBLE
                    rvBackupFiles.visibility = View.VISIBLE
                    tvBackupStatus.text = getString(R.string.settings_backup_files_found_status, list.size)
                    if (showFeedback) {
                        Toast.makeText(context, getString(R.string.settings_backup_files_found, list.size), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    tvBackupFilesTitle.visibility = View.GONE
                    rvBackupFiles.visibility = View.GONE
                    tvBackupStatus.text = getString(R.string.settings_backup_files_none_status)
                    if (showFeedback) {
                        Toast.makeText(context, getString(R.string.settings_backup_files_none), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun confirmRestoreFile(item: BackupFileItem) {
        val context = context ?: return
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.settings_backup_restore_title)
            .setMessage(getString(R.string.settings_backup_restore_message, item.name))
            .setPositiveButton(R.string.settings_backup_merge) { _, _ ->
                readAndRestoreBackup(item, FavoritesManager.ImportMode.MERGE)
            }
            .setNegativeButton(R.string.settings_backup_replace) { _, _ ->
                readAndRestoreBackup(item, FavoritesManager.ImportMode.REPLACE)
            }
            .setNeutralButton(R.string.settings_backup_cancel, null)
            .create()

        dialog.show()
    }

    private fun readAndRestoreBackup(item: BackupFileItem, mode: FavoritesManager.ImportMode) {
        val context = context ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonContent = if (item.file != null && item.file.exists()) {
                    item.file.readText()
                } else if (item.uri != null) {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: ""
                } else {
                    ""
                }

                withContext(Dispatchers.Main) {
                    if (!isAdded || isRemoving) return@withContext
                    if (jsonContent.isEmpty()) {
                        Toast.makeText(context, getString(R.string.settings_backup_empty_file), Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val result = FavoritesManager.importFavoritesFromJson(jsonContent, mode)
                    if (result.success) {
                        refreshBackupFavCount()
                        tvBackupStatus.text = getString(R.string.settings_backup_import_status, result.totalFavorites)
                        Toast.makeText(
                            context,
                            getString(R.string.settings_backup_import_success, result.importedCount, result.totalFavorites),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, result.errorMessage ?: getString(R.string.settings_backup_import_failed), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (isAdded && !isRemoving) {
                        Toast.makeText(context, getString(R.string.settings_backup_read_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun initThemeConfirmation(view: View) {
        themeConfirmOverlay = view.findViewById(R.id.theme_confirm_overlay)
        tvConfirmTitle = view.findViewById(R.id.tv_confirm_title)
        tvConfirmSubtitle = view.findViewById(R.id.tv_confirm_subtitle)
        tvConfirmMessage = view.findViewById(R.id.tv_confirm_message)
        viewConfirmColorPrimary = view.findViewById(R.id.view_confirm_color_primary)
        viewConfirmColorSecondary = view.findViewById(R.id.view_confirm_color_secondary)
        tvConfirmThemeName = view.findViewById(R.id.tv_confirm_theme_name)
        tvConfirmModeBadge = view.findViewById(R.id.tv_confirm_mode_badge)
        btnConfirmYes = view.findViewById(R.id.btn_confirm_yes)
        btnConfirmNo = view.findViewById(R.id.btn_confirm_no)

        btnConfirmNo.setOnClickListener {
            cancelThemeConfirmation()
        }

        btnConfirmYes.setOnClickListener {
            confirmThemeActivation()
        }

        btnConfirmNo.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        cancelThemeConfirmation()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        btnConfirmYes.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // Trap focus inside overlay
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        btnConfirmYes.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        confirmThemeActivation()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        btnConfirmNo.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Trap focus inside overlay
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    private fun showThemeConfirmation(theme: Constants.ThemeConfig, position: Int) {
        val currentThemeId = ThemeManager.getCurrentThemeId(requireContext())
        if (theme.id == currentThemeId) {
            Toast.makeText(context, getString(R.string.theme_already_active, theme.name), Toast.LENGTH_SHORT).show()
            return
        }

        pendingTheme = theme
        lastFocusedThemePosition = position

        tvConfirmMessage.text = getString(R.string.theme_confirm_question, theme.name)
        tvConfirmThemeName.text = theme.name
        val isLight = Constants.isLightTheme(theme.id)
        tvConfirmModeBadge.text = getString(if (isLight) R.string.theme_mode_light else R.string.theme_mode_dark)

        val colors = ThemeAdapter.THEME_COLORS[theme.id] ?: Pair(Color.GRAY, Color.LTGRAY)
        (viewConfirmColorPrimary.background.mutate() as? GradientDrawable)?.setColor(colors.first)
            ?: run { viewConfirmColorPrimary.setBackgroundColor(colors.first) }
        (viewConfirmColorSecondary.background.mutate() as? GradientDrawable)?.setColor(colors.second)
            ?: run { viewConfirmColorSecondary.setBackgroundColor(colors.second) }

        // Block background focus while overlay is displayed
        view?.findViewById<ViewGroup>(R.id.settings_body)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        themeConfirmOverlay.visibility = View.VISIBLE
        themeConfirmOverlay.alpha = 0f
        themeConfirmOverlay.animate()
            .alpha(1f)
            .setDuration(160)
            .withEndAction {
                btnConfirmYes.requestFocus()
            }
            .start()
    }

    private fun cancelThemeConfirmation() {
        if (!::themeConfirmOverlay.isInitialized || themeConfirmOverlay.visibility != View.VISIBLE) return
        themeConfirmOverlay.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                themeConfirmOverlay.visibility = View.GONE
                // Restore background focusability
                view?.findViewById<ViewGroup>(R.id.settings_body)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                val restorePos = lastFocusedThemePosition
                pendingTheme = null
                lastFocusedThemePosition = -1
                if (restorePos != -1) {
                    rvThemes.post {
                        val targetView = rvThemes.layoutManager?.findViewByPosition(restorePos)
                            ?: rvThemes.getChildAt(restorePos)
                        targetView?.requestFocus()
                    }
                }
            }
            .start()
    }

    private fun confirmThemeActivation() {
        val themeToActivate = pendingTheme ?: return
        view?.findViewById<ViewGroup>(R.id.settings_body)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        ThemeManager.setTheme(requireContext(), themeToActivate.id)
        MainActivity.pendingReopenThemeSettings = themeToActivate.id

        dismiss()
        activity?.let { act ->
            ThemeManager.applyTheme(act)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (themeChanged) {
            activity?.let { act ->
                ThemeManager.applyTheme(act)
            }
        }
    }

    // ============================================================================
    // PANEL 4: PRISTUPAČNOST
    // ============================================================================

    private fun initAccessibilityPanel(view: View) {
        switchReducedMotion = view.findViewById(R.id.switch_reduced_motion)
        btnLangSr = view.findViewById(R.id.btn_lang_sr)
        btnLangEn = view.findViewById(R.id.btn_lang_en)

        val isReduced = AccessibilityManager.isReducedMotion(requireContext())
        switchReducedMotion.isChecked = isReduced
        switchReducedMotion.setOnCheckedChangeListener { _, isChecked ->
            AccessibilityManager.setReducedMotion(requireContext(), isChecked)
        }

        updateLanguageButtons()

        btnLangSr.setOnClickListener {
            changeLanguage(AccessibilityManager.LANG_SR)
        }

        btnLangEn.setOnClickListener {
            changeLanguage(AccessibilityManager.LANG_EN)
        }
    }

    private fun updateLanguageButtons() {
        val currentLang = AccessibilityManager.getLanguage(requireContext())
        btnLangSr.isActivated = (currentLang == AccessibilityManager.LANG_SR)
        btnLangEn.isActivated = (currentLang == AccessibilityManager.LANG_EN)
    }

    private fun changeLanguage(langCode: String) {
        if (AccessibilityManager.getLanguage(requireContext()) == langCode) return
        MainActivity.pendingReopenSettingsTab = SettingsTab.ACCESSIBILITY
        AccessibilityManager.setLanguage(requireContext(), langCode)
        dismiss()
    }

    companion object {
        const val TAG = "SettingsDialogFragment"
        private const val ARG_INITIAL_TAB = "INITIAL_TAB"
        private const val ARG_FOCUS_THEME_ID = "INITIAL_FOCUS_THEME_ID"

        fun newInstance(
            initialTab: SettingsTab = SettingsTab.THEME,
            initialFocusThemeId: String? = null
        ): SettingsDialogFragment {
            return SettingsDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_TAB, initialTab.name)
                    putString(ARG_FOCUS_THEME_ID, initialFocusThemeId)
                }
            }
        }
    }

    // ============================================================================
    // BackupFilesAdapter (Inner class)
    // ============================================================================
    private class BackupFilesAdapter(
        private var items: List<BackupFileItem>,
        private val onItemClick: (BackupFileItem) -> Unit
    ) : RecyclerView.Adapter<BackupFilesAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvFileName: TextView = itemView.findViewById(R.id.tv_file_name)
            val tvFileDetails: TextView = itemView.findViewById(R.id.tv_file_details)
            val btnRestoreAction: TextView = itemView.findViewById(R.id.btn_restore_action)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_backup_file, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvFileName.text = item.name
            holder.tvFileDetails.text = "${item.formattedDate} · ${item.sizeFormatted}"

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateList(newItems: List<BackupFileItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
