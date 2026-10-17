@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.occaecat.hush.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Where Hush lives on GitHub; change here if the repository moves. */
object HushLinks {
    const val AUTHOR = "dmitthedazed"
    const val PROFILE = "https://github.com/$AUTHOR"
    const val REPO = "https://github.com/$AUTHOR/hush"
    const val ISSUES = "$REPO/issues"
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
            Text("Your Sony headphones, without Sound Connect", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- author
        SectionHeader("Made by")
        AuthorCard { open(HushLinks.PROFILE) }

        // ---- project
        SectionHeader("Project")
        SegmentedGroup {
            ValueRow(0, 3, Icons.Outlined.Code, "Source code", HushLinks.REPO.removePrefix("https://"),
                trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) { open(HushLinks.REPO) }
            ValueRow(1, 3, Icons.Outlined.BugReport, "Report a problem", "Open an issue on GitHub",
                trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) { open(HushLinks.ISSUES) }
            ValueRow(2, 3, Icons.Outlined.Description, "Open-source licenses", "${COMPONENTS.size} components") { licenses = !licenses }
        }
        if (licenses) {
            Spacer(Modifier.height(Space.s))
            SegmentedGroup {
                COMPONENTS.forEachIndexed { i, c ->
                    ValueRow(i, COMPONENTS.size, Icons.Outlined.Copyright, c.name, "${c.license} · ${c.by}",
                        trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) { open(c.url) }
                }
            }
        }

        // ---- build
        SectionHeader("This build")
        SegmentedGroup {
            InfoRow(0, 3, Icons.Outlined.Inventory2, "Package", context.packageName)
            InfoRow(1, 3, Icons.Outlined.Android, "Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · needs API ${context.applicationInfo.minSdkVersion}+")
            InfoRow(2, 3, Icons.Outlined.PhoneAndroid, "Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        }

        // ---- notices
        SectionHeader("Notices")
        Text(
            "Hush is an independent app and is not made, endorsed or supported by Sony. Sony, WH-1000XM4, " +
                "Sound Connect and DSEE Extreme are trademarks of Sony Group Corporation; LDAC is a trademark of Sony. " +
                "Headphone images, tutorials, help articles and firmware information are Sony's and are fetched from " +
                "Sony's own servers, the way Sound Connect does, so the app can work with headphones you own.\n\n" +
                "AutoEq results are computed from measurements by Jaakko Pasanen and contributors (MIT License).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s),
        )
        Spacer(Modifier.height(Space.l))
    }
}

/** GitHub avatar (cached after the first load) with the handle; opens the profile. */
@Composable
private fun AuthorCard(onClick: () -> Unit) {
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
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
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
