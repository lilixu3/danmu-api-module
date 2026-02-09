package com.danmuapi.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    /** Optional title shown at the top of the card with default padding. */
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val inner: @Composable () -> Unit = if (title != null) {
        {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        }
    } else {
        content
    }

    if (onClick != null) {
        ElevatedCard(
            modifier = modifier,
            onClick = onClick,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            inner()
        }
    } else {
        ElevatedCard(
            modifier = modifier,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            inner()
        }
    }
}
