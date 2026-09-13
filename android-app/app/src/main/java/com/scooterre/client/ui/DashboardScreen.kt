package com.scooterre.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scooterre.client.protocol.SpecProperties
import com.scooterre.client.protocol.SpecProperty
import com.scooterre.client.protocol.SpecReadResult
import com.scooterre.client.protocol.SpecType
import com.scooterre.client.viewmodel.UiState

private val GROUPS = listOf(1, 2, 3, 4)

private fun tabName(siid: Int, s: AppStrings): String = when (siid) {
    1 -> s.tabRideBattery
    2 -> s.tabSettings
    3 -> s.tabBatteryDetail
    else -> s.tabIdentification
}

/** Scale factor for numeric properties. Confirmed against the plugin's own UNITS table and
 * verified raw-byte captures from the real device (docs/RESEARCH_LOG.md) - several of these
 * (distances, voltage, speeds) are FLOAT on the wire but actually store value*100 as a
 * whole-number float, e.g. raw 6050 -> 60.5 km. The unit strings themselves are language-neutral
 * (V, A, W, km, °C, %, mAh) except NUMBER_OF_CYCLES, handled separately in [unitSuffix]. */
private val UNIT_SCALES: Map<String, Double> = mapOf(
    "VOLTAGE" to 0.01, "TOTAL_MILEAGE" to 0.01, "CURRENT_MILEAGE" to 0.01, "REMAINING_MILEAGE" to 0.01,
    "AVERAGE_SPEED" to 0.01, "HIGHEST_SPEED" to 0.01,
    // Confirmed against Xiaomi Home's live reading on the real device (0.03 A vs. our
    // undivided 3 A) - these are also stored as value*100 on the wire, like voltage/distance.
    "CURRENT" to 0.01, "POWER" to 0.01,
    "BATTERY_LEVEL" to 1.0, "SOH" to 1.0, "REMAINING_BATTERY" to 1.0,
    "BATTERY_TEMPERATURE" to 1.0, "SCOOTER_TEMPERATURE" to 1.0, "NUMBER_OF_CYCLES" to 1.0,
)

private val UNIT_SUFFIX: Map<String, String> = mapOf(
    "VOLTAGE" to "V", "TOTAL_MILEAGE" to "km", "CURRENT_MILEAGE" to "km", "REMAINING_MILEAGE" to "km",
    "AVERAGE_SPEED" to "km/h", "HIGHEST_SPEED" to "km/h", "CURRENT" to "A", "POWER" to "W",
    "BATTERY_LEVEL" to "%", "SOH" to "%", "REMAINING_BATTERY" to "mAh",
    "BATTERY_TEMPERATURE" to "°C", "SCOOTER_TEMPERATURE" to "°C",
)

private fun unitSuffix(name: String, lang: Lang): String? =
    if (name == "NUMBER_OF_CYCLES") (if (lang == Lang.DE) "Zyklen" else "cycles") else UNIT_SUFFIX[name]

/** PRODUCTION_DATE/ACTIVATION_DATE come back as bare digit strings (YYMMDD or YYYYMMDD) -
 * shown as an ISO date instead of the raw digits. */
private fun formatDateString(raw: String): String {
    val s = raw.trim()
    if (!s.all { it.isDigit() }) return raw
    return when (s.length) {
        8 -> "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
        6 -> "20${s.substring(0, 2)}-${s.substring(2, 4)}-${s.substring(4, 6)}"
        else -> raw
    }
}

@Composable
fun DashboardScreen(
    state: UiState,
    onRefreshAll: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
) {
    val s = strings(state.language)
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    state.deviceName ?: s.fallbackDeviceName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(state.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRefreshAll) { Text(s.refreshButton) }
                    TextButton(onClick = onDisconnect) { Text(s.disconnectButton) }
                    TextButton(onClick = onToggleLanguage) { Text(if (state.language == Lang.DE) "🇩🇪" else "🇬🇧") }
                }
            }
        }
        state.error?.let {
            Text(
                "${s.errorPrefix}$it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        var selectedTab by remember { mutableStateOf(0) }
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            GROUPS.forEachIndexed { index, siid ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tabName(siid, s)) },
                )
            }
        }

        val activeSiid = GROUPS[selectedTab]
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        ) {
            items(SpecProperties.ALL.filter { it.siid == activeSiid }, key = { it.name }) { property ->
                PropertyRow(property, state.values[property.name], state.language, onSetBool, onSetNumeric)
            }
        }
    }
}

