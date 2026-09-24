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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scooterre.client.protocol.RangeEstimate
import com.scooterre.client.protocol.RideBookEntry
import com.scooterre.client.protocol.RideTrip
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

/** The scooter tabs as horizontally swipeable pages - an alternative to the side menu. App settings
 * are deliberately not a page (no swiping there). [selected] stays the single source of truth,
 * shared with the menu and the back gesture: a change from outside scrolls the pager, a swipe that
 * settles on a page reports it through [onSelected]. The pager state is created per entry, so
 * coming back from the app settings starts on the right page instead of flashing another first. */
@Composable
private fun SectionPager(
    selected: DashboardSection,
    onSelected: (DashboardSection) -> Unit,
    modifier: Modifier,
    content: @Composable (DashboardSection) -> Unit,
) {
    val pages = remember { DashboardSection.entries.filter { it != DashboardSection.APP_SETTINGS } }
    val pagerState = rememberPagerState(initialPage = pages.indexOf(selected).coerceAtLeast(0)) { pages.size }
    val currentOnSelected by rememberUpdatedState(onSelected)

    LaunchedEffect(selected) {
        val target = pages.indexOf(selected)
        if (target >= 0 && target != pagerState.currentPage) {
            // A neighbouring page animates; a far jump from the menu snaps instead of scrolling
            // through every tab in between.
            if (kotlin.math.abs(target - pagerState.currentPage) == 1) pagerState.animateScrollToPage(target)
            else pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnSelected(pages[it]) }
    }

    HorizontalPager(state = pagerState, modifier = modifier, verticalAlignment = Alignment.Top) { index ->
        Column(modifier = Modifier.fillMaxWidth()) { content(pages[index]) }
    }
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

/** The whole ride book as plain text, newest first - see [RideBook]. */
private fun buildRideLogExportText(state: UiState, s: AppStrings): String {
    val locale = if (state.language == Lang.DE) java.util.Locale.GERMANY else java.util.Locale.US
    val units = state.units
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    val header = buildString {
        append(state.deviceName ?: modelDisplayName(state.activeModel, state.language))
        append(" (").append(state.macAddress).append(")\n")
        append(stamp.format(java.util.Date()))
        append("\n\n")
    }
    val lines = orderedRideBook(state.rideBook).map { e ->
        val r = e.record
        val whenText = if (e.savedMs > 0) stamp.format(java.util.Date(e.savedMs)) else s.rideBookUndated
        "%s: %s, %.1f %s, %s %.1f %s".format(
            locale, whenText, formatRideDuration((r.minutes * 60_000).toLong()), units.distance(r.km), units.distanceUnit,
            s.rideBookAvgLabel, units.speed(r.avgKmh), units.speedUnit,
        )
    }
    return header + lines.joinToString("\n")
}

/** Dated rides newest first, then the ones from before the ride book existed (no time known). */
private fun orderedRideBook(book: List<RideBookEntry>): List<RideBookEntry> =
    book.filter { it.savedMs > 0 }.reversed().sortedByDescending { it.savedMs } + book.filter { it.savedMs <= 0 }

@Composable
fun DashboardScreen(
    state: UiState,
    onRefreshAll: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
    onSetString: (SpecProperty, String) -> Unit,
    onResetHistory: () -> Unit,
    onDismissRideBookNote: () -> Unit,
    settings: SettingsActions,
) {
    val s = strings(state.language)
    val profile = state.activeSpecProfile
    val propertiesByName = profile.all.associateBy { it.name }
    val context = LocalContext.current
    // Landscape is used one-handed while riding, mounted on the handlebar - the overview must fit
    // without scrolling there (see OverviewContent), and the header gives back the height that
    // costs by dropping its second line.
    val isLandscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
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
        // Swiping right anywhere on the content would otherwise open the menu and fight with the
        // tab pager's "previous tab" swipe - the menu opens via the hamburger button only, but an
        // open menu can still be swiped shut.
        gesturesEnabled = drawerState.isOpen,
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
                modifier = Modifier.fillMaxWidth().padding(top = if (isLandscape) 6.dp else 16.dp, bottom = 4.dp),
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
                        // The MAC address is a second line that only fits in portrait's spare
                        // height - landscape needs every dp for the values themselves instead.
                        if (!isLandscape) {
                            Text(state.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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

            // Same drop as the MAC line above: the section title only earns its keep where height
            // is not the scarce resource - the drawer itself already shows the current section.
            if (!isLandscape || selectedSection != DashboardSection.OVERVIEW) {
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
            }

            // One scooter tab's content - rendered as a page of the swipe pager below.
            val renderSection: @Composable (DashboardSection) -> Unit = { section ->
                if (section == DashboardSection.HISTORY) {
                    HistoryTabContent(state, s, onResetHistory, isLandscape)
                } else if (section == DashboardSection.OVERVIEW) {
                    OverviewContent(state, s, profile, propertiesByName, guardedSetBool, onSetNumeric, onSetString, isLandscape)
                } else if (section == DashboardSection.RIDE_LOG) {
                    RideBookContent(
                        state, s, isLandscape, onDismissRideBookNote,
                        onExport = {
                            pendingRideLogExport = buildRideLogExportText(state, s)
                            rideLogExportLauncher.launch("fahrtenbuch.txt")
                        },
                    )
                } else {
                    val activeNames = namesFor(section, profile).orEmpty()
                    val activeProperties = activeNames.mapNotNull { propertiesByName[it] }
                    // Landscape gets two columns instead of one, halving how many rows tall the tab
                    // is - the same trick as the Overview split, so a long tab (Battery/Settings, 13
                    // properties) needs far less scrolling, ideally none, without shrinking any row.
                    val columns = if (isLandscape) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    ) {
                        gridItems(activeProperties, key = { it.name }) { property ->
                            PropertyRow(property, state.values[property.name], state.language, profile, guardedSetBool, onSetNumeric, onSetString)
                        }
                    }
                }
            }

            // App settings stay outside the pager: no swiping there (and none on the device list,
            // which isn't part of this screen at all).
            if (selectedSection == DashboardSection.APP_SETTINGS) {
                AppSettingsContent(state, s, settings, isLandscape)
            } else {
                SectionPager(
                    selected = selectedSection,
                    onSelected = { selectedSection = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    content = renderSection,
                )
            }
        }
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
/** The range at the rider's own consumption in [mode] (see [RangeEstimate]), in the current units,
 * or null while the values or the recorded rides are missing. */
private fun habitRangeKm(state: UiState, mode: Long, units: UnitSystem): Double? {
    if (!state.rideTracking) return null
    val batteryPercent = (state.values["BATTERY_LEVEL"]?.takeIf { it.ok }?.value as? Long)?.toDouble() ?: return null
    val km = RangeEstimate.rangeKm(batteryPercent, state.modeStats[mode]) ?: return null
    return units.distance(km)
}

@Composable
private fun OverviewContent(
    state: UiState,
    s: AppStrings,
    profile: SpecProfile,
    propertiesByName: Map<String, SpecProperty>,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    onSetNumeric: (SpecProperty, Long) -> Unit,
    onSetString: (SpecProperty, String) -> Unit,
    isLandscape: Boolean,
) {
    val lang = state.language
    val units = LocalUnits.current
    val ridingMode = state.values["RIDING_MODE"]?.takeIf { it.ok }?.value as? Long

    // The top half: the two headline numbers, the read-only/mismatch notice and the mode card -
    // shared verbatim between portrait (its own column) and landscape (the column's left half).
    // Splitting it out here, rather than branching inside one giant composable, is what keeps the
    // two arrangements from silently drifting apart as either one gets edited later.
    val primary: @Composable ColumnScope.() -> Unit = {
        RangeAndBatteryRow(state, s, lang, units, ridingMode)
        if (state.activeSpecProfile.readOnly || state.layoutMismatch) {
            Text(
                if (state.layoutMismatch) s.layoutMismatchError else s.readOnlyNotice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        ridingMode?.let { mode -> ModeCard(state, s, profile, propertiesByName, lang, units, mode, onSetNumeric) }
    }
    // The bottom half: recuperation plus the glance tiles - the part that most benefits from
    // landscape's extra width (four tiles per row instead of two), so it moves to its own column
    // there rather than just being stacked under [primary] as it is in portrait.
    val secondary: @Composable ColumnScope.() -> Unit = {
        propertiesByName["ENERGY_RECOVERY"]?.let { property ->
            PropertyRow(property, state.values["ENERGY_RECOVERY"], lang, profile, onSetBool, onSetNumeric, onSetString)
        }
        OverviewTileGrid(state, s, lang, profile, propertiesByName, onSetBool, perRow = if (isLandscape) 4 else 2)
    }

    if (isLandscape) {
        // Mounted on the handlebar in landscape, this screen has to show everything at a glance
        // with no scrolling (user feedback, 2026-09-22) - two side-by-side columns instead of one
        // long one, each as wide as portrait's single column used to be (landscape width is
        // roughly double portrait's), so every card and tap target keeps its portrait size.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = secondary)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            primary()
            secondary()
        }
    }
}

@Composable
private fun RangeAndBatteryRow(state: UiState, s: AppStrings, lang: Lang, units: UnitSystem, ridingMode: Long?) {
    val remainingKm = (state.values["REMAINING_MILEAGE"]?.takeIf { it.ok }?.value as? Float)?.let { units.distance(it * 0.01) }
    val batteryPct = state.values["BATTERY_LEVEL"]?.takeIf { it.ok }?.value as? Long
    val charging = (state.values["IS_CHARGING"]?.takeIf { it.ok }?.value as? Long) == 1L
    val batteryAccent = when {
        charging -> Color(0xFF4CAF50)
        batteryPct != null && batteryPct <= 15L -> MaterialTheme.colorScheme.error
        else -> Color(0xFF7EA6FF)
    }
    // "Eigene Verbrauchsanalyse" doesn't just gate recording (see the app-settings hint) - it also
    // picks which range estimate gets the big, prominent number here. On: your own, with the
    // scooter's own estimate named underneath for comparison, once there is enough data for it -
    // before that (or with the switch off), the scooter's estimate is the only one shown, exactly
    // as before this existed.
    val ownKm = ridingMode?.let { habitRangeKm(state, it, units) }
    val ownLabel = ridingMode?.let { enumLabel("RIDING_MODE", it, lang) }
    val showOwn = state.rideTracking && ownKm != null && ownLabel != null
    // IntrinsicSize.Min + fillMaxHeight: whichever card has more to say (an extra subtitle line
    // when showOwn is true) sets the row's height, and the other card stretches to match instead
    // of sitting shorter next to it - matched height, not matched (or padded) text.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showOwn) {
            BigStatCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                value = "%.0f".format(java.util.Locale.US, ownKm!!),
                unit = units.distanceUnit,
                label = s.rangeOwnCardLabel(ownLabel!!),
                accent = Color(0xFF7EA6FF),
                subtitle = remainingKm?.let { s.rangeScooterSubtitle("%.0f %s".format(java.util.Locale.US, it, units.distanceUnit)) },
            )
        } else {
            BigStatCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                value = remainingKm?.let { "%.0f".format(java.util.Locale.US, it) } ?: "–",
                unit = units.distanceUnit,
                label = propertyName("REMAINING_MILEAGE", lang),
                accent = Color(0xFF7EA6FF),
            )
        }
        BigStatCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            value = batteryPct?.toString() ?: "–",
            unit = "%",
            label = if (charging) (if (lang == Lang.DE) "Lädt gerade" else "Charging now") else propertyName("BATTERY_LEVEL", lang),
            accent = batteryAccent,
        )
    }
}

