@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.occaecat.hush.devices.BlobRole
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceState
import com.occaecat.hush.devices.Feature
import com.occaecat.hush.devices.SettingsGroup
import com.occaecat.hush.devices.Source
import com.occaecat.hush.devices.Tab

/**
 * The one UI every device gets: Listen, Sound and Device built from what [c] reports and can do, with its own
 * sections slotted in. A device that can't do something simply has nothing there.
 */
@Composable
fun DeviceUi(c: DeviceController, key: String, tab: androidx.compose.runtime.MutableState<com.occaecat.hush.ui.Tab>? = null) {
    val s by c.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(c) {
        com.occaecat.hush.features.SleepTimer.target = c.sleepTarget ?: com.occaecat.hush.features.PhoneSleepTarget(context, s.name)
    }
    val open = c.openPage?.collectAsState()?.value
    // A notification asking for updates: the Device tab underneath, the Updates page on top.
    val nav = LocalNavigator.current
    LaunchedEffect(open) {
        if (open == "updates" || open == "firmware") {
            nav.push(Route.Updates(key))
            c.consumeOpenPage()
        }
    }
    c.Gate {
        val settings = c.settings()
        DeviceShell(
            listen = { ListenPage(c, s, settings.filter { it.tab == Tab.LISTEN }) },
            sound = { SoundPage(c, s, key, settings.filter { it.tab == Tab.SOUND }) },
            device = { DevicePage(c, s, key, settings.filter { it.tab == Tab.DEVICE }) },
            openDevice = open,
            statusPane = c.statusPane,
            tabState = tab,
        )
        c.Overlays()
    }
}

// --- Listen --------------------------------------------------------------------------------------------------------

@Composable
private fun ListenPage(c: DeviceController, s: DeviceState, settings: List<SettingsGroup>) {
    val key = com.occaecat.hush.devices.LocalDeviceHost.current?.current?.key
    val showHero = LocalLayout.current.tabletopTop == null
    val colors = MaterialTheme.colorScheme
    TabScaffold(
        title = s.name,
        subtitle = s.subtitle,
        pinnedHero = if (showHero) ({ collapse ->
            ProductHero(
                name = s.name,
                collapse = collapse,
                connected = s.connected,
                busy = s.connection == Connection.CONNECTING,
                blobShape = s.blobShape ?: MaterialShapes.Cookie9Sided,
                blobColor = when {
                    !s.connected -> colors.surfaceContainerHighest
                    s.blobRole == BlobRole.TERTIARY -> colors.tertiaryContainer
                    s.blobRole == BlobRole.SECONDARY -> colors.secondaryContainer
                    else -> colors.primaryContainer
                },
                onTap = if (s.connection == Connection.CONNECTED) c::onHeroTap else null,
                tapLabel = "Refresh",
                image = { m -> c.Render(m, s.connected) },
                status = {
                    val label = s.statusLabel
                    when {
                        s.connected && s.batteries.isNotEmpty() -> BatteryStatus(s.batteries) { c.partImage(it) }
                        s.connected && label != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(label.first, null, Modifier.size(18.dp), tint = colors.primary)
                            Spacer(Modifier.width(Space.xs))
                            Text(label.second, style = MaterialTheme.typography.titleMedium)
                        }
                        s.connected -> {}
                        else -> Text(s.statusText ?: "Not connected", style = MaterialTheme.typography.headlineSmallEmphasized)
                    }
                },
                shrinkStatus = !s.connected,
                chips = if (s.connected && s.heroChips.isNotEmpty()) ({
                    s.heroChips.forEach { chip ->
                        chip.logo?.let { LogoPill(it, chip.label, dimmed = chip.dimmed, tight = chip.tight) } ?: Pill(chip.label)
                    }
                }) else null,
            )
        }) else null,
    ) {
        s.banner?.let { wideItem { SettlingBanner(it, ItemWidth) } }
        // Disconnected, the hero already says so; below it, what to do (or all of it, when the hero is hidden).
        if (!s.connected) wideItem { if (showHero) DisconnectedHelp(s, ItemWidth) else NotConnected(c, s, ItemWidth) }
        if (s.connected) c.noise?.let { noise ->
            item {
                val ui by noise.state.collectAsState()
                ui?.let { NoiseControlCard(noise, it, ItemWidth) }
            }
        }
        if (s.connected && key != null && (c.noise != null || c.equalizer != null)) item { ScenesRow(key, c, ItemWidth) }
        c.listenSections(this)
        if (s.connected) settings.forEach { g -> item { SettingsGroupView(g, ItemWidth) } }
        if (s.connected) item {
            NowPlayingCard(
                ItemWidth,
                source = s.source ?: (Icons.Outlined.Smartphone to "This phone"),
                fallback = s.track,
                badge = { SleepTimerChip() },
                volume = s.volume?.let { v -> { VolumeRow(v, s.volumeSteps, c::setVolume) } },
            )
        }
    }
}

