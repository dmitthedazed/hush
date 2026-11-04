package com.occaecat.hush.devices

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Saves a store's JSON off the main thread. Writes run one at a time in order, and a burst of changes
 * (a slider, a rename) reaches the disk as its newest text only.
 */
class DiskWriter(private val file: File) {
    private val pending = AtomicReference<String?>(null)

    fun write(text: String) {
        if (pending.getAndSet(text) == null) scope.launch {
            pending.getAndSet(null)?.let(::save)
        }
    }

    /** Writes a sibling file and renames it over [file], so a killed process never leaves half a file behind. */
    private fun save(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(text)
            check(tmp.renameTo(file)) { "could not replace ${file.name}" }
        }.onFailure {
            tmp.delete()
            Log.w("Hush", "saving ${file.name} failed", it)
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    }
}
