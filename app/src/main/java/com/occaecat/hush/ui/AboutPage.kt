@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.occaecat.hush.sony.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where Hush lives on GitHub; change here if the repository moves. */
object HushLinks {
    const val AUTHOR = "dmitthedazed"
    const val PROFILE = "https://github.com/$AUTHOR"
    const val REPO = "https://github.com/$AUTHOR/hush"
    const val ISSUES = "$REPO/issues"
    const val CONTRIBUTING = "$REPO/blob/main/CONTRIBUTING.md"
    const val CONTRIBUTORS_API = "https://api.github.com/repos/$AUTHOR/hush/contributors"
}

/** An open-source component Hush ships, with its license and where to read it. */
private data class Component(val name: String, val by: String, val license: String, val url: String)

private val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"

private val COMPONENTS = listOf(
    Component("Jetpack Compose & Material 3", "Google · AndroidX", "Apache License 2.0", APACHE),
    Component("Material Symbols (Compose icons)", "Google", "Apache License 2.0", APACHE),
    Component("AndroidX Core, Activity, Lifecycle", "Google · AndroidX", "Apache License 2.0", APACHE),
    Component("Jetpack Glance (widgets)", "Google · AndroidX", "Apache License 2.0", APACHE),
    Component("Wear OS Compose, Tiles & ProtoLayout", "Google · AndroidX", "Apache License 2.0", APACHE),
    Component("Compose for TV", "Google · AndroidX", "Apache License 2.0", APACHE),
    Component("Kotlin standard library", "JetBrains", "Apache License 2.0", APACHE),
    Component("kotlinx.coroutines", "JetBrains", "Apache License 2.0", APACHE),
    Component("Google Play services (location, wearable)", "Google", "Android Software Development Kit License",
        "https://developer.android.com/studio/terms"),
    Component("libxposed API", "LibXposed", "Apache License 2.0", "https://github.com/libxposed/api"),
    Component("AutoEq headphone measurements", "Jaakko Pasanen", "MIT License", "https://github.com/jaakkopasanen/AutoEq/blob/master/LICENSE"),
)

@Composable
fun AboutPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val open = { url: String ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Unit
    }
    var licenses by remember { mutableStateOf(false) }
    FullScreenPage("About Hush", onClose) {
        // ---- identity
        Column(Modifier.fillMaxWidth().padding(vertical = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(112.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_xm4_glyph), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(Space.m))
            Text("Hush", style = MaterialTheme.typography.displaySmallEmphasized)
            Text("Version ${info.versionName} (${info.longVersionCode})", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
        }

        // ---- author
        SectionHeader("Made by")
        val people by produceState<List<Contributor>?>(null) { value = withContext(Dispatchers.IO) { loadContributors(context) } }
        var featuring by remember { mutableStateOf(false) }
        SegmentedGroup {
            AuthorCard(segShapes(index = 0, count = 2).shape) { open(HushLinks.PROFILE) }
            FeaturingRow(people.orEmpty(), segShapes(index = 1, count = 2).shape) { featuring = true }
        }
        if (featuring) FeaturingSheet(people.orEmpty(), open) { featuring = false }

        // ---- project
        SectionHeader("Project")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            ProjectButton(0, Icons.Outlined.Code, "Source code") { open(HushLinks.REPO) }
            ProjectButton(1, Icons.Outlined.BugReport, "Report a problem") { open(HushLinks.ISSUES) }
            ProjectButton(2, Icons.Outlined.Description, "Licenses") { licenses = true }
        }
        if (licenses) LicensesSheet(open) { licenses = false }

        // ---- notices
        SectionHeader("Notices")
        Text(
            // Each brand module brings its own trademarks and sources.
            (com.occaecat.hush.DeviceModules.all.mapNotNull { it.notice } +
                "AutoEq results are computed from measurements by Jaakko Pasanen and contributors (MIT License).")
                .joinToString("\n\n"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s),
        )
        Spacer(Modifier.height(Space.l))
    }
}

/** Someone besides the author who has commits in the repository. */
private data class Contributor(val login: String, val commits: Int, val avatar: ImageBitmap?)

/** A GitHub avatar, downloaded once into the cache. */
private fun cachedAvatar(context: android.content.Context, login: String, url: String): ImageBitmap? = runCatching {
    val f = File(context.cacheDir, "avatar_$login.png")
    if (f.length() == 0L) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 10_000
        try { if (c.responseCode == 200) c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } } } finally { c.disconnect() }
    }
    BitmapFactory.decodeFile(f.path)?.asImageBitmap()
}.getOrNull()

/**
 * The repository's contributors from GitHub's public API, without the author and bots. The list is cached for a day
 * so About doesn't ask GitHub every time it opens; offline, the last list stays.
 */