@Composable
private fun ModeCard(
    state: UiState,
    s: AppStrings,
    profile: SpecProfile,
    propertiesByName: Map<String, SpecProperty>,
    lang: Lang,
    units: UnitSystem,
    mode: Long,
    onSetNumeric: (SpecProperty, Long) -> Unit,
) {
    // The range at this mode's own consumption now lives in the headline stat card above (see
    // RangeAndBatteryRow), as prominent as the scooter's own estimate - not repeated here.
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
            if (!profile.readOnly) {
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
}

/** Everything else worth a glance during a ride, [perRow] tiles at a time - two in portrait, four
 * in landscape (see [OverviewContent]) - so the whole overview fits on one screen without
 * scrolling either way. Each value is still also shown in its normal category tab. */
@Composable
private fun OverviewTileGrid(
    state: UiState,
    s: AppStrings,
    lang: Lang,
    profile: SpecProfile,
    propertiesByName: Map<String, SpecProperty>,
    onSetBool: (SpecProperty, Boolean) -> Unit,
    perRow: Int,
) {
    listOf(
        // IS_RIDING ("Fahrzustand") and FAULT ("Fehler") used to be here too - dropped to keep
        // the overview from being cut off at the bottom once the "Nach deinem Verbrauch" line is
        // showing (confirmed on-device, 2026-09-22): both stay one tap away, on the Fahrt and
        // Fahrzeug tabs, and neither is something you'd read while actually riding anyway.
        "IS_LOCKED", "CURRENT_MILEAGE", "RIDING_TIME",
        "AVERAGE_SPEED", "HIGHEST_SPEED", "BLUETOOTH_CAR_SEARCH",
    ).chunked(perRow).forEach { names ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (name in names) {
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
            } else if (property.name == "IS_LOCKED" && result?.ok == true && !profile.readOnly) {
                Switch(checked = (result.value as? Long) == 1L, onCheckedChange = { onSetBool(property, it) })
            }
        }
    }
}

