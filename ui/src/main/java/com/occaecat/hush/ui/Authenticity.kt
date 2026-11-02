@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** One line of an authenticity check: passed (true), failed (false) or couldn't be read (null). */
data class AuthItem(val title: String, val detail: String, val passed: Boolean?)

/** How the device came out: every check passed, nothing failed but some weren't read, or something failed. */
enum class AuthVerdict { GENUINE, LIKELY, SUSPICIOUS }

/**
 * The Authenticity section shared by every brand: the verdict card, the individual checks behind "Show checks" and
 * a footnote on what the check can and can't prove. [actions] go next to the toggle (e.g. "Allow USB access").
 */
@Composable
fun AuthenticitySection(
    verdict: AuthVerdict,
    title: String,
    body: String,
    items: List<AuthItem>,
    footnote: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val good = verdict != AuthVerdict.SUSPICIOUS
    val icon = when (verdict) {
        AuthVerdict.GENUINE -> Icons.Outlined.VerifiedUser
        AuthVerdict.LIKELY -> Icons.Outlined.GppGood
        AuthVerdict.SUSPICIOUS -> Icons.Outlined.GppMaybe
    }
    val onContainer = if (good) colors.onPrimaryContainer else colors.onErrorContainer
    Column(ItemWidth) {
        SectionHeader("Authenticity")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(if (good) colors.primaryContainer else colors.errorContainer).padding(Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(icon, size = 48.dp, polygon = if (good) MaterialShapes.Cookie9Sided else MaterialShapes.Burst,
                    container = if (good) colors.primary else colors.error, content = if (good) colors.onPrimary else colors.onError)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = onContainer)
                    Text("${items.count { it.passed == true }} of ${items.size} checks passed",
                        style = MaterialTheme.typography.bodySmall, color = onContainer)
                }
            }
            Spacer(Modifier.height(Space.s))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = onContainer)
            Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                actions()
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide checks" else "Show checks") }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(Space.s))
            SegmentedGroup {
                items.forEachIndexed { i, item ->
                    InfoRow(i, items.size, when (item.passed) {
                        true -> Icons.Outlined.CheckCircle
                        false -> Icons.Outlined.Cancel
                        null -> Icons.Outlined.HelpOutline
                    }, item.title, item.detail)
                }
            }
            Text(footnote, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s))
        }
    }
}
