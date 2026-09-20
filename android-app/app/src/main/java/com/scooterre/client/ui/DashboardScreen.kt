package com.scooterre.client.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scooterre.client.protocol.PendingRideDelta
import com.scooterre.client.protocol.SpecProfile
import com.scooterre.client.protocol.SpecProperty
import com.scooterre.client.protocol.SpecReadResult
import com.scooterre.client.protocol.SpecType
import com.scooterre.client.viewmodel.RefreshRate
import com.scooterre.client.viewmodel.UiState
import kotlinx.coroutines.launch

/** One entry in the side menu - pictogram + section, replacing the old horizontal scrolling tab
 * row once the tab count grew past what fits/is easy to discover on screen (user feedback after
 * live-testing the Verlauf tab addition: finding a tab 6-7 positions in required repeated
 * scroll-and-dump just to locate it). Neither Overview nor History is a plain property list -
 * Overview has its own curated, custom-rendered layout (see [OverviewContent]), History shows
 * derived data from BatteryHistoryStore - so [namesFor] returns null for both. Properties shown
 * in Overview are also still reachable from their normal category tab - nothing is removed from
 * Ride/Battery/Settings/Vehicle by also curating a copy into Overview. */
private enum class DashboardSection(val emoji: String, val label: (AppStrings) -> String) {
    OVERVIEW("🏠", { it.sectionOverview }),
    RIDE("🛴", { it.tabRide }),
    BATTERY("🔋", { it.tabBattery }),
    SETTINGS("⚙️", { it.tabSettings }),
    VEHICLE("🚨", { it.tabVehicleStatus }),
    IDENTIFICATION("🪪", { it.tabIdentification }),
    RIDE_LOG("📖", { it.tabRideLog }),
    HISTORY("📈", { it.tabHistory }),
    APP_SETTINGS("🎛️", { it.sectionApp }),
}

