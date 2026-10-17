package com.occaecat.hush.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview

// Android Studio previews of every widget at the edges of its size range, connected and disconnected.

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 57, heightDp = 57)
@Composable
fun BatterySmallPreview() = GlanceTheme { BatteryContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
fun BatteryLargePreview() = GlanceTheme { BatteryContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
fun BatteryDisconnectedPreview() = GlanceTheme { BatteryContent(WidgetState.sampleDisconnected) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 130, heightDp = 57)
@Composable
fun StatusPreview() = GlanceTheme { StatusContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 57)
@Composable
fun StatusWidePreview() = GlanceTheme { StatusContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 130, heightDp = 130)
@Composable
fun ControlsPreview() = GlanceTheme { ControlsContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 200)
@Composable
fun ControlsLargePreview() = GlanceTheme { ControlsContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 130)
@Composable
fun DashboardPreview() = GlanceTheme { DashboardContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 360, heightDp = 230)
@Composable
fun Dashboard4x3Preview() = GlanceTheme { DashboardContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 270)
@Composable
fun DashboardTallPreview() = GlanceTheme { DashboardContent(WidgetState.sample) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 270)
@Composable
fun DashboardDisconnectedPreview() = GlanceTheme { DashboardContent(WidgetState.sampleDisconnected) }
