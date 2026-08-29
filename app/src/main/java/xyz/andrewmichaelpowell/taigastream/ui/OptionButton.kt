//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

@Composable
fun OptionButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalTaigaStreamColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = colors.secondarySystemBackground,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun OptionButton(
    text: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalTaigaStreamColors.current
    OptionButton(modifier = modifier, enabled = enabled, onClick = onClick) {
        Text(
            text,
            fontWeight = FontWeight.Bold,
            color = when {
                destructive -> Color.Red
                !enabled -> colors.tertiaryLabel
                else -> colors.label
            },
        )
    }
}

@Composable
fun TaigaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalTaigaStreamColors.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = colors.tertiaryLabel) },
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.secondarySystemBackground,
            unfocusedContainerColor = colors.secondarySystemBackground,
            disabledContainerColor = colors.secondarySystemBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = colors.mint,
            focusedTextColor = colors.label,
            unfocusedTextColor = colors.label,
            focusedLeadingIconColor = colors.tertiaryLabel,
            unfocusedLeadingIconColor = colors.tertiaryLabel,
        ),
    )
}
