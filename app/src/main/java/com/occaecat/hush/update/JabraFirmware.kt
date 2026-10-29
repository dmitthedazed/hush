package com.occaecat.hush.update

import android.content.Context
import android.util.AtomicFile
import com.occaecat.hush.jabra.firmware.JabraFirmwareFile
import com.occaecat.hush.jabra.firmware.JabraFirmwarePackage
import com.occaecat.hush.jabra.firmware.JabraFirmwareVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/** Public metadata and intact firmware files; no vendor updater code or bundled firmware. */
object JabraFirmware {
    data class Manifest(
        val productId: Int, val productName: String, val version: String, val released: String?,
        val downloadUrl: String, val releaseNotesUrl: String?, val languages: Map<Int, String>,
    ) {
        fun available(current: String): Boolean = compare(version, current) > 0
        val directory: String get() = downloadUrl.removeSuffix(".zip") + "/"
    }

    internal data class Part(val name: String, val partition: Int, val version: String,
                             val languageId: Int, val crc: Long, val crcOffset: Int, val order: Int)
    internal data class Vector(val parts: List<Part>, val preloadCount: Int, val partialUploadAllowed: Boolean)
    data class DownloadProgress(val completedFiles: Int, val totalFiles: Int)

    private const val METADATA_LIMIT = 1_048_576
    private const val FILE_LIMIT = 52 * 65535
    private const val TTL = 12 * 60 * 60 * 1000L
    private val models = mapOf(11846 to "Jabra Elite 10", 9421 to "Jabra Elite 85t")
    private val cacheWrites = Any()

    fun supports(productId: Int) = productId in models

    suspend fun check(context: Context, productId: Int, force: Boolean = false): Manifest = withContext(Dispatchers.IO) {
        require(supports(productId)) { "Unsupported Jabra product" }
        val file = File(cacheRoot(context), "manifest-$productId.xml")
        if (!force && file.isFile && System.currentTimeMillis() - file.lastModified() in 0 until TTL) {
            runCatching { parseManifest(readBounded(file, METADATA_LIMIT), productId) }.getOrNull()?.let { return@withContext it }
        }
        val bytes = download("https://www.jabra.com/firmwareinfo_%04X.xml".format(productId), METADATA_LIMIT)
        val manifest = parseManifest(bytes, productId)
        writeAtomic(file, bytes)
        manifest
    }

    /** Cached bytes are re-parsed and CRC checked before every use. A failed download is never reusable. */
    suspend fun prepare(context: Context, manifest: Manifest, languageId: Int,
                        onProgress: (DownloadProgress) -> Unit = {}): JabraFirmwarePackage = withContext(Dispatchers.IO) {
        require(languageId in manifest.languages) { "The update does not contain the headphones' current language" }
        val source = sha256(manifest.downloadUrl.toByteArray()).take(16)
        val directory = File(cacheRoot(context), "${manifest.productId}-${manifest.version}-$languageId-$source").apply { mkdirs() }
        val metadataFile = File(directory, "info.xml")
        val cached = if (metadataFile.isFile && System.currentTimeMillis() - metadataFile.lastModified() in 0 until TTL)
            runCatching { readBounded(metadataFile, METADATA_LIMIT).let { it to parseVector(it, manifest, languageId) } }.getOrNull() else null
        val (metadata, vector) = cached ?: download(manifest.directory + "info.xml", METADATA_LIMIT).let { bytes ->
            (bytes to parseVector(bytes, manifest, languageId)).also { writeAtomic(metadataFile, bytes) }
        }
        val files = vector.parts.mapIndexed { index, part ->
            currentCoroutineContext().ensureActive()
            onProgress(DownloadProgress(index, vector.parts.size))
            val target = File(directory, part.name)
            fun verify(bytes: ByteArray) = JabraFirmwareFile.verified(part.partition,
                JabraFirmwareVersion.parse(part.version, part.languageId), part.crc, bytes, part.crcOffset)
            val cached = if (target.isFile) runCatching { verify(readBounded(target, FILE_LIMIT)) }.getOrNull() else null
            cached ?: download(manifest.directory + part.name, FILE_LIMIT).let { bytes ->
                verify(bytes).also { writeAtomic(target, bytes) }
            }
        }
        val identity = sha256(metadata + files.joinToString("\n") { "${it.partition}:${it.sha256}" }.toByteArray())
        JabraFirmwarePackage.verified(manifest.productId, manifest.version, languageId, files,
            vector.preloadCount, vector.partialUploadAllowed, identity).also {
            onProgress(DownloadProgress(files.size, files.size))
        }
    }

