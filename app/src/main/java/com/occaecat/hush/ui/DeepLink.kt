package com.occaecat.hush.ui

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A page to open from outside (a notification): MainActivity's "open" extra, consumed by the Device tab. */
object DeepLink {
    private val _target = MutableStateFlow<String?>(null)
    val target: StateFlow<String?> = _target

    fun open(page: String) { _target.value = page }

    fun from(intent: Intent?) {
        intent?.getStringExtra("open")?.let { _target.value = it }
    }

    fun consume() { _target.value = null }
}
