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

private val GROUPS = listOf(
    1 to "Fahrt & Akku",
    2 to "Einstellungen",
    3 to "Akku-Detail",
    4 to "Identifikation",
)

/** Scale factor + unit suffix for numeric properties. Confirmed against the plugin's own
 * UNITS table and verified raw-byte captures from the real device (docs/RESEARCH_LOG.md) -
 * several of these (distances, voltage, speeds) are FLOAT on the wire but actually store
 * value*100 as a whole-number float, e.g. raw 6050 -> 60.5 km. */
private val UNITS: Map<String, Pair<Double, String>> = mapOf(
    "VOLTAGE" to (0.01 to "V"),
    "TOTAL_MILEAGE" to (0.01 to "km"),
    "CURRENT_MILEAGE" to (0.01 to "km"),
    "REMAINING_MILEAGE" to (0.01 to "km"),
    "AVERAGE_SPEED" to (0.01 to "km/h"),
    "HIGHEST_SPEED" to (0.01 to "km/h"),
    // Confirmed against Xiaomi Home's live reading on the real device (0.03 A vs. our
    // undivided 3 A) - these are also stored as value*100 on the wire, like voltage/distance.
    "CURRENT" to (0.01 to "A"),
    "POWER" to (0.01 to "W"),
    "BATTERY_LEVEL" to (1.0 to "%"),
    "SOH" to (1.0 to "%"),
    "REMAINING_BATTERY" to (1.0 to "mAh"),
    "BATTERY_TEMPERATURE" to (1.0 to "°C"),
    "SCOOTER_TEMPERATURE" to (1.0 to "°C"),
    "NUMBER_OF_CYCLES" to (1.0 to "Zyklen"),
)

private val FAULT_LABELS: Map<Long, String> = mapOf(
    0L to "Normal", 10L to "Kommunikationsfehler Display", 11L to "Controller überlastet",
    12L to "Controller-Fehler", 14L to "Fehler Gaskabel", 15L to "Fehler Bremshebel-Kabel",
    18L to "Motorfehler", 21L to "Kommunikationsfehler Akku", 24L to "Überdruck im Akku",
    28L to "Controller-Fehler", 29L to "Controller-Fehler", 39L to "Akkufehler",
    40L to "Controller-Fehler", 45L to "Controller überhitzt", 50L to "Temperaturfehler Akku",
    52L to "Akkufehler",
)

/** Human-readable labels for enum-valued properties. Confirmed against the plugin's own
 * ENUM_LABELS table, not guessed. */
private val ENUM_LABELS: Map<String, Map<Long, String>> = mapOf(
    "RIDING_MODE" to mapOf(11L to "Walk", 2L to "Drive", 3L to "Sport"),
    "ENERGY_RECOVERY" to mapOf(30L to "Schwach", 60L to "Mittel", 90L to "Stark"),
    "ATMOSPHERE_LIGHT" to mapOf(0L to "Aus", 1L to "An", 2L to "Aktiv"),
    "IS_RIDING" to mapOf(0L to "Steht", 1L to "Übergang", 2L to "Fährt"),
    "BATTERY_STATUS" to mapOf(1L to "OK"),
    "MILEAGE_UNIT" to mapOf(1L to "km", 0L to "mi"),
    "FAULT" to FAULT_LABELS,
)

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
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    state.deviceName ?: "Scooter 5 Pro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(state.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            } else {
                Row {
                    TextButton(onClick = onRefreshAll) { Text("Aktualisieren") }
                    TextButton(onClick = onDisconnect) { Text("Trennen") }
                }
            }
        }
        state.error?.let {
            Text(
                "Fehler: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        var selectedTab by remember { mutableStateOf(0) }
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            GROUPS.forEachIndexed { index, (_, groupName) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(groupName) },
                )
            }
        }

        val (activeSiid, _) = GROUPS[selectedTab]
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        ) {
            items(SpecProperties.ALL.filter { it.siid == activeSiid }, key = { it.name }) { property ->
                PropertyRow(property, state.values[property.name], onSetBool, onSetNumeric)
            }
        }
    }
}

