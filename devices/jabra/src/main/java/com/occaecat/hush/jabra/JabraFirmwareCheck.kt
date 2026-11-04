package com.occaecat.hush.jabra

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

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
                failed != null -> "Couldn't reach Jabra"
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
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement as Element
            if (root.tagName != "firmwareVersionInfo") throw IOException("Unexpected Jabra manifest")
            fun field(name: String): String = root.getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
            if (field("productName") != model.label) throw IOException("Firmware manifest is for another model")
            return field("version").also { version ->
                if (!version.matches(Regex("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")))
                    throw IOException("Invalid Jabra firmware version")
            }
        } finally { connection.disconnect() }
    }

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