private fun loadContributors(context: android.content.Context): List<Contributor>? {
    val f = File(context.cacheDir, "contributors.json")
    val fresh = System.currentTimeMillis() - f.lastModified() < 24 * 60 * 60 * 1000L
    if (!fresh || f.length() == 0L) runCatching {
        val c = URL(HushLinks.CONTRIBUTORS_API).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try { if (c.responseCode == 200) f.writeText(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
    }
    if (f.length() == 0L) return null
    return runCatching {
        val a = org.json.JSONArray(f.readText())
        (0 until a.length()).map(a::getJSONObject)
            .filter { it.optString("type") == "User" && !it.getString("login").equals(HushLinks.AUTHOR, ignoreCase = true) }
            .take(30)
            .map { o ->
                val login = o.getString("login")
                Contributor(login, o.optInt("contributions"), cachedAvatar(context, login, o.getString("avatar_url") + "&s=96"))
            }
    }.getOrNull()
}

/** One of the Project buttons, connected like a segmented row: outer corners round, inner ones tight. */
@Composable
private fun RowScope.ProjectButton(index: Int, icon: ImageVector, label: String, onClick: () -> Unit) {
    val outer = 28.dp
    val inner = 8.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) outer else inner, bottomStart = if (index == 0) outer else inner,
        topEnd = if (index == 2) outer else inner, bottomEnd = if (index == 2) outer else inner,
    )
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, interactionSource = press,
        modifier = Modifier.weight(1f).heightIn(min = 88.dp).squishy(press, 0.94f),
    ) {
        Column(
            Modifier.padding(horizontal = Space.xs, vertical = Space.m),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** The open-source components Hush ships, each opening its license. */
@Composable
private fun LicensesSheet(open: (String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.m).padding(bottom = Space.l).navigationBarsPadding(),
        ) {
            Text("Open-source licenses", style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = Space.s))
            Text(
                "Hush is built on ${COMPONENTS.size} open-source components.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.s, top = Space.xs, bottom = Space.m),
            )
            SegmentedGroup {
                COMPONENTS.forEachIndexed { i, c ->
                    SegmentedListItem(
                        onClick = { open(c.url) },
                        shapes = segShapes(index = i, count = COMPONENTS.size),
                        colors = colors,
                        leadingContent = { LeadIcon(Icons.Outlined.Copyright) },
                        supportingContent = { Text("${c.license} · ${c.by}") },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        content = { Text(c.name) },
                    )
                }
            }
        }
    }
}

/** Under the author: "Featuring" and everyone else's avatars, stacked; until there is someone, an invitation. */
@Composable
private fun FeaturingRow(people: List<Contributor>, shape: Shape, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = Space.m, vertical = Space.s).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Featuring", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (people.isEmpty()) {
                Text("you?", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Spacer(Modifier.width(Space.s))
                Box(Modifier.size(32.dp).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = colors.onSecondaryContainer)
                }
            } else AvatarStack(people, ring = colors.surfaceContainerHigh)
        }
    }
}

/** Up to five avatars overlapping like a hand of cards, then the rest as "+N". */
@Composable
private fun AvatarStack(people: List<Contributor>, ring: Color) {
    val colors = MaterialTheme.colorScheme
    val shown = people.take(5)
    val rest = people.size - shown.size
    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
        shown.forEach { p -> Avatar(p, 32.dp, Modifier.border(2.dp, ring, CircleShape)) }
        if (rest > 0) Box(
            Modifier.size(32.dp).border(2.dp, ring, CircleShape).clip(CircleShape).background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("+$rest", style = MaterialTheme.typography.labelSmall, color = colors.onSecondaryContainer) }
    }
}

@Composable
private fun Avatar(p: Contributor, size: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val a = p.avatar
    if (a != null) Image(a, null, modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    else Box(modifier.size(size).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
        Text(p.login.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
    }
}

/** Everyone who built Hush besides the author, each opening their profile, and a way to join them. */
@Composable
private fun FeaturingSheet(people: List<Contributor>, open: (String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.m).padding(bottom = Space.l).navigationBarsPadding(),
        ) {
            Text("Featuring", style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = Space.s))
            Text(
                when (people.size) {
                    0 -> "No one else yet. Fixes, translations and new headphones are welcome."
                    1 -> "1 person helped build Hush."
                    else -> "${people.size} people helped build Hush."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.s, top = Space.xs, bottom = Space.m),
            )
            val count = people.size + 1
            SegmentedGroup {
                people.forEachIndexed { i, p ->
                    SegmentedListItem(
                        onClick = { open("https://github.com/${p.login}") },
                        shapes = segShapes(index = i, count = count),
                        colors = colors,
                        leadingContent = { Avatar(p, 40.dp) },
                        supportingContent = { Text(if (p.commits == 1) "1 commit" else "${p.commits} commits") },
                        content = { Text("@${p.login}") },
                    )
                }
                SegmentedListItem(
                    onClick = { open(HushLinks.CONTRIBUTING) },
                    shapes = segShapes(index = count - 1, count = count),
                    colors = colors,
                    leadingContent = { LeadIcon(Icons.Outlined.Add) },
                    supportingContent = { Text("How to contribute, on GitHub") },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    content = { Text("Join them") },
                )
            }
        }
    }
}

/** GitHub avatar (cached after the first load) with the handle; opens the profile. */
@Composable
private fun AuthorCard(shape: Shape, onClick: () -> Unit) {
    val context = LocalContext.current
    val avatar by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(context.cacheDir, "avatar_${HushLinks.AUTHOR}.png")
                if (f.length() == 0L) {
                    val c = URL("${HushLinks.PROFILE}.png?size=192").openConnection() as HttpURLConnection
                    c.connectTimeout = 8_000; c.readTimeout = 10_000
                    try { if (c.responseCode == 200) c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } } } finally { c.disconnect() }
                }
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
            val a = avatar
            if (a != null) Image(a, "Avatar", Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            else Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("@${HushLinks.AUTHOR}", style = MaterialTheme.typography.titleMedium)
                Text(HushLinks.PROFILE.removePrefix("https://"), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
