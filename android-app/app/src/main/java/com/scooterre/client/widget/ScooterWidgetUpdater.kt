package com.scooterre.client.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/** Pushes the latest known status to every placed instance of [ScooterWidget] - called from
 * [com.scooterre.client.viewmodel.ScooterViewModel] after each successful refresh, never as its
 * own BLE round trip. A no-op (cheap early return) when no widget is currently placed, so this is
 * safe to call unconditionally on every refresh regardless of whether anyone has the widget on
 * their home screen. */
object ScooterWidgetUpdater {
    suspend fun update(
        context: Context,
        deviceName: String?,
        mac: String,
        batteryLevel: Long?,
        isLocked: Boolean?,
        remainingKm: Double?,
        lang: String,
    ) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(ScooterWidget::class.java)
        if (ids.isEmpty()) return
        val widget = ScooterWidget()
        for (id in ids) {
            updateAppWidgetState(context, id) { prefs ->
                if (deviceName != null) prefs[KEY_DEVICE_NAME] = deviceName else prefs.remove(KEY_DEVICE_NAME)
                prefs[KEY_MAC] = mac
                prefs[KEY_BATTERY] = batteryLevel ?: -1L
                if (isLocked != null) prefs[KEY_LOCKED] = isLocked else prefs.remove(KEY_LOCKED)
                if (remainingKm != null) prefs[KEY_REMAINING_KM] = remainingKm else prefs.remove(KEY_REMAINING_KM)
                prefs[KEY_TIMESTAMP] = System.currentTimeMillis()
                prefs[KEY_LANG] = lang
            }
            widget.update(context, id)
        }
    }
}