    internal fun parseManifest(bytes: ByteArray, productId: Int): Manifest {
        val expectedName = models[productId] ?: error("Unsupported Jabra product")
        val root = xml(bytes, "firmwareVersionInfo")
        require(root.required("productName") == expectedName) { "Firmware manifest belongs to a different product" }
        val version = root.required("version").also(::versionParts)
        validateUpdaterVersion(root.optional("minFirmwareUpdaterAppVersion"))
        val download = root.required("downloadUrl").also { url ->
            val uri = allowedUrl(url)
            require(uri.path.matches(Regex("/Firmware_Data/[A-Za-z0-9_.-]+\\.zip")) && !uri.path.contains("..")) {
                "Unsupported Jabra firmware package URL"
            }
        }
        val languageNodes = root.one("headsetLanguages").children("language")
        require(languageNodes.isNotEmpty() && languageNodes.size <= 64)
        val languages = languageNodes.associate { node ->
            number(node.getAttribute("id"), 65535).toInt() to node.textContent.trim().take(80)
        }
        require(languages.size == languageNodes.size && languages.keys.none { it == 0 }) { "Duplicate or invalid firmware languages" }
        return Manifest(productId, expectedName, version, root.optional("releaseDate"), download,
            root.optional("releaseNotesUrl")?.also { allowedUrl(it) }, languages)
    }

