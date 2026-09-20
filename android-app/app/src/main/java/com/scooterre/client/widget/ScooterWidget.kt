package com.scooterre.client.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.scooterre.client.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
internal val KEY_MAC = stringPreferencesKey("mac")
internal val KEY_BATTERY = longPreferencesKey("battery_level")
internal val KEY_LOCKED = booleanPreferencesKey("is_locked")
internal val KEY_REMAINING_KM = doublePreferencesKey("remaining_km")
internal val KEY_TIMESTAMP = longPreferencesKey("timestamp")
internal val KEY_LANG = stringPreferencesKey("lang")

// Widget-background navy, matching the app's own dark theme (ui/Theme.kt) rather than Glance's
// default system surface color - keeps the widget visually part of the same app.
private val WidgetBackground = Color(0xFF171A24)
private val AccentBlue = Color(0xFF7EA6FF)

/**
 * Home-screen widget showing the last known status for whichever scooter the app most recently
 * talked to - NOT a live reading, since the app has no background service (see project research
 * log) and cannot maintain a BLE connection while closed. Tapping it opens the app. Layout scales
 * with the widget's actual placed size (`SizeMode.Responsive`) - a narrow/short placement shows
 * just the essentials (battery), a bigger one adds lock state and range, rather than clipping or
 * leaving empty space either way.
 */
class ScooterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(90.dp, 60.dp),
            DpSize(180.dp, 60.dp),
            DpSize(180.dp, 110.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetContent(
                deviceName = prefs[KEY_DEVICE_NAME],
                batteryLevel = prefs[KEY_BATTERY] ?: -1L,
                isLocked = prefs[KEY_LOCKED],
                remainingKm = prefs[KEY_REMAINING_KM],
                timestampMillis = prefs[KEY_TIMESTAMP] ?: 0L,
                lang = prefs[KEY_LANG] ?: "DE",
            )
        }
    }
}

private fun batteryEmoji(level: Long): String = when {
    level < 0 -> "🔋"
    level <= 15 -> "🪫"
    else -> "🔋"
}

@Composable
private fun WidgetContent(
    deviceName: String?,
    batteryLevel: Long,
    isLocked: Boolean?,
    remainingKm: Double?,
    timestampMillis: Long,
    lang: String,
) {
    val isDe = lang != "EN"
    val size = LocalSize.current
    val compact = size.height < 90.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground, WidgetBackground))
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            deviceName ?: "Scooter",
            maxLines = 1,
            style = TextStyle(color = ColorProvider(Color(0xFFB8C0D8), Color(0xFFB8C0D8)), fontWeight = FontWeight.Medium, fontSize = 12.sp),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(batteryEmoji(batteryLevel), style = TextStyle(fontSize = 22.sp))
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                if (batteryLevel >= 0) "$batteryLevel%" else "–",
                style = TextStyle(color = ColorProvider(Color.White, Color.White), fontWeight = FontWeight.Bold, fontSize = 26.sp),
            )
        }
        if (!compact) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                isLocked?.let {
                    Text(if (it) "🔒" else "🔓", style = TextStyle(fontSize = 13.sp))
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        if (it) (if (isDe) "Gesperrt" else "Locked") else (if (isDe) "Frei" else "Unlocked"),
                        style = TextStyle(color = ColorProvider(Color(0xFFB8C0D8), Color(0xFFB8C0D8)), fontSize = 11.sp),
                    )
                }
                remainingKm?.let {
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Text("📍", style = TextStyle(fontSize = 13.sp))
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        "${(it * 10).roundToInt() / 10.0} km",
                        style = TextStyle(color = ColorProvider(Color(0xFFB8C0D8), Color(0xFFB8C0D8)), fontSize = 11.sp),
                    )
                }
            }
            if (timestampMillis > 0) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))
                Text(
                    (if (isDe) "Stand " else "as of ") + time,
                    style = TextStyle(color = ColorProvider(Color(0xFF6E7690), Color(0xFF6E7690)), fontSize = 10.sp),
                )
            }
        }
    }
}
