package dev.titan.code.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The reading size (rev 27, rev 28) for the Problem and Comments panes' Compose parts: everything
 * inside [content] (text, buttons, chips, padding, fixed heights) is drawn [scale] times larger,
 * so the parts keep their sizes relative to each other. The statement and comment pages get the
 * same size through WebView's textZoom.
 */
@Composable
fun ReadingScale(scale: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale), content = content)
}
