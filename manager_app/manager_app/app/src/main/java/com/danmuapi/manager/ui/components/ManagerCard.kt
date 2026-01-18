package com.danmuapi.manager.ui.components

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * App-wide card style.
 *
 * All screens should use the same card background as the Dashboard's “服务状态” card.
 */
@Composable
fun ManagerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        ElevatedCard(
            modifier = modifier,
            onClick = onClick,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            content()
        }
    } else {
        ElevatedCard(
            modifier = modifier,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            content()
        }
    }
}
