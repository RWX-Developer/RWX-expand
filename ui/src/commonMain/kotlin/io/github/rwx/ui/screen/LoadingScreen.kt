package io.github.rwx.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rwx.ui.model.LoadingUiState
import io.github.rwx.ui.theme.Layout
import io.github.rwx.ui.theme.LocalColorScheme
import io.github.rwx.ui.theme.Spacing

@Composable
fun LoadingScreen(state: LoadingUiState) {
    val scheme = LocalColorScheme.current
    val rawProgress = state.progress
    // 引擎的进度是「完成了几个步骤」，会一格一格地跳；补间一下，加载页就不会一顿一顿的。
    val targetProgress = remember { mutableFloatStateOf(rawProgress ?: 0f) }
        if (rawProgress != null) {
            // 只往上爬；新一局从 0 开始时直接对齐，不然条子会卡在上一局的尾巴上。
            targetProgress.floatValue = if (rawProgress < targetProgress.floatValue - 0.05f) rawProgress else maxOf(targetProgress.floatValue, rawProgress)
        }
    val progress = rawProgress?.let {
        animateFloatAsState(targetProgress.floatValue, tween(220, easing = FastOutSlowInEasing), label = "loading-progress").value
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(scheme.palette.surfaceBase).testTag("startup-loading")) {
        val compact = maxHeight < Layout.compactHeightBreakpoint
        val showHeader = maxHeight >= 320.dp
        val contentWidth = (maxWidth - Spacing.xxl).coerceAtLeast(1.dp).coerceAtMost(640.dp)
        Column(
            Modifier.width(contentWidth).align(Alignment.Center)
                .verticalScroll(rememberScrollState()).padding(vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (showHeader) MainMenuHeader(contentWidth, scheme, compact)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(
                    state.text,
                    modifier = Modifier.weight(1f).testTag("startup-loading-status"),
                    color = scheme.palette.textPrimary,
                    maxLines = if (compact) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("startup-loading-indeterminate"))
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().testTag("startup-loading-progress"),
                )
            }
            state.completedSteps.forEach { step ->
                Text(step, Modifier.fillMaxWidth(), color = scheme.palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
