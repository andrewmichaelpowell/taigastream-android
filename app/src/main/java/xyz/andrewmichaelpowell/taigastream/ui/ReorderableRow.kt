//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

private val LiftedShape = RoundedCornerShape(16.dp)

@Composable
fun LazyItemScope.ReorderableRow(
    state: ReorderableLazyListState,
    key: Any,
    onDragStopped: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ReorderableItem(state, key = key) { isDragging ->
        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
        val scale by animateFloatAsState(if (isDragging) 1.03f else 1f, label = "scale")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp - RowInset)
                .scale(scale)
                .shadow(elevation, LiftedShape)
                .then(
                    if (isDragging) {
                        Modifier.background(
                            LocalTaigaStreamColors.current.secondarySystemBackground.copy(alpha = 0.85f),
                            LiftedShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .longPressDraggableHandle(
                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragStopped = { onDragStopped() },
                )
                .padding(horizontal = RowInset, vertical = 4.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
