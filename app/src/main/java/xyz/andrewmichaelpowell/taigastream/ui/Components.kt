//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import xyz.andrewmichaelpowell.taigastream.metadata.FaviconArtwork
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

val RowShape = RoundedCornerShape(10.dp)

@Composable
fun frostedColor(): Color = LocalTaigaStreamColors.current.secondarySystemBackground.copy(alpha = 0.7f)

@Composable
fun frostedBorder(): BorderStroke =
    BorderStroke(1.dp, LocalTaigaStreamColors.current.label.copy(alpha = 0.08f))

@Composable
fun selectedIconGreen(): Color =
    if (isSystemInDarkTheme()) Color.White else LocalTaigaStreamColors.current.green

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

@Composable
fun NowPlayingBackground(artwork: Bitmap?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appIcon = remember { FaviconArtwork.appIcon(context) }
    val source = artwork ?: appIcon
    Box(modifier = modifier) {
        Crossfade(targetState = source, animationSpec = tween(500), label = "background") { bitmap ->
            if (bitmap != null) {
                val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val image = remember(bitmap) {
                    (if (supportsBlur) bitmap else bitmap.scale(16, 16)).asImageBitmap()
                }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (supportsBlur) Modifier.blur(60.dp) else Modifier),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)),
        )
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
    val green = selectedIconGreen()
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
                    .background(green.copy(alpha = 0.18f)),
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
                            tint = if (isSelected) green else LocalTaigaStreamColors.current.label,
                        )
                    }
                }
            }
        }
    }
}

val RowInset: Dp = 6.dp