    internal fun parseVector(bytes: ByteArray, manifest: Manifest, languageId: Int): Vector {
        require(languageId in manifest.languages)
        val root = xml(bytes, "buildVector")
        require(root.getAttribute("version") == manifest.version) { "Package version does not match its manifest" }
        val targets = root.one("targetUsbPids").children("usbPid").map { number(it.textContent.trim(), 65535).toInt() }
        require(manifest.productId in targets && targets.size == targets.distinct().size) { "Package does not target these headphones" }
        validateUpdaterVersion(root.optional("minFirmwareUpdaterAppVersion"))
        val partial = when (root.required("partialUploadAllowed").lowercase()) {
            "true" -> true; "false" -> false; else -> error("Invalid partial upload policy")
        }
        val preload = number(root.required("maxPreloadCount"), 100).toInt().also { require(it > 0) }
        val nodes = root.one("files").children("file")
        require(nodes.size in 1..256) { "Invalid firmware component count" }
        val all = nodes.map { node ->
            val name = node.getAttribute("name")
            require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,159}")) && !name.contains("..")) { "Unsafe firmware filename" }
            val content = node.one("content")
            require(content.textContent.trim() == "firmware" && node.required("target") == "headset") { "Unsupported firmware target" }
            val signed = content.getAttribute("gnsigned").let { value ->
                require(value.isEmpty() || value.equals("true", true) || value.equals("false", true))
                value.equals("true", true)
            }
            val language = node.children("language").also { require(it.size <= 1) }.singleOrNull()
            val version = node.required("version").also(::versionParts)
            require(version == manifest.version) { "Component version does not match the update" }
            Part(name, number(node.required("partition"), 255).toInt(), version,
                language?.let { number(it.getAttribute("id"), 65535).toInt() } ?: 0,
                number(node.required("crc"), 0xffffffffL), if (signed) 52 else 0,
                node.optional("updateOrder")?.let { number(it, 65535).toInt() } ?: 0)
        }
        require(all.map { it.name }.distinct().size == all.size) { "Duplicate firmware filenames" }
        val selected = all.filter { it.languageId == 0 || it.languageId == languageId }.sortedBy { it.order }
        require(selected.isNotEmpty() && selected.map { it.partition }.distinct().size == selected.size) { "Duplicate or missing firmware partitions" }
        require(selected.map { it.partition }.toSet() == all.map { it.partition }.toSet()) { "The selected language is missing a firmware partition" }
        val expected = if (manifest.productId == 11846) setOf(0) else setOf(0, 1, 4, 5, 6, 7, 9, 254)
        require(selected.map { it.partition }.toSet() == expected) { "Unsupported firmware partition layout" }
        return Vector(selected, preload, partial)
    }

    internal fun compare(a: String, b: String): Int {
        val left = versionParts(a); val right = versionParts(b)
        return left.zip(right).firstOrNull { it.first != it.second }?.let { it.first.compareTo(it.second) } ?: 0
    }

    private fun versionParts(value: String): List<Int> {
        require(value.matches(Regex("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}"))) { "Unsupported firmware version" }
        return value.split('.').map { it.toInt().also { n -> require(n in 0..255) } }
    }

    private fun validateUpdaterVersion(value: String?) {
        if (value == null) return
        require(value.matches(Regex("[0-9]+(?:\\.[0-9]+){0,3}"))) { "Invalid updater compatibility version" }
        val parts = value.split('.').map { it.toInt() }.let { it + List(4 - it.size) { 0 } }
        val supported = listOf(5, 0, 0, 0)
        require(parts.zip(supported).firstOrNull { it.first != it.second }?.let { it.first < it.second } != false) {
            "This package requires a newer Jabra updater protocol profile"
        }
    }

    internal fun allowedUrl(value: String): URI {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host?.lowercase() in setOf("www.jabra.com", "jabra.com") &&
            uri.port in setOf(-1, 443) && uri.userInfo == null && uri.fragment == null && uri.query == null) {
            "Firmware downloads must come from Jabra over HTTPS"
        }
        return uri
    }

    private suspend fun download(value: String, limit: Int): ByteArray {
        var uri = allowedUrl(value)
        repeat(5) {
            currentCoroutineContext().ensureActive()
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            try {
                when (val status = connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        uri = allowedUrl(uri.resolve(connection.getHeaderField("Location") ?: error("Missing redirect URL")).toString())
                    }
                    200 -> {
                        require(connection.contentLengthLong <= limit) { "Firmware download exceeds the size limit" }
                        return connection.inputStream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(16_384)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= limit) { "Firmware download exceeds the size limit" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray().also { require(it.isNotEmpty()) { "Empty firmware download" } }
                        }
                    }
                    else -> throw IOException("Jabra firmware server returned HTTP $status")
                }
            } finally { connection.disconnect() }
        }
        throw IOException("Too many firmware download redirects")
    }

    private fun xml(bytes: ByteArray, rootName: String): Element {
        require(bytes.size in 1..METADATA_LIMIT) { "Invalid firmware metadata size" }
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains(Regex("<!\\s*(?:DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE))) { "External XML declarations are forbidden" }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false; isExpandEntityReferences = false }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw IOException("External XML entities are forbidden") }
        }
        val root = builder.parse(InputSource(StringReader(text))).documentElement
        require(root.tagName == rootName) { "Unexpected firmware metadata document" }
        return root
    }

    private fun Element.children(name: String): List<Element> = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == name }
    private fun Element.one(name: String): Element = children(name).also { require(it.size == 1) { "Missing or duplicate $name" } }.single()
    private fun Element.required(name: String): String = one(name).textContent.trim().also { require(it.isNotEmpty()) { "Empty $name" } }
    private fun Element.optional(name: String): String? = children(name).also { require(it.size <= 1) { "Duplicate $name" } }
        .singleOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    private fun number(text: String, max: Long): Long {
        require(text.matches(Regex("(?:0[xX][0-9a-fA-F]+|[0-9]+)"))) { "Invalid firmware metadata number" }
        return (if (text.startsWith("0x", true)) text.drop(2).toLong(16) else text.toLong()).also { require(it in 0..max) }
    }
    private fun cacheRoot(context: Context) = File(context.noBackupFilesDir, "jabra_firmware").apply { mkdirs() }
    private fun readBounded(file: File, limit: Int): ByteArray {
        require(file.length() in 1..limit.toLong()) { "Invalid cached firmware size" }
        return file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit) { "Cached firmware exceeds the size limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
    private fun writeAtomic(file: File, bytes: ByteArray) = synchronized(cacheWrites) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
