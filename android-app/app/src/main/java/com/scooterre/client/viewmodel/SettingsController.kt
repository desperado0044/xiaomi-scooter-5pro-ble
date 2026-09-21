package com.scooterre.client.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import com.scooterre.client.protocol.*
import com.scooterre.client.reminder.InsuranceReminders
import com.scooterre.client.reminder.InsuranceSchedule
import com.scooterre.client.ui.*
import com.scooterre.client.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update

/** The app-wide settings: each setter saves the preference and updates the UI state. */
internal class SettingsController(
    private val shared: Shared,
    private val onUnitsChanged: () -> Unit,
) {
    private val _state get() = shared.state
    private val prefs get() = shared.prefs
    private val s get() = shared.s
    private val app get() = shared.app
    private val scope get() = shared.scope

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setAutoBrightness(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BRIGHTNESS, enabled).apply()
        _state.update { it.copy(autoBrightness = enabled) }
    }

    fun setLanguage(lang: Lang) {
        prefs.edit().putString(KEY_LANG, lang.name).apply()
        _state.update { it.copy(language = lang) }
    }

    fun setUnits(units: UnitSystem) {
        prefs.edit().putString(KEY_UNITS, units.name).apply()
        _state.update { it.copy(units = units) }
        onUnitsChanged()
    }

    fun setRefreshRate(rate: RefreshRate) {
        prefs.edit().putString(KEY_REFRESH_RATE, rate.name).apply()
        _state.update { it.copy(refreshRate = rate) }
    }

    fun setAutoConnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
        _state.update { it.copy(autoConnect = enabled) }
    }

    fun setConfirmCritical(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_CRITICAL, enabled).apply()
        _state.update { it.copy(confirmCritical = enabled) }
    }

    fun setRideTracking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RIDE_TRACKING, enabled).apply()
        _state.update { it.copy(rideTracking = enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        _state.update { it.copy(keepScreenOn = enabled) }
    }

    fun toggleLanguage() {
        val next = if (_state.value.language == Lang.DE) Lang.EN else Lang.DE
        prefs.edit().putString(KEY_LANG, next.name).apply()
        _state.update { it.copy(language = next) }
    }

    fun setAppLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _state.update { it.copy(appLock = enabled) }
    }

    fun reloadSettings() {
        _state.update {
            it.copy(
                language = resolveLang(prefs.getString(KEY_LANG, null)),
                themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
                keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
                autoBrightness = prefs.getBoolean(KEY_AUTO_BRIGHTNESS, false),
                units = runCatching { UnitSystem.valueOf(prefs.getString(KEY_UNITS, null) ?: "METRIC") }.getOrDefault(UnitSystem.METRIC),
                autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false),
                refreshRate = runCatching { RefreshRate.valueOf(prefs.getString(KEY_REFRESH_RATE, null) ?: "NORMAL") }.getOrDefault(RefreshRate.NORMAL),
                confirmCritical = prefs.getBoolean(KEY_CONFIRM_CRITICAL, false),
                rideTracking = prefs.getBoolean(KEY_RIDE_TRACKING, true),
                updateCheck = prefs.getBoolean(KEY_UPDATE_CHECK, true),
            )
        }
    }
}
