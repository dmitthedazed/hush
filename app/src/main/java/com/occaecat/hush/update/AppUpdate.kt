package com.occaecat.hush.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Hush's own updates, from the GitHub releases of [REPO]. The latest release's tag is the version (`v0.16.0`); its
 * phone APK is the asset whose name ends in `-phone.apk` (any other `.apk` that isn't the Wear or TV build will do),
 * and a `SHA256SUMS.txt` asset, when present, is checked before installing. Installing goes through
 * [PackageInstaller]; Android itself refuses an APK signed with a different key, so a release can only replace
 * Hush if it was signed like the installed build.
 */
object AppUpdate {
    const val REPO = "dmitthedazed/hush"
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val PREFS = "app_update"

    data class Release(
        val version: String,
        val notes: String?,
        val published: String?,
        val apkUrl: String?,
        val apkName: String?,
        val sizeBytes: Long?,
        val sumsUrl: String?,
        val pageUrl: String,
        val checkedAt: Long,
    )

    data class Result(val installed: String, val release: Release?) {
        val available get() = release?.apkUrl != null && FirmwareCheck.compare(release.version, installed) > 0
    }

    sealed interface Install {
        data object Idle : Install
        data class Downloading(val fraction: Float?) : Install
        data object Verifying : Install
        /** Handed to Android; its confirmation dialog may be showing. */
        data object Installing : Install
        data class Failed(val message: String) : Install
    }

    private val _install = MutableStateFlow<Install>(Install.Idle)
    val install: StateFlow<Install> = _install

    fun installed(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    fun cached(context: Context): Result? = prefs(context).getString("latest", null)
        ?.let { runCatching { Result(installed(context), fromJson(JSONObject(it))) }.getOrNull() }

    /** Uses the cached answer for up to 6 hours unless [force]. A repository without releases yields a null release. */
    suspend fun check(context: Context, force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val have = installed(context)
        prefs(context).getString("latest", null)?.let { JSONObject(it) }?.takeIf { !force }
            ?.let { runCatching { fromJson(it) }.getOrNull() }
            ?.takeIf { System.currentTimeMillis() - it.checkedAt < TTL_MS }
            ?.let { return@withContext Result(have, it) }
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Hush/$have")
        val release = try {
            when (c.responseCode) {
                200 -> parse(JSONObject(c.inputStream.bufferedReader().readText()))
                404 -> null
                else -> error("GitHub answered ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
        prefs(context).edit().apply {
            if (release != null) putString("latest", toJson(release).toString()) else remove("latest")
        }.apply()
        Result(have, release)
    }

    private fun parse(o: JSONObject): Release {
        val assets = o.optJSONArray("assets")
        val list = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
        val apks = list.filter { it.getString("name").endsWith(".apk") }
        val apk = apks.firstOrNull { it.getString("name").endsWith("-phone.apk") }
            ?: apks.firstOrNull { a -> val n = a.getString("name").lowercase(); "wear" !in n && "tv" !in n }
        return Release(
            version = o.getString("tag_name").removePrefix("v"),
            notes = o.optString("body").trim().ifEmpty { null },
            published = o.optString("published_at").ifEmpty { null },
            apkUrl = apk?.getString("browser_download_url"),
            apkName = apk?.getString("name"),
            sizeBytes = apk?.optLong("size")?.takeIf { it > 0 },
            sumsUrl = list.firstOrNull { it.getString("name").equals("SHA256SUMS.txt", ignoreCase = true) }
                ?.getString("browser_download_url"),
            pageUrl = o.optString("html_url").ifEmpty { "https://github.com/$REPO/releases" },
            checkedAt = System.currentTimeMillis(),
        )
    }

    /** True when Hush may hand APKs to the installer; otherwise the user has to allow it in settings first. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettings(context: Context) = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        android.net.Uri.parse("package:${context.packageName}"),
    )

    /** Downloads, verifies and installs [release]; progress and errors go to [install]. */
    suspend fun downloadAndInstall(context: Context, release: Release) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            val url = release.apkUrl ?: error("This release has no phone APK")
            val dir = File(app.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, release.apkName ?: "hush.apk")
            _install.value = Install.Downloading(null)
            download(url, file) { done, total -> _install.value = Install.Downloading(total?.let { done.toFloat() / it }) }
            _install.value = Install.Verifying
            release.sumsUrl?.let { sums ->
                val want = String(fetch(sums)).lines().map { it.trim().split(Regex("\\s+"), limit = 2) }
                    .firstOrNull { it.size == 2 && it[1].removePrefix("*") == file.name }?.get(0)
                    ?: error("${file.name} isn't listed in SHA256SUMS.txt")
                val got = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                if (!got.equals(want, ignoreCase = true)) error("Checksum mismatch: the download is damaged")
            }
            val archive = app.packageManager.getPackageArchiveInfo(file.path, 0) ?: error("The download isn't an APK")
            if (archive.packageName != app.packageName) error("The APK is for ${archive.packageName}")
            _install.value = Install.Installing
            commit(app, file)
        } catch (e: Exception) {
            _install.value = Install.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun commit(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            // Once Hush is the installer of record (after its first self-update), later ones need no confirmation.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 34) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("hush.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val status = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            s.commit(status.intentSender)
        }
    }

    internal fun onStatus(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            // On success the process is replaced; nothing to show.
            PackageInstaller.STATUS_SUCCESS -> _install.value = Install.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _install.value = Install.Idle
            else -> _install.value = Install.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { msg ->
                    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || "signature" in msg.lowercase())
                        "This build is signed with a different key. Uninstall Hush, then install the release by hand."
                    else msg
                } ?: "Android didn't install the update (status $status)",
            )
        }
    }

    fun dismissError() { if (_install.value is Install.Failed) _install.value = Install.Idle }

    private fun download(url: String, to: File, progress: (Long, Long?) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Hush")
        try {
            if (c.responseCode != 200) error("Download failed: HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 }
            var done = 0L; var last = 0L
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (done - last > 256 * 1024) { progress(done, total); last = done }
                    }
                }
            }
            progress(done, total)
        } finally {
            c.disconnect()
        }
    }

    private fun fetch(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.readBytes()
        } finally {
            c.disconnect()
        }
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(r: Release) = JSONObject().put("version", r.version).put("notes", r.notes).put("published", r.published)
        .put("apkUrl", r.apkUrl).put("apkName", r.apkName).put("size", r.sizeBytes).put("sumsUrl", r.sumsUrl)
        .put("pageUrl", r.pageUrl).put("checkedAt", r.checkedAt)

    private fun JSONObject.str(k: String) = if (isNull(k)) null else optString(k).ifEmpty { null }

    private fun fromJson(o: JSONObject) = Release(
        o.getString("version"), o.str("notes"), o.str("published"), o.str("apkUrl"), o.str("apkName"),
        if (o.has("size") && !o.isNull("size")) o.getLong("size") else null, o.str("sumsUrl"),
        o.getString("pageUrl"), o.getLong("checkedAt"),
    )
}

/** PackageInstaller's status callback for [AppUpdate]. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AppUpdate.onStatus(context, intent)
}
