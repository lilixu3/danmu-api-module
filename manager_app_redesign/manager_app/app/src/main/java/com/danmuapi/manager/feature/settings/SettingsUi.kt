package com.danmuapi.manager.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.designsystem.theme.ImmersivePalette
import com.danmuapi.manager.core.designsystem.theme.rememberImmersivePalette

typealias SettingsPalette = ImmersivePalette

@Composable
fun rememberSettingsPalette(): SettingsPalette = rememberImmersivePalette()

@Composable
fun SettingsBackdrop(
    palette: SettingsPalette,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.backdropTop,
                        palette.backdropMid,
                        palette.backdropBottom,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = 220.dp, y = (-54).dp)
                .size(230.dp)
                .clip(CircleShape)
                .background(palette.haloPrimary),
        )
        Box(
            modifier = Modifier
                .offset(x = (-72).dp, y = 104.dp)
                .size(188.dp)
                .clip(CircleShape)
                .background(palette.haloSecondary),
        )
        content()
    }
}

@Composable
fun SettingsScrollablePage(
    contentPadding: PaddingValues,
    palette: SettingsPalette,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsBackdrop(palette = palette) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .widthIn(max = 860.dp)
                        .align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun SettingsImmersiveHeader(
    title: String,
    subtitle: String,
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = palette.subtleText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

@Composable
fun SettingsHeaderIconButton(
    icon: ImageVector,
    palette: SettingsPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        color = palette.card,
        border = BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else palette.subtleText,
            )
        }
    }
}

@Composable
fun SettingsInfoPill(
    label: String,
    value: String,
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.chip)
            .border(1.dp, palette.chipBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tone?.let {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(it),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.subtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun SettingsBusyStrip(
    message: String,
    palette: SettingsPalette,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = palette.cardStrong,
        border = BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.subtleText,
        )
    }
}

@Composable
fun SettingsPanel(
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = palette.cardStrong,
        border = BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsPanelHeader(
    title: String,
    palette: SettingsPalette,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.subtleText,
                )
            }
        }
        if (trailing != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

@Composable
fun SettingsDivider(palette: SettingsPalette) {
    HorizontalDivider(color = palette.cardBorder)
}

@Composable
fun SettingsNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = palette.cardStrong,
        border = BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.accentContainer)
                    .border(1.dp, palette.mutedBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accent,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.subtleText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                summary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = palette.subtleText,
            )
        }
    }
}

@Composable
fun SettingsPlainRow(
    title: String,
    subtitle: String,
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.subtleText,
            )
        }
        if (trailing != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

@Composable
fun SettingsValueRow(
    label: String,
    value: String,
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(min = 64.dp),
            style = MaterialTheme.typography.labelLarge,
            color = palette.subtleText,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun SettingsTag(
    text: String,
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    containerColor: Color? = null,
) {
    val chipColor = containerColor ?: palette.chip
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(chipColor)
            .border(1.dp, palette.chipBorder, CircleShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tone?.let {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(it),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tone ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SettingsCodeBlock(
    text: String,
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = palette.cardMuted,
        border = BorderStroke(1.dp, palette.mutedBorder),
        shadowElevation = 0.dp,
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