@Composable
private fun BigStatCard(modifier: Modifier = Modifier, value: String, unit: String, label: String, accent: Color, subtitle: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                // Bigger + bolder than a typical stat card (M3 Expressive's 2025 update leans into
                // larger, heavier numerals for exactly this "glance at the key figure" use case) -
                // sized up once more (displayLarge, more card padding) once there was screen room
                // to spare for it (confirmed on-device, 2026-09-22): these are the two or three
                // numbers this whole screen exists to show at a glance while riding.
                Text(value, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = accent)
                Text(
                    " $unit",
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The estimate NOT currently shown big (see RangeAndBatteryRow: the "Eigene
            // Verbrauchsanalyse" switch decides which one that is) - named explicitly so it's
            // never mistaken for the same figure as the big number above it.
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The rides of one day (or the undated ones), shown together in one card - not added up. */
private data class BookDay(val label: String, val rides: List<RideBookEntry>)

/** The ride book: every ride the scooter has logged since the app first saw it (see [RideBook]). */
@Composable
private fun RideBookContent(state: UiState, s: AppStrings, isLandscape: Boolean, onDismissNote: () -> Unit, onExport: () -> Unit) {
    val locale = if (state.language == Lang.DE) java.util.Locale.GERMANY else java.util.Locale.US
    val units = LocalUnits.current
    // The "n new rides imported" note stays for 5 seconds once this tab is showing; a further import
    // during that time restarts the timer.
    LaunchedEffect(state.rideBookNew) {
        if (state.rideBookNew > 0) {
            kotlinx.coroutines.delay(5_000)
            onDismissNote()
        }
    }
    val book = state.rideBook
    if (book.isEmpty()) {
        Text(s.noRidesYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        return
    }
    val today = java.time.LocalDate.now()
    val zone = java.time.ZoneId.systemDefault()
    val dayFormat = java.text.SimpleDateFormat("EEEE, dd.MM.yyyy", locale)
    val days = remember(book, state.language) {
        val out = mutableListOf<BookDay>()
        for (e in orderedRideBook(book)) {
            val label = if (e.savedMs > 0) {
                when (val day = java.time.Instant.ofEpochMilli(e.savedMs).atZone(zone).toLocalDate()) {
                    today -> s.rideBookToday
                    today.minusDays(1) -> s.rideBookYesterday
                    else -> dayFormat.format(java.util.Date(e.savedMs))
                }
            } else {
                s.rideBookUndated
            }
            if (out.lastOrNull()?.label == label) out[out.lastIndex] = out.last().copy(rides = out.last().rides + e) else out += BookDay(label, listOf(e))
        }
        out
    }
    val longest = book.maxOf { it.record.km }.coerceAtLeast(0.1)
    val totalKm = book.sumOf { it.record.km }
    val columns = if (isLandscape) 2 else 1
    val fullWidth: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan =
        { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.rideBookNew > 0) {
            item(span = fullWidth) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        s.rideBookNewFormat(state.rideBookNew),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        item(span = fullWidth) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookStat(Modifier.weight(1f), book.size.toString(), s.rideBookCountLabel)
                BookStat(Modifier.weight(1f), "%.0f %s".format(locale, units.distance(totalKm), units.distanceUnit), s.rideBookTotalLabel)
                BookStat(Modifier.weight(1f), "%.1f %s".format(locale, units.distance(totalKm / book.size), units.distanceUnit), s.rideBookPerRideLabel)
            }
        }
        days.forEach { day ->
            item { BookDayCard(day, longest, s, locale) }
        }
        item(span = fullWidth) {
            TextButton(onClick = onExport) { Text(s.exportRideLogButton) }
        }
    }
}

@Composable
private fun BookStat(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF7EA6FF), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BookDayCard(day: BookDay, longestKm: Double, s: AppStrings, locale: java.util.Locale) {
    val units = LocalUnits.current
    val timeFormat = java.text.SimpleDateFormat("HH:mm", locale)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(day.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            day.rides.forEach { entry ->
                val r = entry.record
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.1f %s".format(locale, units.distance(r.km), units.distanceUnit),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        if (entry.savedMs > 0) {
                            Text(timeFormat.format(java.util.Date(entry.savedMs)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatRideDuration((r.minutes * 60_000).toLong()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(50))) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((r.km / longestKm).toFloat().coerceIn(0.04f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFF7EA6FF), RoundedCornerShape(50)),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${s.rideBookAvgLabel} %.0f %s".format(locale, units.speed(r.avgKmh), units.speedUnit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryTabContent(state: UiState, s: AppStrings, onResetHistory: () -> Unit, isLandscape: Boolean) {
    var showResetConfirm by remember { mutableStateOf(false) }
    val locale = if (state.language == Lang.DE) java.util.Locale.GERMANY else java.util.Locale.US
    val units = LocalUnits.current
    // A scroll wrapper as the safety net (matches AppSettingsContent) - the landscape split below
    // usually needs none, but this stops any edge case (many modes, a long log) from becoming
    // unreachable outright the way this tab's content used to with no scroll container at all.
    val scrollState = rememberScrollState()

    if (!state.rideTracking || state.modeStats.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (!state.rideTracking) s.consumptionOffText else s.noHistoryYetText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            TextButton(onClick = { showResetConfirm = true }) { Text(s.resetHistoryButton) }
        }
    } else {
        // Highest-distance mode first (already sorted that way by the store), so the mode
        // actually ridden the most is what's most prominent, not an arbitrary fixed order.
        val modeCards: @Composable ColumnScope.() -> Unit = {
            state.modeStats.entries.sortedByDescending { it.value.km }.forEach { (mode, stats) ->
                val modeLabel = enumLabel("RIDING_MODE", mode, state.language) ?: mode.toString()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(modeColor(mode)))
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                s.historyKmDrivenFormat(modeLabel, "%.1f %s".format(locale, units.distance(stats.km), units.distanceUnit)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                s.historyConsumptionFormat("%.1f %%/%s".format(locale, units.ratePerDistance(stats.percentPerKm), units.distanceUnit)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            habitRangeKm(state, mode, units)?.let { habit ->
                                Text(
                                    s.historyRangeFormat("%.0f %s".format(locale, habit, units.distanceUnit)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Text(s.historyRangeHint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val rides: @Composable ColumnScope.() -> Unit = {
            val trips = state.rideLog.trips
            if (trips.isNotEmpty()) {
                RideChart(trips.takeLast(12), s, state.language, locale)
                Text(s.recentRidesTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                val recent = trips.takeLast(20).reversed()
                val worst = recent.maxOf { it.percentPerKm }.coerceAtLeast(0.01)
                recent.forEach { RideCard(it, worst, s, state.language, locale) }
            }
            state.rideLog.untimed?.let { old ->
                Text(
                    s.earlierRidesFormat(
                        "%.1f %s".format(locale, units.distance(old.km), units.distanceUnit),
                        "%.1f %%/%s".format(locale, units.ratePerDistance(old.percentPerKm), units.distanceUnit),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val health: @Composable ColumnScope.() -> Unit = {
            BatteryHealthCard(state, s, locale)
            TextButton(onClick = { showResetConfirm = true }) { Text(s.resetHistoryButton) }
        }
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    modeCards()
                    health()
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = rides)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                modeCards()
                rides()
                health()
            }
        }
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

/** Fixed per riding mode (11 Walk, 2 Drive, 3 Sport), the same in every chart and chip. */
private fun modeColor(mode: Long): Color = when (mode) {
    11L -> Color(0xFF43A047)
    2L -> Color(0xFF1E88E5)
    3L -> Color(0xFFF4511E)
    else -> Color(0xFF8E8E93)
}

private fun formatRideDuration(ms: Long): String {
    val minutes = ((ms + 30_000) / 60_000).toInt().coerceAtLeast(1)
    return if (minutes >= 60) "%d h %02d min".format(minutes / 60, minutes % 60) else "$minutes min"
}

@Composable
private fun ModeChip(mode: Long, lang: Lang) {
    val color = modeColor(mode)
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(enumLabel("RIDING_MODE", mode, lang) ?: mode.toString(), style = MaterialTheme.typography.labelMedium)
    }
}

/** One bar per recent ride, oldest to newest, coloured by the mode ridden most - taller = thirstier. */
@Composable
private fun RideChart(trips: List<RideTrip>, s: AppStrings, lang: Lang, locale: java.util.Locale) {
    val units = LocalUnits.current
    val rates = trips.map { units.ratePerDistance(it.percentPerKm) }
    val top = rates.max().coerceAtLeast(0.01)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.rideChartTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("max %.1f %%/%s".format(locale, top, units.distanceUnit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(84.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                trips.forEachIndexed { i, t ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight((rates[i] / top).toFloat().coerceIn(0.06f, 1f))
                            .background(modeColor(t.mode), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                trips.map { it.mode }.distinct().sorted().forEach { ModeChip(it, lang) }
            }
        }
    }
}

@Composable
private fun RideCard(trip: RideTrip, worstPerKm: Double, s: AppStrings, lang: Lang, locale: java.util.Locale) {
    val units = LocalUnits.current
    val color = modeColor(trip.mode)
    val dateFormat = java.text.SimpleDateFormat("EEE, dd.MM. HH:mm", locale)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(java.util.Date(trip.startMs)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    trip.modeKm.forEach { ModeChip(it.first, lang) }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.1f %s".format(locale, units.distance(trip.km), units.distanceUnit),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.1f %%/%s".format(locale, units.ratePerDistance(trip.percentPerKm), units.distanceUnit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        s.rideBatteryUsedFormat("%.0f %%".format(locale, trip.percentUsed)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(50))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((trip.percentPerKm / worstPerKm).toFloat().coerceIn(0.04f, 1f))
                        .fillMaxHeight()
                        .background(color, RoundedCornerShape(50)),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val speed = trip.avgKmh?.let { " · Ø %.0f %s/h".format(locale, units.distance(it), units.distanceUnit) } ?: ""
            Text(formatRideDuration(trip.movingMs) + speed, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Battery health as a small table - only the days on which health or cycles actually changed. */
@Composable
private fun BatteryHealthCard(state: UiState, s: AppStrings, locale: java.util.Locale) {
    if (state.batteryLog.isEmpty()) return
    val units = LocalUnits.current
    val log = state.batteryLog
    // Older versions wrote one identical row per day - show only where a value changed.
    val changes = log.filterIndexed { i, e -> i == 0 || e.soh != log[i - 1].soh || e.cycles != log[i - 1].cycles }
    val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    val header = MaterialTheme.typography.labelMedium
    val body = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.batteryLogTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth()) {
                LogCell(s.batteryLogDate, 1.25f, false, header, muted)
                LogCell(s.batteryLogHealth, 1.1f, true, header, muted)
                LogCell(s.batteryLogCycles, 0.9f, true, header, muted)
                LogCell(s.batteryLogOdometer, 1.4f, true, header, muted)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            changes.takeLast(8).reversed().forEach { e ->
                val millis = java.time.LocalDate.ofEpochDay(e.epochDay).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                Row(modifier = Modifier.fillMaxWidth()) {
                    LogCell(dateFormat.format(java.util.Date(millis)), 1.25f, false, body)
                    LogCell(e.soh?.let { "$it %" } ?: "–", 1.1f, true, body)
                    LogCell(e.cycles?.toString() ?: "–", 0.9f, true, body)
                    LogCell("%.0f %s".format(locale, units.distance(e.km), units.distanceUnit), 1.4f, true, body)
                }
            }
        }
    }
}

@Composable
private fun RowScope.LogCell(text: String, weight: Float, end: Boolean, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    Text(text, modifier = Modifier.weight(weight), style = style, color = color, textAlign = if (end) TextAlign.End else TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