@Composable
private fun PropertyRow(
    property: SpecProperty,
    result: SpecReadResult?,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
) {
    val settable = property.name in SpecProperties.SETTABLE
    val cycleOptions = SpecProperties.CYCLE_VALUES[property.name]
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
                        friendlyName(property.name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        displayValue(property, result),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Cycle-button properties (fixed value sets like RIDING_MODE, ENERGY_RECOVERY)
                // need more width than fits next to the label - rendered as their own
                // full-width row below instead (see branch further down).
                if (settable && result?.ok == true && cycleOptions == null) {
                    when (property.type) {
                        SpecType.BOOL -> {
                            val current = (result.value as? Long) == 1L
                            var showRegionWarning by remember { mutableStateOf(false) }
                            val warningText = SpecProperties.REGION_SENSITIVE_WARNINGS[property.name]
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
                                    onConfirm = {
                                        showRegionWarning = false
                                        onSetBool(property, true)
                                    },
                                    onDismiss = { showRegionWarning = false },
                                )
                            }
                        }
                        else -> NumericSetter(current = result.value as? Long ?: 0L, onSet = { onSetNumeric(property, it) })
                    }
                }
            }

            if (settable && cycleOptions != null && result?.ok == true) {
                CycleButtons(
                    options = cycleOptions,
                    current = result.value as? Long,
                    onSelect = { onSetNumeric(property, it) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun RegionWarningDialog(warningText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rechtlicher Hinweis") },
        text = { Text("$warningText Mit dem Aktivieren bestätigst du, dass du die Verantwortung dafür übernimmst.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Ich bestätige, aktivieren") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun CycleButtons(
    options: List<Pair<Long, String>>,
    current: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
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
private fun NumericSetter(current: Long, onSet: (Long) -> Unit) {
    var text by remember(current) { mutableStateOf(current.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.padding(end = 4.dp).weight(1f, fill = false),
            singleLine = true,
        )
        TextButton(onClick = { text.toLongOrNull()?.let(onSet) }) { Text("Setzen") }
    }
}

/** "REMAINING_MILEAGE" -> "Remaining mileage" - readable label without losing the exact
 * MIoT-spec name a technical reader might want (still shown verbatim in the value's status text
 * on error, e.g. "Fehler (status=...)"). */
private fun friendlyName(name: String): String =
    name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private fun displayValue(property: SpecProperty, result: SpecReadResult?): String {
    if (result == null) return "…"
    if (!result.ok) return "Fehler (status=${result.status})"
    val v = result.value ?: return "–"

    if (property.name == "RIDING_TIME") {
        val seconds = when (v) { is Long -> v; is Float -> v.toLong(); else -> null }
        if (seconds != null) return formatDuration(seconds)
    }
    if ((property.name == "PRODUCTION_DATE" || property.name == "ACTIVATION_DATE") && v is String) {
        return formatDateString(v)
    }
    ENUM_LABELS[property.name]?.let { labels ->
        val key = (v as? Long)
        val label = labels[key]
        return if (label != null) label else "Unbekannt ($v)"
    }
    UNITS[property.name]?.let { (scale, unit) ->
        val num = when (v) {
            is Long -> v * scale
            is Float -> v * scale
            else -> null
        }
        if (num != null) {
            val text = if (scale == 1.0) {
                num.toLong().toString()
            } else {
                val sep = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
                "%.2f".format(num).trimEnd('0').trimEnd(sep)
            }
            return "$text $unit"
        }
    }
    return when (v) {
        is Boolean -> if (v) "An" else "Aus"
        is Long -> if (property.type == SpecType.BOOL) (if (v == 1L) "An" else "Aus") else v.toString()
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