private fun namesFor(section: DashboardSection, profile: SpecProfile): List<String>? = when (section) {
    DashboardSection.OVERVIEW -> null
    DashboardSection.RIDE -> profile.tabRide
    DashboardSection.BATTERY -> profile.tabBattery
    DashboardSection.SETTINGS -> profile.tabSettings
    DashboardSection.VEHICLE -> profile.tabVehicleStatus
    DashboardSection.IDENTIFICATION -> profile.tabIdentification
    DashboardSection.RIDE_LOG -> profile.tabRideLog
    DashboardSection.HISTORY -> null
    DashboardSection.APP_SETTINGS -> null
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

/** The device only remembers its 5 most recent ride-log slots (LOG_1..LOG_5, siid=6) - a new
 * ride overwrites the oldest one, so anything older is gone for good unless saved externally
 * first. Reuses [formatRideLog], the exact same decoder the Ride-Log tab itself displays, so the
 * exported text always matches what's on screen. */
private fun buildRideLogExportText(state: UiState, s: AppStrings): String {
    val header = buildString {
        append(state.deviceName ?: modelDisplayName(state.activeModel, state.language))
        append(" (").append(state.macAddress).append(")\n")
        append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
        append("\n\n")
    }
    val lines = listOf("LOG_1", "LOG_2", "LOG_3", "LOG_4", "LOG_5").map { name ->
        val raw = state.values[name]?.value as? String
        val text = if (raw != null) formatRideLog(raw, state.language, state.units) else s.loadingPlaceholder
        "${propertyName(name, state.language)}: $text"
    }
    return header + lines.joinToString("\n")
}

@Composable
fun DashboardScreen(
    state: UiState,
    onRefreshAll: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
    onSetString: (SpecProperty, String) -> Unit,
    onAttributeRideMode: (Long) -> Unit,
    onSkipPendingRide: () -> Unit,
    onResetHistory: () -> Unit,
    settings: SettingsActions,
) {
    val s = strings(state.language)
    val profile = state.activeSpecProfile
    val propertiesByName = profile.all.associateBy { it.name }
    val context = LocalContext.current
    var pendingRideLogExport by remember { mutableStateOf<String?>(null) }
    val rideLogExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingRideLogExport
        pendingRideLogExport = null
        if (uri != null && text != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }
    var selectedSection by remember { mutableStateOf(DashboardSection.OVERVIEW) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Back gesture: close the menu first, then return to the overview; ScooterApp's own
    // handler (disconnect to the device list) only takes over from the overview.
    BackHandler(enabled = drawerState.isOpen || selectedSection != DashboardSection.OVERVIEW) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else selectedSection = DashboardSection.OVERVIEW
    }

    // Optional safety net (see settings): lock/unlock changes ask once first.
    var pendingConfirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val guardedSetBool: (SpecProperty, Boolean) -> Unit = { property, value ->
        if (state.confirmCritical && property.name == "IS_LOCKED") {
            pendingConfirm = propertyName(property.name, state.language) to { onSetBool(property, value) }
        } else {
            onSetBool(property, value)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    state.deviceName ?: modelDisplayName(state.activeModel, state.language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()
                DashboardSection.entries.forEach { section ->
                    NavigationDrawerItem(
                        icon = { Text(section.emoji, fontSize = 20.sp) },
                        label = { Text(section.label(s)) },
                        selected = selectedSection == section,
                        onClick = { selectedSection = section; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Text("\uD83D\uDCCB", fontSize = 20.sp) },
                    label = { Text(s.deviceListMenu) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onDisconnect() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 48x48dp minimum touch target (Material Design guideline) even though the
                    // glyph itself is small - a shrunk-padding TextButton here measured well
                    // under that.
                    androidx.compose.material3.IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.size(48.dp),
                    ) { Text("☰", fontSize = 22.sp) }
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            state.deviceName ?: modelDisplayName(state.activeModel, state.language),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(state.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp))
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedSection.emoji, fontSize = 18.sp)
                Text(
                    selectedSection.label(s),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider()

            if (selectedSection == DashboardSection.APP_SETTINGS) {
                AppSettingsContent(state, s, settings)
            } else if (selectedSection == DashboardSection.HISTORY) {
                HistoryTabContent(state, s, onResetHistory)
            } else if (selectedSection == DashboardSection.OVERVIEW) {
                OverviewContent(state, s, profile, propertiesByName, guardedSetBool, onSetNumeric, onSetString)
            } else {
                val activeNames = namesFor(selectedSection, profile).orEmpty()
                val activeProperties = activeNames.mapNotNull { propertiesByName[it] }
                if (selectedSection == DashboardSection.RIDE_LOG) {
                    TextButton(
                        onClick = {
                            pendingRideLogExport = buildRideLogExportText(state, s)
                            rideLogExportLauncher.launch("fahrtenbuch.txt")
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(s.exportRideLogButton) }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                ) {
                    items(activeProperties, key = { it.name }) { property ->
                        PropertyRow(property, state.values[property.name], state.language, profile, guardedSetBool, onSetNumeric, onSetString)
                    }
                }
            }
        }
    }

    state.pendingRideDelta?.let { delta ->
        PendingRideDialog(delta, s, state.language, onAttributeRideMode, onSkipPendingRide)
    }

    pendingConfirm?.let { (name, action) ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(s.confirmChangeTitle) },
            text = { Text(s.confirmChangeText(name)) },
            confirmButton = { TextButton(onClick = { pendingConfirm = null; action() }) { Text(s.confirmChangeButton) } },
            dismissButton = { TextButton(onClick = { pendingConfirm = null }) { Text(s.cancelButton) } },
        )
    }
}

/** The app's own take on a "dashboard" - reference screenshots of Xiaomi Home's own scooter
 * screen (shown by the user, 2026-09-20) confirmed there's no live speedometer even there, just
 * large Restreichweite/Akkustand numbers plus a mode badge and quick actions - this deliberately
 * matches that CONCEPT (which figures matter most at a glance) while using an entirely own visual
 * design (colors, layout, typography), not Xiaomi's - see this project's own disclaimer about no
 * affiliation with Xiaomi and the PolyForm Noncommercial license's spirit of an independent,
 * from-scratch client. */
@Composable
private fun OverviewContent(
    state: UiState,
    s: AppStrings,
    profile: SpecProfile,
    propertiesByName: Map<String, SpecProperty>,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
    onSetString: (SpecProperty, String) -> Unit,
) {
    val lang = state.language
    val remainingResult = state.values["REMAINING_MILEAGE"]
    val units = LocalUnits.current
    val remainingKm = (remainingResult?.takeIf { it.ok }?.value as? Float)?.let { units.distance(it * 0.01) }
    val batteryResult = state.values["BATTERY_LEVEL"]
    val batteryPct = batteryResult?.takeIf { it.ok }?.value as? Long
    val charging = (state.values["IS_CHARGING"]?.takeIf { it.ok }?.value as? Long) == 1L
    val ridingMode = state.values["RIDING_MODE"]?.takeIf { it.ok }?.value as? Long

    val batteryAccent = when {
        charging -> Color(0xFF4CAF50)
        batteryPct != null && batteryPct <= 15L -> MaterialTheme.colorScheme.error
        else -> Color(0xFF7EA6FF)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStatCard(
                modifier = Modifier.weight(1f),
                value = remainingKm?.let { "%.0f".format(java.util.Locale.US, it) } ?: "–",
                unit = units.distanceUnit,
                label = propertyName("REMAINING_MILEAGE", lang),
                accent = Color(0xFF7EA6FF),
            )
            BigStatCard(
                modifier = Modifier.weight(1f),
                value = batteryPct?.toString() ?: "–",
                unit = "%",
                label = if (charging) (if (lang == Lang.DE) "Lädt gerade" else "Charging now") else propertyName("BATTERY_LEVEL", lang),
                accent = batteryAccent,
            )
        }

        ridingMode?.let { mode ->
            val modeLabel = enumLabel("RIDING_MODE", mode, lang) ?: mode.toString()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).background(Color(0xFF7EA6FF), shape = CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(modeLabel.take(1), color = Color(0xFF0E1220), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(modeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                propertyName("RIDING_MODE", lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Switching the mode right from the overview - reuses the exact same
                    // CycleButtons control the Settings tab uses (same fixed Walk/Drive/Sport
                    // values, same onSetNumeric path), not a second implementation of mode
                    // switching living here.
                    CycleButtons(
                        propertyName = "RIDING_MODE",
                        lang = lang,
                        current = mode,
                        onSelect = { onSetNumeric(propertiesByName.getValue("RIDING_MODE"), it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }

        // Recuperation sits right under the mode card: another quick switch for use while riding.
        propertiesByName["ENERGY_RECOVERY"]?.let { property ->
            PropertyRow(property, state.values["ENERGY_RECOVERY"], lang, profile, onSetBool, onSetNumeric, onSetString)
        }

        // Everything else worth a glance during a ride, two per row so the whole overview fits on
        // one screen without scrolling. Each value is still also shown in its normal category tab.
        listOf(
            "IS_RIDING" to "IS_LOCKED",
            "CURRENT_MILEAGE" to "RIDING_TIME",
            "AVERAGE_SPEED" to "HIGHEST_SPEED",
            "BLUETOOTH_CAR_SEARCH" to "FAULT",
        ).forEach { (left, right) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (name in listOf(left, right)) {
                    val property = propertiesByName[name]
                    if (property != null) {
                        OverviewTile(property, state.values[name], lang, s, profile, onSetBool, Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun overviewLabel(name: String, lang: Lang): String {
    val de = lang == Lang.DE
    return when (name) {
        "IS_RIDING" -> if (de) "Fahrzustand" else "State"
        "IS_LOCKED" -> if (de) "Gesperrt" else "Locked"
        "CURRENT_MILEAGE" -> if (de) "Fahrstrecke" else "Trip"
        "RIDING_TIME" -> if (de) "Fahrzeit" else "Ride time"
        "AVERAGE_SPEED" -> if (de) "Ø Tempo" else "Avg speed"
        "HIGHEST_SPEED" -> if (de) "Max. Tempo" else "Top speed"
        "BLUETOOTH_CAR_SEARCH" -> if (de) "Suche" else "Find"
        "FAULT" -> if (de) "Fehler" else "Fault"
        else -> propertyName(name, lang)
    }
}

/** Compact half-width version of [PropertyRow] for the overview - label, value and (for the lock
 * and the find-my-scooter trigger) one control on the right. Only the few properties the overview
 * shows need handling here; everything else stays in the full-width rows of the category tabs. */
@Composable
private fun OverviewTile(
    property: SpecProperty,
    result: SpecReadResult?,
    lang: Lang,
    s: AppStrings,
    profile: SpecProfile,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val writeOnly = property.name in profile.writeOnly
    val isError = !writeOnly && result != null && !result.ok
    Card(
        modifier = modifier.heightIn(min = 68.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    overviewLabel(property.name, lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!writeOnly) {
                    Text(
                        displayValue(property, result, lang, s, LocalUnits.current),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (writeOnly) {
                Button(onClick = { onSetBool(property, true) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(s.triggerButton)
                }
            } else if (property.name == "IS_LOCKED" && result?.ok == true) {
                Switch(checked = (result.value as? Long) == 1L, onCheckedChange = { onSetBool(property, it) })
            }
        }
    }
}

@Composable
private fun BigStatCard(modifier: Modifier = Modifier, value: String, unit: String, label: String, accent: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                // Bigger + bolder than a typical stat card (M3 Expressive's 2025 update leans into
                // larger, heavier numerals for exactly this "glance at the key figure" use case).
                Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = accent)
                Text(
                    " $unit",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    modifier = Modifier.padding(bottom = 5.dp, start = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryTabContent(state: UiState, s: AppStrings, onResetHistory: () -> Unit) {
    var showResetConfirm by remember { mutableStateOf(false) }
    val locale = if (state.language == Lang.DE) java.util.Locale.GERMANY else java.util.Locale.US
    val units = LocalUnits.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.efficiencyTotals.isEmpty()) {
            Text(
                s.noHistoryYetText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            // Highest-distance mode first (already sorted that way by the store), so the mode
            // actually ridden the most is what's most prominent, not an arbitrary fixed order.
            state.efficiencyTotals.entries.sortedByDescending { it.value.totalKm }.forEach { (mode, totals) ->
                val modeLabel = enumLabel("RIDING_MODE", mode, state.language) ?: mode.toString()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            s.historyKmDrivenFormat(modeLabel, "%.1f %s".format(locale, units.distance(totals.totalKm), units.distanceUnit)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            s.historyWhPerKmFormat("%.1f Wh/%s".format(locale, units.energyPerDistance(totals.whPerKm), units.distanceUnit)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        TextButton(onClick = { showResetConfirm = true }) { Text(s.resetHistoryButton) }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(s.resetHistoryConfirmTitle) },
            text = { Text(s.resetHistoryConfirmText) },
            confirmButton = {
                TextButton(onClick = { onResetHistory(); showResetConfirm = false }) { Text(s.resetHistoryButton) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text(s.cancelButton) } },
        )
    }
}

/** Shown right after connecting when the odometer moved since the last time this device was seen
 * - see [com.scooterre.client.protocol.BatteryHistoryStore.checkForPendingRide] for why the app
 * has to ask instead of just knowing. Mode labels reuse [enumLabel]'s existing RIDING_MODE names
 * (language-invariant, like a manufacturer preset name - same convention as the Settings tab's
 * cycle buttons), so this doesn't invent a second, inconsistent set of mode names. */
@Composable
private fun PendingRideDialog(
    delta: PendingRideDelta,
    s: AppStrings,
    lang: Lang,
    onAttributeRideMode: (Long) -> Unit,
    onSkipPendingRide: () -> Unit,
) {
    val units = LocalUnits.current
    val kmText = "%.1f %s".format(java.util.Locale.US, units.distance(delta.km), units.distanceUnit)
    val whText = "%.0f".format(java.util.Locale.US, delta.wh)
    val body = delta.sinceMillis?.let {
        val since = java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
        s.pendingRideBody(kmText, whText, since)
    } ?: s.pendingRideBodyNoSince(kmText, whText)
    AlertDialog(
        onDismissRequest = onSkipPendingRide,
        title = { Text(s.pendingRideTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mode in listOf(11L, 2L, 3L)) {
                        Button(onClick = { onAttributeRideMode(mode) }) {
                            Text(enumLabel("RIDING_MODE", mode, lang) ?: mode.toString())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSkipPendingRide) { Text(s.pendingRideSkipButton) } },
    )
}

@Composable
private fun PropertyRow(
    property: SpecProperty,
    result: SpecReadResult?,
    lang: Lang,
    profile: SpecProfile,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
    onSetString: (SpecProperty, String) -> Unit,
) {
    val s = strings(lang)
    val settable = property.name in profile.settable
    val isCycle = property.name in profile.cycleProperties
    val isError = result != null && !result.ok

    // Write-only properties (see SpecProfile.writeOnly) never have a value to show - GET always
    // fails for them - so they get their own simple "trigger" row instead of the usual
    // label+value+switch layout, which would otherwise show a permanent, misleading error.
    if (property.name in profile.writeOnly) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    propertyName(property.name, lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Button(onClick = { onSetBool(property, true) }) { Text(s.triggerButton) }
            }
        }
        return
    }

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
                        displayValue(property, result, lang, s, LocalUnits.current),
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
                        // TIRE_MAINTENANCE is read as "[state 1][interval 3][remaining-days 3]" but
                        // WRITTEN as the 4-char "[state 1][interval 3]" - the same format Xiaomi
                        // Home writes, confirmed live 2026-09-20 (state flips, interval changes and
                        // resets the remaining days to it). State '2' = off, '0' = on.
                        SpecType.STRING -> if (property.name == "TIRE_MAINTENANCE") {
                            val raw = result.value as? String
                            if (raw != null && raw.length >= 7 && raw.all { it.isDigit() }) {
                                Switch(
                                    checked = raw[0] != '2',
                                    onCheckedChange = { turningOn ->
                                        onSetString(property, (if (turningOn) "0" else "2") + raw.substring(1, 4))
                                    },
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

            if (settable && property.name == "TIRE_MAINTENANCE" && result?.ok == true) {
                val raw = result.value as? String
                if (raw != null && raw.length >= 7 && raw.all { it.isDigit() } && raw[0] != '2') {
                    Text(
                        s.tireIntervalLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    IntervalButtons(
                        options = TIRE_INTERVAL_DAYS,
                        current = raw.substring(1, 4).toInt(),
                        onSelect = { days -> onSetString(property, "0" + "%03d".format(days)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
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

private val TIRE_INTERVAL_DAYS = listOf(14, 30, 60, 90, 180)

@Composable
private fun IntervalButtons(options: List<Int>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Five buttons share one row, so drop the default 24dp horizontal padding or the digits wrap.
        val tight = PaddingValues(horizontal = 4.dp)
        for (days in options) {
            if (days == current) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f), contentPadding = tight) {
                    Text(days.toString(), maxLines = 1, softWrap = false)
                }
            } else {
                Button(
                    onClick = { onSelect(days) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    contentPadding = tight,
                ) { Text(days.toString(), maxLines = 1, softWrap = false) }
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

private fun convertUnits(name: String, value: Double, units: UnitSystem): Pair<Double, String?> = when (name) {
    "TOTAL_MILEAGE", "CURRENT_MILEAGE", "REMAINING_MILEAGE" -> units.distance(value) to units.distanceUnit
    "AVERAGE_SPEED", "HIGHEST_SPEED" -> units.speed(value) to units.speedUnit
    "BATTERY_TEMPERATURE", "SCOOTER_TEMPERATURE" -> units.temperature(value) to units.temperatureUnit
    else -> value to null
}

private fun displayValue(property: SpecProperty, result: SpecReadResult?, lang: Lang, s: AppStrings, units: UnitSystem): String {
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
    if (property.name == "TIRE_MAINTENANCE" && v is String) return formatTireMaintenance(v, lang)
    if (property.name == "MORE_BATTERY_INFO" && v is String) return formatMoreBatteryInfo(v, lang)
    if (property.name == "MORE_BATTERY_INFO_2" && v is String) return formatMoreBatteryInfo2(v, lang)
    if (property.siid == 6 && v is String) return formatRideLog(v, lang, units)
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
            val (shown, unitOverride) = convertUnits(property.name, num.toDouble(), units)
            val text = if (scale == 1.0) {
                Math.round(shown).toString()
            } else {
                // Decimal separator follows the chosen app language, not the device locale -
                // otherwise an English UI could still show "53,84" with a German-style comma.
                val locale = if (lang == Lang.DE) java.util.Locale.GERMANY else java.util.Locale.US
                val sep = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
                "%.2f".format(locale, shown).trimEnd('0').trimEnd(sep)
            }
            val unit = unitOverride ?: unitSuffix(property.name, lang)
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