// --- Sound ---------------------------------------------------------------------------------------------------------

@Composable
private fun SoundPage(c: DeviceController, s: DeviceState, key: String, settings: List<SettingsGroup>) {
    val nav = LocalNavigator.current
    val eq = c.equalizer
    val eqState = eq?.state?.collectAsState()?.value
    var autoEqOpen by rememberSaveable { mutableStateOf(false) }
    TabScaffold(
        title = "Sound",
        subtitle = s.subtitle,
        // Same frame as Listen: the equalizer is the page's hero, so there is no app bar while it is shown.
        hero = if (s.connected && eq != null && eqState != null) ({
            EqualizerHero(
                eq = eqState,
                onBand = eq::setBand,
                onReset = eq::reset,
                onCompare = eq::compare,
                actions = { eq.toggle?.let { t -> Switch(eqState.enabled, t, Modifier.padding(start = Space.s)) } },
                extra = {
                    if (eq.providedBy == Source.PHONE) PhoneSourceNote(Feature.EQUALIZER, Modifier.padding(horizontal = Space.l, vertical = Space.xs))
                    eq.HeroExtra()
                },
            )
        }) else null,
    ) {
        s.banner?.let { wideItem { SettlingBanner(it, ItemWidth) } }
        if (!s.connected) {
            wideItem { NotConnected(c, s, ItemWidth) }
            return@TabScaffold
        }
        if (eq != null) {
            item {
                val groups by eq.presets.collectAsState()
                val library by com.occaecat.hush.devices.EqLibrary.items.collectAsState()
                val enabled = eqState?.enabled != false
                // The phone's library curves this equalizer can play, after the device's own presets.
                val saved = library.filter { it.layout == eq.layout }
                Column(ItemWidth) {
                    groups.forEachIndexed { i, g ->
                        SectionHeader(g.title, top = if (i == 0) Space.s else Space.l)
                        PresetChips(g.presets.map { p -> Triple(p.name, p.active) { eq.selectPreset(p.id) } }, enabled = enabled)
                    }
                    if (saved.isNotEmpty() && eqState?.editable != false) {
                        SectionHeader("Library")
                        PresetChips(saved.map { c -> Triple(c.name, eqState?.levels == c.levels) { eq.applyLevels(c.levels) } }, enabled = enabled)
                    }
                }
            }
            if (eqState?.editable != false) item {
                val applied by eq.autoEqApplied.collectAsState()
                val library by com.occaecat.hush.devices.EqLibrary.items.collectAsState()
                val n = library.count { it.layout == eq.layout }
                Column(ItemWidth) {
                    SectionHeader("Personalize")
                    SegmentedGroup {
                        ValueRow(0, 2, Icons.Outlined.AutoGraph, "AutoEq",
                            applied?.let { "AutoEq · $it" },
                            enabled = eqState?.enabled != false) { autoEqOpen = true }
                        ValueRow(1, 2, Icons.Outlined.LibraryMusic, "EQ library",
                            when (n) { 0 -> null; 1 -> "1 saved curve"; else -> "$n saved curves" },
                            modifier = Modifier.sharedPage(Route.EqLibrary(key).pageKey!!)) { nav.push(Route.EqLibrary(key)) }
                    }
                }
            }
        }
        if (c.noise != null || c.equalizer != null) item { AutomationSection(key, c, ItemWidth) { nav.push(Route.Automation(key, it)) } }
        settings.forEach { g -> item { SettingsGroupView(g, ItemWidth) } }
        c.soundSections(this)
    }
    if (autoEqOpen && eq != null) AutoEqSheet(eq.autoEq.exactName ?: s.model, eq.autoEq) { autoEqOpen = false }
}

// --- Device --------------------------------------------------------------------------------------------------------

@Composable
private fun DevicePage(c: DeviceController, s: DeviceState, key: String, settings: List<SettingsGroup>) {
    val nav = LocalNavigator.current
    val app = rememberAppUpdate()
    val fw = c.firmware
    val fwPending = fw?.pending()
    val fwChecking = fw?.checking() == true
    TabScaffold(title = "Device", subtitle = null) {
        s.banner?.let { wideItem { SettlingBanner(it, ItemWidth) } }
        item { DeviceCard(c, s, key, ItemWidth.padding(top = Space.s)) }
        if (s.connected) settings.forEach { g -> item { SettingsGroupView(g, ItemWidth) } }
        c.deviceSections(this)
        if (s.connected && c.microphone != null) item { MicTestRow(ItemWidth) { nav.push(Route.MicTest(key)) } }
        c.insights?.let { profile ->
            item {
                InsightsSection(profile, live(s), ItemWidth) { nav.push(Route.Insight(key, it)) }
            }
        }
        item {
            // The app's own pages are destinations (rows grow into them); a device's own rows open what it draws.
            val rows = buildList {
                add(com.occaecat.hush.devices.AboutRow(Icons.Outlined.SystemUpdate, "Updates", updatesSummary(app, fwPending, fwChecking)) {
                    nav.push(Route.Updates(key))
                } to Route.Updates(key).pageKey)
                addAll(c.aboutRows.map { it to null })
                add(com.occaecat.hush.devices.AboutRow(Icons.Outlined.Tune, "Features", null) {
                    nav.push(Route.Features(key))
                } to Route.Features(key).pageKey)
                add(com.occaecat.hush.devices.AboutRow(Icons.Outlined.Favorite, "About Hush", null) {
                    nav.push(Route.About(key))
                } to Route.About(key).pageKey)
            }
            Column(ItemWidth.padding(bottom = Space.l)) {
                SectionHeader("About", top = Space.s)
                SegmentedGroup {
                    rows.forEachIndexed { i, (r, page) ->
                        ValueRow(i, rows.size, r.icon, r.title, r.value,
                            modifier = if (page != null) Modifier.sharedPage(page) else Modifier, onClick = r.onClick)
                    }
                }
            }
        }
    }
}

