package com.occaecat.hush.jabra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Xml
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.occaecat.hush.devices.Firmware
import com.occaecat.hush.gnp.JabraModel
import com.occaecat.hush.gnp.JabraState
import com.occaecat.hush.ui.StatusCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import org.xmlpull.v1.XmlPullParser

/** Version lookup only. Firmware images and GNP transfer commands are deliberately absent. */
internal class JabraFirmwareCheck(
    private val app: Context,
    private val raw: StateFlow<JabraState>,
) : Firmware {
    private data class Result(val product: Int, val installed: String, val latest: String) {
        val available get() = newerVersion(latest, installed)
    }
    private val result = MutableStateFlow<Result?>(null)
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    @Composable
    override fun pending(): String? {
        val state by raw.collectAsStateWithLifecycle()
        val latest by result.collectAsStateWithLifecycle()
        LaunchedEffect(state.model, state.firmware) {
            val model = state.model ?: return@LaunchedEffect
            val version = state.firmware ?: return@LaunchedEffect
            if (latest?.product != model.productId || latest?.installed != version) recheck(app)
        }
        return latest?.takeIf { it.product == state.model?.productId && it.installed == state.firmware && it.available }
            ?.latest?.let { "firmware $it" }
    }

    @Composable
    override fun checking(): Boolean = busy.collectAsStateWithLifecycle().value

    @Composable
    override fun Section() {
        val state by raw.collectAsStateWithLifecycle()
        val observed by result.collectAsStateWithLifecycle()
        val running by busy.collectAsStateWithLifecycle()
        val failed by error.collectAsStateWithLifecycle()
        val current = state.firmware
        val checked = observed?.takeIf { it.product == state.model?.productId && it.installed == current }
        val available = checked?.available == true
        StatusCard(
            icon = when { available -> Icons.Outlined.NewReleases; failed != null -> Icons.Outlined.CloudOff; else -> Icons.Outlined.CheckCircle },
            busy = running && checked == null,
            highlight = available,
            title = when {
                current == null -> "Connect the earbuds to check"
                available -> "Firmware ${checked!!.latest} is available"
                failed != null -> "Couldn't check Jabra firmware"
                running && checked == null -> "Checking with Jabra…"
                checked != null -> "Firmware is up to date"
                else -> "Firmware version $current"
            },
            detail = listOfNotNull(current?.let { "Installed $it" }, checked?.latest?.let { "Latest $it" }).joinToString(" · ").ifEmpty { null },
        ) {
            if (available) {
                Spacer(Modifier.height(12.dp))
                Text("Install this update using Jabra Sound+ and follow Jabra's instructions.")
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { openInstructions(state.model) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.SystemUpdate, null)
                    Text("Jabra update instructions")
                }
            }
        }
    }

    private fun openInstructions(model: JabraModel?) {
        val slug = if (model == JabraModel.ELITE_85T) "jabra-elite-85t" else "jabra-elite-10"
        val url = "https://www.jabra.com/supportpages/$slug"
        app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override suspend fun recheck(context: Context) {
        val model = raw.value.model ?: return
        val installed = raw.value.firmware ?: return
        busy.value = true
        error.value = null
        try {
            val latest = withContext(Dispatchers.IO) { fetch(model) }
            result.value = Result(model.productId, installed, latest)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.w("JabraFirmware", "Firmware manifest check failed for product ${model.productId}", e)
            error.value = e.message ?: "Firmware check failed"
        } finally { busy.value = false }
    }

    private fun fetch(model: JabraModel): String {
        val endpoint = URI("https://www.jabra.com/firmwareinfo_%04X.xml".format(model.productId))
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != 200) throw IOException("Jabra firmware check returned HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > 1_048_576) throw IOException("Jabra firmware manifest is too large")
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1_048_576) throw IOException("Jabra firmware manifest is too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > 1_048_576) throw IOException("Jabra firmware manifest is too large")
            return parseManifest(bytes, model)
        } finally { connection.disconnect() }
    }

}

/** Android's XML parser does not support the desktop DOM security features. Read only direct manifest fields. */
private fun parseManifest(bytes: ByteArray, model: JabraModel): String {
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        setInput(ByteArrayInputStream(bytes), null)
    }
    var depth = 0
    var field: String? = null
    val value = StringBuilder()
    var product: String? = null
    var version: String? = null
    while (true) {
        when (parser.nextToken()) {
            XmlPullParser.DOCDECL, XmlPullParser.ENTITY_REF -> throw IOException("Unsafe Jabra manifest")
            XmlPullParser.START_TAG -> {
                depth++
                if (depth == 1 && parser.name != "firmwareVersionInfo") throw IOException("Unexpected Jabra manifest")
                if (depth == 2) {
                    field = parser.name.takeIf { it == "productName" || it == "version" }
                    value.clear()
                }
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (depth == 2 && field != null) value.append(parser.text)
            XmlPullParser.END_TAG -> {
                if (depth == 2) when (field) {
                    "productName" -> product = value.toString().trim()
                    "version" -> version = value.toString().trim()
                }
                if (depth == 2) field = null
                depth--
            }
            XmlPullParser.END_DOCUMENT -> break
        }
    }
    if (product != model.label) throw IOException("Firmware manifest is for another model")
    return version?.takeIf { it.matches(Regex("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")) }
        ?: throw IOException("Invalid Jabra firmware version")
}

private fun newerVersion(a: String, b: String): Boolean {
    fun parts(value: String): List<Int>? {
        val tokens = value.split('.')
        if (tokens.size != 3) return null
        return tokens.map { it.toIntOrNull() ?: return null }
    }
    val left = parts(a) ?: return false
    val right = parts(b) ?: return false
    return left.zip(right).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
}
