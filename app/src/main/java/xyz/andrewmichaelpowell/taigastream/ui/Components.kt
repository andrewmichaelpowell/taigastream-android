//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

val RowShape = RoundedCornerShape(10.dp)

@Composable
fun frostedColor(): Color = LocalTaigaStreamColors.current.secondarySystemBackground.copy(alpha = 0.7f)

@Composable
fun frostedBorder(): BorderStroke =
    BorderStroke(1.dp, LocalTaigaStreamColors.current.label.copy(alpha = 0.08f))

@Composable
fun CircleButton(
    onClick: () -> Unit,
    isOn: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalTaigaStreamColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isOn) colors.green else frostedColor(),
        border = if (isOn) null else frostedBorder(),
        modifier = modifier.size(50.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun PageChooser(
    selectedIndex: Int,
    items: List<Pair<ImageVector, String>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemWidth = 80.dp
    val itemHeight = 48.dp
    val indicatorOffset by animateDpAsState(itemWidth * selectedIndex, label = "indicator")
    val colors = LocalTaigaStreamColors.current
    Surface(
        shape = CircleShape,
        color = LocalTaigaStreamColors.current.secondarySystemBackground.copy(alpha = 0.9f),
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .size(itemWidth, itemHeight)
                    .clip(CircleShape)
                    .background(colors.green),
            )
            Row {
                items.forEachIndexed { index, (icon, label) ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .size(itemWidth, itemHeight)
                            .clip(CircleShape)
                            .semantics {
                                contentDescription = label
                                selected = isSelected
                            }
                            .clickable(role = Role.Tab) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else colors.label,
                        )
                    }
                }
            }
        }
    }
}

val RowInset: Dp = 6.dp