/** Who the device is, at a glance: render, model, short facts, and the device's own actions. */
@Composable
private fun DeviceCard(c: DeviceController, s: DeviceState, key: String, modifier: Modifier) {
    var showDetails by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    if (showDetails) DeviceDetailsSheet(s.details) { showDetails = false }
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh) {
        Column(Modifier.padding(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                c.Render(Modifier.size(88.dp), s.connected)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    // Device is about the hardware: the model, not the phone's nickname for it.
                    val title = @Composable {
                        Text(s.model ?: s.name, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (s.details.isNotEmpty()) Row(
                        Modifier.clip(MaterialTheme.shapes.small).clickable { showDetails = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        title()
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Device details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Row { title() }
                    if (s.connected && s.batteries.isNotEmpty()) BatteryStatus(s.batteries) { c.partImage(it) }
                    val chips = if (s.connected) s.heroChips.take(1) else emptyList()
                    val facts = s.cardFacts.filter { f -> chips.none { it.label == f } }
                    if (chips.isNotEmpty() || facts.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        chips.forEach { chip ->
                            chip.logo?.let { LogoPill(it, chip.label, dimmed = chip.dimmed, tight = chip.tight) } ?: Pill(chip.label)
                        }
                        facts.forEach { Fact(it) }
                    }
                }
            }
            val host = com.occaecat.hush.devices.LocalDeviceHost.current
            var removing by androidx.compose.runtime.remember { mutableStateOf(false) }
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                with(c) { CardActions() }
                // Icon only: three labelled buttons don't fit a phone ("Disconne…"), and Remove asks first anyway.
                if (host?.current != null) androidx.compose.material3.OutlinedIconButton(onClick = { removing = true }) {
                    Icon(Icons.Outlined.DeleteOutline, "Remove")
                }
            }
            val device = host?.current
            if (removing && device != null) ConfirmDialog(
                if (device.transport == com.occaecat.hush.devices.Transport.BLUETOOTH)
                    "${device.name} leaves the device switcher and Hush stops connecting to it. It stays paired with the " +
                        "phone; add it again any time."
                else "${device.name} leaves the device switcher, with its equalizer settings kept. Add it again any time.",
                onConfirm = { host.remove(device) }, onDismiss = { removing = false },
                title = "Remove ${device.name}?", confirm = "Remove",
            )
        }
    }
}

@Composable
private fun Fact(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Space.s, vertical = 2.dp))
    }
}

/** While something settles (a codec change, a reconnect). */
@Composable
fun SettlingBanner(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(Space.m))
            Column {
                Text("Reconnecting", style = MaterialTheme.typography.titleMediumEmphasized)
                Text("$text. This takes a few seconds.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Listen while the device isn't connected, under the hero: what to do, or a plain hint when the device has none. */
@Composable
private fun DisconnectedHelp(s: DeviceState, modifier: Modifier) {
    Text(
        s.disconnectedHelp ?: "Connect ${s.name} to this phone and its controls show up here.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = modifier.padding(horizontal = Space.l, vertical = Space.l),
    )
}

/** Sound while the device isn't connected: the render, dimmed, and what to do. */
@Composable
private fun NotConnected(c: DeviceController, s: DeviceState, modifier: Modifier) {
    Column(modifier.padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        c.Render(Modifier.size(112.dp), false)
        Spacer(Modifier.height(Space.m))
        Text(s.statusText ?: "Not connected", style = MaterialTheme.typography.headlineSmallEmphasized)
        s.disconnectedHelp?.let {
            Spacer(Modifier.height(Space.s))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Space.l))
        }
    }
}

internal fun live(s: DeviceState) = com.occaecat.hush.features.Insights.Sample(
    connected = s.connected, battery = s.battery, charging = s.charging, nc = s.noiseCancelling,
    volume = s.volume, volumeSteps = s.volumeSteps.coerceAtLeast(2),
)
