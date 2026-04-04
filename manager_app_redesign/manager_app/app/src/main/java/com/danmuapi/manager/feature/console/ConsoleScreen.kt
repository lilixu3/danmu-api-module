package com.danmuapi.manager.feature.console

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.danmuapi.manager.app.state.ManagerViewModel

@Composable
fun ConsoleScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onOpenSettings: () -> Unit,
) {
    ConsoleRecordScreen(
        contentPadding = contentPadding,
        viewModel = viewModel,
        onOpenSettings = onOpenSettings,
    )
}