@Composable
private fun PropertyRow(
    property: SpecProperty,
    result: SpecReadResult?,
    lang: Lang,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
) {
    val s = strings(lang)
    val settable = property.name in SpecProperties.SETTABLE
    val isCycle = property.name in SpecProperties.CYCLE_PROPERTIES
    val isError = result != null && !result.ok

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        propertyName(property.name, lang),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        displayValue(property, result, lang, s),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Cycle-button properties (fixed value sets like RIDING_MODE, ENERGY_RECOVERY)
                // need more width than fits next to the label - rendered as their own
                // full-width row below instead (see branch further down).
                if (settable && result?.ok == true && !isCycle) {
                    when (property.type) {
                        SpecType.BOOL -> {
                            val current = (result.value as? Long) == 1L
                            var showRegionWarning by remember { mutableStateOf(false) }
                            val warningText = regionWarning(property.name, lang)
                            Switch(
                                checked = current,
                                onCheckedChange = { turningOn ->
                                    if (turningOn && warningText != null) {
                                        showRegionWarning = true
                                    } else {
                                        onSetBool(property, turningOn)
                                    }
                                },
                            )
                            if (showRegionWarning && warningText != null) {
                                RegionWarningDialog(
                                    warningText = warningText,
                                    strings = s,
                                    onConfirm = {
                                        showRegionWarning = false
                                        onSetBool(property, true)
                                    },
                                    onDismiss = { showRegionWarning = false },
                                )
                            }
                        }
                        else -> NumericSetter(current = result.value as? Long ?: 0L, setLabel = s.setButton, onSet = { onSetNumeric(property, it) })
                    }
                }
            }

            if (settable && isCycle && result?.ok == true) {
                CycleButtons(
                    propertyName = property.name,
                    lang = lang,
                    current = result.value as? Long,
                    onSelect = { onSetNumeric(property, it) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun RegionWarningDialog(warningText: String, strings: AppStrings, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.regionWarningTitle) },
        text = { Text(warningText + strings.regionWarningConfirmSuffix) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.regionWarningConfirmButton) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.regionWarningCancelButton) } },
    )
}

@Composable
private fun CycleButtons(
    propertyName: String,
    lang: Lang,
    current: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = CYCLE_VALUES[propertyName] ?: return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (value in options) {
            val label = cycleLabel(propertyName, value, lang)
            val selected = current == value
            if (selected) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text(label) }
            } else {
                Button(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun NumericSetter(current: Long, setLabel: String, onSet: (Long) -> Unit) {
    var text by remember(current) { mutableStateOf(current.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.padding(end = 4.dp).weight(1f, fill = false),
            singleLine = true,
        )
        TextButton(onClick = { text.toLongOrNull()?.let(onSet) }) { Text(setLabel) }
    }
}

private fun displayValue(property: SpecProperty, result: SpecReadResult?, lang: Lang, s: AppStrings): String {
    if (result == null) return s.loadingPlaceholder
    if (!result.ok) return s.errorStatusFormat(result.status)
    val v = result.value ?: return s.emptyValuePlaceholder

    if (property.name == "RIDING_TIME") {
        val seconds = when (v) { is Long -> v; is Float -> v.toLong(); else -> null }
        if (seconds != null) return formatDuration(seconds)
    }
    if ((property.name == "PRODUCTION_DATE" || property.name == "ACTIVATION_DATE") && v is String) {
        return formatDateString(v)
    }
    if (hasEnumLabels(property.name)) {
        val key = v as? Long
        val label = key?.let { enumLabel(property.name, it, lang) }
        return label ?: s.unknownValueFormat(v.toString())
    }
    UNIT_SCALES[property.name]?.let { scale ->
        val num = when (v) {
            is Long -> v * scale
            is Float -> v * scale
            else -> null
        }
        if (num != null) {
            val text = if (scale == 1.0) {
                num.toLong().toString()
            } else {
                // Decimal separator follows the chosen app language, not the device locale -
                // otherwise an English UI could still show "53,84" with a German-style comma.
                val locale = if (lang == Lang.DE) java.util.Locale.GERMANY else java.util.Locale.US
                val sep = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
                "%.2f".format(locale, num).trimEnd('0').trimEnd(sep)
            }
            val unit = unitSuffix(property.name, lang)
            return if (unit != null) "$text $unit" else text
        }
    }
    return when (v) {
        is Boolean -> if (v) s.boolOn else s.boolOff
        is Long -> if (property.type == SpecType.BOOL) (if (v == 1L) s.boolOn else s.boolOff) else v.toString()
        else -> v.toString()
    }
}

private fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds < 0) return totalSeconds.toString()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "%dh %02dm %02ds".format(h, m, s)
        m > 0 -> "%dm %02ds".format(m, s)
        else -> "${s}s"
    }
}
