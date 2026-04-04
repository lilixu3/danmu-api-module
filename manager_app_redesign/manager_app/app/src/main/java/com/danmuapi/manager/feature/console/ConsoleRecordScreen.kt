package com.danmuapi.manager.feature.console

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.model.LogFileEntry
import com.danmuapi.manager.core.model.RequestRecord
import com.danmuapi.manager.core.model.ServerLogEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class ConsoleMainTab(val label: String) {
    Logs("日志"),
    Requests("请求"),
}

private enum class ConsoleLogSource(val label: String) {
    Service("服务"),
    Module("模块"),
}

private enum class ConsoleLogFilter(val label: String) {
    All("全部"),
    Error("报错"),
    Warning("警告"),
}

private enum class RenderedLogTone {
    Default,
    Error,
    Warning,
    Accent,
}

private data class RenderedLogLine(
    val text: String,
    val tone: RenderedLogTone,
)

private data class ConsolePalette(
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val haloPrimary: Color,
    val haloSecondary: Color,
    val panel: Color,
    val panelStrong: Color,
    val panelBorder: Color,
    val subtleText: Color,
    val accent: Color,
    val accentSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val terminal: Color,
    val terminalBorder: Color,
    val terminalText: Color,
    val terminalMuted: Color,
    val terminalError: Color,
    val terminalWarning: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConsoleRecordScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onOpenSettings: () -> Unit,
) {
    val consoleLogLimit by viewModel.consoleLogLimit.collectAsStateWithLifecycle()
    val palette = rememberConsolePalette()

    var selectedTab by rememberSaveable { mutableStateOf(ConsoleMainTab.Logs) }
    var selectedLogSource by rememberSaveable { mutableStateOf(ConsoleLogSource.Service) }
    var selectedLogFilter by rememberSaveable { mutableStateOf(ConsoleLogFilter.All) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showModuleFileSheet by rememberSaveable { mutableStateOf(false) }
    var showLogSearch by rememberSaveable { mutableStateOf(false) }
    var logSearchQuery by rememberSaveable { mutableStateOf("") }
    var expandedRequestKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLogExportText by remember { mutableStateOf<String?>(null) }
    var pendingLogExportName by rememberSaveable { mutableStateOf("danmu_logs.txt") }

    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val exportText = pendingLogExportText
        if (uri != null && exportText != null) {
            viewModel.exportTextToUri(
                uri = uri,
                text = exportText,
                successMessage = "已导出日志",
                failureMessage = "日志导出失败",
            )
        }
        pendingLogExportText = null
    }

    val moduleFiles = viewModel.logs?.files.orEmpty()
    val selectedModuleFile = remember(moduleFiles, viewModel.moduleLogPath) {
        moduleFiles.firstOrNull { it.path == viewModel.moduleLogPath } ?: moduleFiles.firstOrNull()
    }

    LaunchedEffect(selectedTab, selectedLogSource, viewModel.status?.isRunning, moduleFiles, viewModel.moduleLogPath) {
        when (selectedTab) {
            ConsoleMainTab.Logs -> {
                when (selectedLogSource) {
                    ConsoleLogSource.Service -> {
                        if (viewModel.status?.isRunning == true &&
                            viewModel.serverLogs.isEmpty() &&
                            !viewModel.serverLogsLoading
                        ) {
                            viewModel.refreshServerLogs()
                        }
                    }

                    ConsoleLogSource.Module -> {
                        if (viewModel.logs == null) {
                            viewModel.refreshLogs()
                        }
                        val target = selectedModuleFile ?: moduleFiles.firstOrNull()
                        if (target != null && viewModel.moduleLogPath != target.path) {
                            viewModel.loadModuleLog(target.path)
                        }
                    }
                }
            }

            ConsoleMainTab.Requests -> {
                if (viewModel.status?.isRunning == true &&
                    viewModel.requestRecords.isEmpty() &&
                    !viewModel.requestRecordsLoading
                ) {
                    viewModel.refreshRequestRecords()
                }
            }
        }
    }

    LaunchedEffect(selectedTab, selectedLogSource, selectedModuleFile?.path, viewModel.status?.isRunning) {
        if (selectedTab != ConsoleMainTab.Logs) return@LaunchedEffect

        while (isActive) {
            delay(2_000L)
            when (selectedLogSource) {
                ConsoleLogSource.Service -> {
                    if (viewModel.status?.isRunning == true) {
                        viewModel.refreshServerLogs()
                    }
                }

                ConsoleLogSource.Module -> {
                    selectedModuleFile?.path?.let(viewModel::loadModuleLog)
                }
            }
        }
    }

    val renderedLogLines = remember(
        selectedLogSource,
        selectedLogFilter,
        consoleLogLimit,
        viewModel.serverLogs,
        viewModel.moduleLogText,
    ) {
        when (selectedLogSource) {
            ConsoleLogSource.Service -> buildServiceLogLines(
                serverLogs = viewModel.serverLogs,
                filter = selectedLogFilter,
                limit = consoleLogLimit,
            )

            ConsoleLogSource.Module -> buildModuleLogLines(
                text = viewModel.moduleLogText,
                filter = selectedLogFilter,
            )
        }
    }
    val visibleLogLines = remember(renderedLogLines, logSearchQuery) {
        filterLogLines(renderedLogLines, logSearchQuery)
    }

    val statusText = remember(
        selectedLogSource,
        selectedModuleFile,
        consoleLogLimit,
        viewModel.serverLogsLoading,
        viewModel.moduleLogLoading,
    ) {
        when (selectedLogSource) {
            ConsoleLogSource.Service -> {
                if (viewModel.serverLogsLoading && viewModel.serverLogs.isNotEmpty()) {
                    "自动刷新中 · 服务日志 · 最近 $consoleLogLimit 行"
                } else {
                    "自动刷新中 · 服务日志 · 最近 $consoleLogLimit 行"
                }
            }

            ConsoleLogSource.Module -> {
                val fileLabel = selectedModuleFile?.name ?: "未选择日志文件"
                val refreshState = if (viewModel.moduleLogLoading && viewModel.moduleLogText.isNotBlank()) {
                    "自动刷新中"
                } else {
                    "自动刷新中"
                }
                "$refreshState · $fileLabel · 最近 $consoleLogLimit 行"
            }
        }
    }
    val displayStatusText = remember(statusText, logSearchQuery, visibleLogLines.size, renderedLogLines.size) {
        val keyword = logSearchQuery.trim()
        if (keyword.isBlank()) {
            statusText
        } else {
            "$statusText · 搜索 \"$keyword\" ${visibleLogLines.size}/${renderedLogLines.size} 条"
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(if (selectedLogSource == ConsoleLogSource.Service) "清空服务日志" else "清空模块日志") },
            text = { Text("清空后当前日志内容会立即消失，这个操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        if (selectedLogSource == ConsoleLogSource.Service) {
                            viewModel.clearServerLogs()
                        } else {
                            viewModel.clearLogs()
                        }
                    },
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showModuleFileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModuleFileSheet = false },
            containerColor = palette.panelStrong,
        ) {
            ModuleFileSheet(
                files = moduleFiles,
                selectedPath = selectedModuleFile?.path,
                palette = palette,
                onSelect = { file ->
                    showModuleFileSheet = false
                    viewModel.loadModuleLog(file.path)
                },
            )
        }
    }

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
                .offset(x = 210.dp, y = (-44).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(palette.haloPrimary),
        )
        Box(
            modifier = Modifier
                .offset(x = (-58).dp, y = 180.dp)
                .size(176.dp)
                .clip(CircleShape)
                .background(palette.haloSecondary),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ConsolePageHeader(
                    palette = palette,
                    onOpenSettings = onOpenSettings,
                )

                viewModel.busyMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    ConsoleBusyStrip(
                        message = message,
                        palette = palette,
                    )
                }

                RecordTabSwitcher(
                    selectedTab = selectedTab,
                    palette = palette,
                    onSelect = { selectedTab = it },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            ) {
                when (selectedTab) {
                    ConsoleMainTab.Logs -> {
                        LogsWorkbench(
                            viewModel = viewModel,
                            consoleLogLimit = consoleLogLimit,
                            selectedLogSource = selectedLogSource,
                            selectedLogFilter = selectedLogFilter,
                            selectedModuleFile = selectedModuleFile,
                            renderedLogLines = visibleLogLines,
                            statusText = displayStatusText,
                            showLogSearch = showLogSearch,
                            logSearchQuery = logSearchQuery,
                            palette = palette,
                            onLogSourceChange = { selectedLogSource = it },
                            onLogFilterChange = { selectedLogFilter = it },
                            onSearchQueryChange = { logSearchQuery = it },
                            onToggleSearch = {
                                showLogSearch = !showLogSearch
                                if (!showLogSearch) {
                                    logSearchQuery = ""
                                }
                            },
                            onExport = {
                                pendingLogExportText = visibleLogLines.joinToString(separator = "\n") { it.text }
                                pendingLogExportName = buildLogExportFileName(
                                    source = selectedLogSource,
                                    filter = selectedLogFilter,
                                    query = logSearchQuery,
                                )
                                logExportLauncher.launch(pendingLogExportName)
                            },
                            onClear = { showClearDialog = true },
                            onOpenModuleFiles = { showModuleFileSheet = true },
                        )
                    }

                    ConsoleMainTab.Requests -> {
                        RequestsWorkbench(
                            viewModel = viewModel,
                            palette = palette,
                            expandedRequestKey = expandedRequestKey,
                            onExpandedRequestChange = { key ->
                                expandedRequestKey = if (expandedRequestKey == key) null else key
                            },
                            onRefresh = { viewModel.refreshRequestRecords() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsolePageHeader(
    palette: ConsolePalette,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "记录",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.4).sp,
                ),
            )
            Text(
                text = "日志与请求集中查看，保持清晰和高信息密度。",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = palette.subtleText,
            )
        }
        Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = palette.panel.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, palette.panelBorder),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = if (palette.panel.luminance() > 0.5f) Color(0xFF1E293B) else Color.White,
                )
            }
        }
    }
}

@Composable
private fun ConsoleBusyStrip(
    message: String,
    palette: ConsolePalette,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = palette.panel.copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = palette.accent,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = palette.subtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecordTabSwitcher(
    selectedTab: ConsoleMainTab,
    palette: ConsolePalette,
    onSelect: (ConsoleMainTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = palette.panel.copy(alpha = 0.88f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConsoleMainTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Surface(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(tab) },
                    color = if (selected) palette.accent else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsWorkbench(
    viewModel: ManagerViewModel,
    consoleLogLimit: Int,
    selectedLogSource: ConsoleLogSource,
    selectedLogFilter: ConsoleLogFilter,
    selectedModuleFile: LogFileEntry?,
    renderedLogLines: List<RenderedLogLine>,
    statusText: String,
    showLogSearch: Boolean,
    logSearchQuery: String,
    palette: ConsolePalette,
    onLogSourceChange: (ConsoleLogSource) -> Unit,
    onLogFilterChange: (ConsoleLogFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onOpenModuleFiles: () -> Unit,
) {
    val emptyTitle = if (logSearchQuery.isBlank()) {
        if (selectedLogSource == ConsoleLogSource.Service) "暂无服务日志" else "暂无模块日志"
    } else {
        "没有命中日志"
    }
    val emptyDetail = if (logSearchQuery.isBlank()) {
        if (selectedLogSource == ConsoleLogSource.Service) {
            "服务启动后，这里会滚动显示最近日志。"
        } else {
            "先选择日志文件，或先执行安装、切换、启动等动作。"
        }
    } else {
        "当前日志源和分类下，没有命中 \"$logSearchQuery\" 的内容。"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConsoleToolbar(
            selectedLogSource = selectedLogSource,
            selectedLogFilter = selectedLogFilter,
            showLogSearch = showLogSearch,
            palette = palette,
            onLogSourceChange = onLogSourceChange,
            onLogFilterChange = onLogFilterChange,
            exportEnabled = renderedLogLines.isNotEmpty(),
            onToggleSearch = onToggleSearch,
            onExport = onExport,
            onClear = onClear,
        )

        if (showLogSearch) {
            LogSearchStrip(
                query = logSearchQuery,
                resultCount = renderedLogLines.size,
                palette = palette,
                onQueryChange = onSearchQueryChange,
                onClose = onToggleSearch,
            )
        }

        if (selectedLogSource == ConsoleLogSource.Module) {
            ModuleFileSelector(
                file = selectedModuleFile,
                palette = palette,
                onClick = onOpenModuleFiles,
            )
        }

        TerminalPanel(
            viewerKey = "${selectedLogSource.name}|${selectedLogFilter.name}|${selectedModuleFile?.path.orEmpty()}",
            lines = renderedLogLines,
            statusText = statusText,
            loading = if (selectedLogSource == ConsoleLogSource.Service) {
                viewModel.serverLogsLoading && viewModel.serverLogs.isEmpty()
            } else {
                viewModel.moduleLogLoading && viewModel.moduleLogText.isBlank()
            },
            error = if (selectedLogSource == ConsoleLogSource.Service) {
                viewModel.serverLogsError
            } else {
                viewModel.moduleLogError
            },
            emptyTitle = emptyTitle,
            emptyDetail = emptyDetail,
            palette = palette,
            limit = consoleLogLimit,
        )
    }
}

@Composable
private fun ConsoleToolbar(
    selectedLogSource: ConsoleLogSource,
    selectedLogFilter: ConsoleLogFilter,
    showLogSearch: Boolean,
    palette: ConsolePalette,
    onLogSourceChange: (ConsoleLogSource) -> Unit,
    onLogFilterChange: (ConsoleLogFilter) -> Unit,
    exportEnabled: Boolean,
    onToggleSearch: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = palette.panel.copy(alpha = 0.9f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactSegmentGroup(
                options = ConsoleLogSource.entries,
                selected = selectedLogSource,
                palette = palette,
                onSelect = onLogSourceChange,
                label = { it.label },
            )

            CompactSegmentGroup(
                options = ConsoleLogFilter.entries,
                selected = selectedLogFilter,
                palette = palette,
                onSelect = onLogFilterChange,
                label = { it.label },
            )

            Spacer(modifier = Modifier.width(4.dp))

            Box {
                ToolbarIconButton(
                    icon = Icons.Filled.MoreHoriz,
                    contentDescription = "更多操作",
                    palette = palette,
                    onClick = { menuExpanded = true },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (showLogSearch) "关闭搜索" else "搜索日志") },
                        onClick = {
                            menuExpanded = false
                            onToggleSearch()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出当前日志") },
                        enabled = exportEnabled,
                        onClick = {
                            menuExpanded = false
                            onExport()
                        },
                    )
                    if (selectedLogSource == ConsoleLogSource.Service) {
                        DropdownMenuItem(
                            text = { Text("清理服务日志") },
                            onClick = {
                                menuExpanded = false
                                onClear()
                            },
                        )
                    }
                }
            }

            if (selectedLogSource == ConsoleLogSource.Module) {
                ToolbarIconButton(
                    icon = Icons.Filled.DeleteOutline,
                    contentDescription = "清空日志",
                    palette = palette,
                    onClick = onClear,
                )
            }
        }
    }
}

@Composable
private fun LogSearchStrip(
    query: String,
    resultCount: Int,
    palette: ConsolePalette,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = palette.panelStrong,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = palette.subtleText,
            )
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(palette.accent),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (query.isBlank()) {
                                Text(
                                    text = "输入关键词，只显示命中行",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.subtleText,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
            if (query.isNotBlank()) {
                Text(
                    text = "$resultCount 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.subtleText,
                )
            }
            TextButton(onClick = onClose) {
                Text("收起")
            }
        }
    }
}

@Composable
private fun <T> CompactSegmentGroup(
    options: List<T>,
    selected: T,
    palette: ConsolePalette,
    onSelect: (T) -> Unit,
    label: (T) -> String,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.accentSoft,
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val active = option == selected
                Surface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) palette.accent else Color.Transparent,
                    shadowElevation = 0.dp,
                ) {
                    Text(
                        text = label(option),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    palette: ConsolePalette,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = palette.panelStrong,
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ModuleFileSelector(
    file: LogFileEntry?,
    palette: ConsolePalette,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        color = palette.panelStrong,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = file?.name ?: "选择日志文件",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = file?.modifiedAt ?: "点开后切换要查看的模块日志文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.subtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = palette.subtleText,
            )
        }
    }
}

@Composable
private fun TerminalPanel(
    viewerKey: String,
    lines: List<RenderedLogLine>,
    statusText: String,
    loading: Boolean,
    error: String?,
    emptyTitle: String,
    emptyDetail: String,
    palette: ConsolePalette,
    limit: Int,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var followTail by remember(viewerKey) { mutableStateOf(true) }
    var pendingLines by remember(viewerKey) { mutableIntStateOf(0) }
    var previousLineCount by remember(viewerKey) { mutableIntStateOf(0) }
    var previousLastLine by remember(viewerKey) { mutableStateOf<String?>(null) }

    val annotatedText = remember(lines, palette) { buildAnnotatedLogText(lines, palette) }
    val panelStateText by remember(statusText, followTail, pendingLines) {
        derivedStateOf {
            if (followTail || pendingLines == 0) {
                statusText
            } else {
                "已暂停跟随 · 新增 $pendingLines 条"
            }
        }
    }

    LaunchedEffect(viewerKey, lines) {
        if (loading || error != null) return@LaunchedEffect

        val currentLastLine = lines.lastOrNull()?.text
        val contentChanged = currentLastLine != previousLastLine || lines.size != previousLineCount
        if (!contentChanged) return@LaunchedEffect

        val incomingCount = when {
            previousLastLine == null -> lines.size
            lines.isEmpty() -> 0
            lines.size > previousLineCount -> lines.size - previousLineCount
            currentLastLine != previousLastLine -> 1
            else -> 0
        }

        if (followTail) {
            scrollState.scrollTo(scrollState.maxValue)
            pendingLines = 0
        } else if (incomingCount > 0) {
            pendingLines += incomingCount
        }

        previousLineCount = lines.size
        previousLastLine = currentLastLine
    }

    LaunchedEffect(viewerKey, scrollState) {
        snapshotFlow {
            Triple(scrollState.isScrollInProgress, scrollState.value, scrollState.maxValue)
        }.collect { (inProgress, value, maxValue) ->
            if (!inProgress) return@collect
            val nearBottom = maxValue - value <= 28
            if (nearBottom) {
                followTail = true
                pendingLines = 0
            } else {
                followTail = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = palette.terminal,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, palette.terminalBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = panelStateText,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.terminalMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${lines.size} 行",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.terminalMuted,
                )
            }
            HorizontalDivider(color = palette.terminalBorder.copy(alpha = 0.9f))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                when {
                    loading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = palette.accent,
                            )
                            Text(
                                text = "正在读取日志…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.terminalMuted,
                            )
                        }
                    }

                    error != null -> {
                        EmptyTerminalState(
                            title = "读取失败",
                            detail = error,
                            palette = palette,
                        )
                    }

                    lines.isEmpty() -> {
                        EmptyTerminalState(
                            title = emptyTitle,
                            detail = "$emptyDetail 当前显示上限 $limit 行。",
                            palette = palette,
                        )
                    }

                    else -> {
                        SelectionContainer {
                            Text(
                                text = annotatedText,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = DanmuMonoFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.1.sp,
                                ),
                                color = palette.terminalText,
                            )
                        }
                    }
                }

                if (pendingLines > 0 && !followTail && lines.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp),
                        onClick = {
                            scope.launch {
                                followTail = true
                                pendingLines = 0
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = palette.accent,
                        shadowElevation = 0.dp,
                    ) {
                        Text(
                            text = "新增 $pendingLines 条",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTerminalState(
    title: String,
    detail: String,
    palette: ConsolePalette,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = palette.terminalText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.terminalMuted,
        )
    }
}

@Composable
private fun RequestsWorkbench(
    viewModel: ManagerViewModel,
    palette: ConsolePalette,
    expandedRequestKey: String?,
    onExpandedRequestChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = palette.panel.copy(alpha = 0.9f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, palette.panelBorder),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "最近请求",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "今日 ${viewModel.todayReqNum} 次 · 当前 ${viewModel.requestRecords.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.subtleText,
                    )
                }
                ToolbarIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "刷新请求记录",
                    palette = palette,
                    onClick = onRefresh,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.panelStrong,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, palette.panelBorder),
            shadowElevation = 0.dp,
        ) {
            when {
                viewModel.requestRecordsLoading && viewModel.requestRecords.isEmpty() -> {
                    CenteredRequestState("正在加载请求记录…", palette)
                }

                viewModel.requestRecordsError != null -> {
                    CenteredRequestState(viewModel.requestRecordsError.orEmpty(), palette)
                }

                viewModel.requestRecords.isEmpty() -> {
                    CenteredRequestState("服务还没有收到请求，稍后再看这里。", palette)
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        viewModel.requestRecords.forEach { record ->
                            val requestKey = "${record.timestamp}|${record.method}|${record.path}"
                            RequestRow(
                                record = record,
                                expanded = expandedRequestKey == requestKey,
                                palette = palette,
                                onToggle = { onExpandedRequestChange(requestKey) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredRequestState(
    text: String,
    palette: ConsolePalette,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.subtleText,
        )
    }
}

@Composable
private fun RequestRow(
    record: RequestRecord,
    expanded: Boolean,
    palette: ConsolePalette,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        color = palette.panel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = record.path.ifBlank { "/" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "${compactTimestamp(record.timestamp)} · ${record.method} · ${record.clientIp.ifBlank { "--" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.subtleText,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = palette.subtleText,
                )
            }

            if (expanded) {
                HorizontalDivider(color = palette.panelBorder.copy(alpha = 0.8f))
                RequestMetaLine(label = "方法", value = record.method, palette = palette)
                RequestMetaLine(label = "时间", value = record.timestamp, palette = palette)
                RequestMetaLine(label = "来源 IP", value = record.clientIp.ifBlank { "--" }, palette = palette)
                record.params?.takeIf { it.isNotBlank() }?.let { params ->
                    RequestParamBlock(
                        text = params,
                        palette = palette,
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestMetaLine(
    label: String,
    value: String,
    palette: ConsolePalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.subtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RequestParamBlock(
    text: String,
    palette: ConsolePalette,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = palette.accentSoft.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 0.dp,
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = DanmuMonoFamily,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ModuleFileSheet(
    files: List<LogFileEntry>,
    selectedPath: String?,
    palette: ConsolePalette,
    onSelect: (LogFileEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "切换模块日志文件",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "这里只保留文件切换，不把文件列表常驻放在主页面里。",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.subtleText,
        )

        if (files.isEmpty()) {
            Text(
                text = "当前没有可用的模块日志文件。",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.subtleText,
            )
        } else {
            files.forEach { file ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(file) },
                    color = if (selectedPath == file.path) palette.accentSoft else palette.panel,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, palette.panelBorder),
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "${file.modifiedAt ?: "--"} · ${file.sizeBytes} B",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.subtleText,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

private fun buildServiceLogLines(
    serverLogs: List<ServerLogEntry>,
    filter: ConsoleLogFilter,
    limit: Int,
): List<RenderedLogLine> {
    return serverLogs
        .map { entry ->
            val tone = toneForLevel(entry.level)
            RenderedLogLine(
                text = "${compactTimestamp(entry.timestamp)} ${entry.level.ifBlank { "INFO" }.padEnd(5)} ${entry.message}",
                tone = tone,
            )
        }
        .filter { line -> matchesFilter(line.tone, filter) }
        .takeLast(limit)
}

private fun buildModuleLogLines(
    text: String,
    filter: ConsoleLogFilter,
): List<RenderedLogLine> {
    return text.lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .map { line ->
            RenderedLogLine(
                text = line,
                tone = inferLineTone(line),
            )
        }
        .filter { line -> matchesFilter(line.tone, filter) }
        .toList()
}

private fun filterLogLines(
    lines: List<RenderedLogLine>,
    query: String,
): List<RenderedLogLine> {
    val keyword = query.trim()
    if (keyword.isBlank()) return lines
    return lines.filter { line -> line.text.contains(keyword, ignoreCase = true) }
}

private fun buildLogExportFileName(
    source: ConsoleLogSource,
    filter: ConsoleLogFilter,
    query: String,
): String {
    val sourceToken = source.name.lowercase()
    val filterToken = filter.name.lowercase()
    val querySuffix = query.trim()
        .takeIf { it.isNotBlank() }
        ?.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        ?.trim('_')
        ?.take(24)
        ?.takeIf { it.isNotBlank() }
        ?.let { "_$it" }
        .orEmpty()
    return "danmu_${sourceToken}_${filterToken}${querySuffix}.log"
}

private fun buildAnnotatedLogText(
    lines: List<RenderedLogLine>,
    palette: ConsolePalette,
) = buildAnnotatedString {
    lines.forEachIndexed { index, line ->
        val color = when (line.tone) {
            RenderedLogTone.Default -> palette.terminalText
            RenderedLogTone.Error -> palette.terminalError
            RenderedLogTone.Warning -> palette.terminalWarning
            RenderedLogTone.Accent -> palette.accent.copy(alpha = 0.92f)
        }
        pushStyle(SpanStyle(color = color))
        append(line.text)
        pop()
        if (index != lines.lastIndex) append('\n')
    }
}

private fun toneForLevel(level: String): RenderedLogTone {
    return when (level.trim().uppercase()) {
        "ERROR", "ERR", "FATAL" -> RenderedLogTone.Error
        "WARN", "WARNING" -> RenderedLogTone.Warning
        "INFO", "DEBUG", "TRACE" -> RenderedLogTone.Default
        else -> RenderedLogTone.Accent
    }
}

private fun inferLineTone(line: String): RenderedLogTone {
    val upper = line.uppercase()
    return when {
        "ERROR" in upper || "ERR " in upper || " FATAL" in upper -> RenderedLogTone.Error
        "WARN" in upper || "WARNING" in upper -> RenderedLogTone.Warning
        else -> RenderedLogTone.Default
    }
}

private fun matchesFilter(
    tone: RenderedLogTone,
    filter: ConsoleLogFilter,
): Boolean {
    return when (filter) {
        ConsoleLogFilter.All -> true
        ConsoleLogFilter.Error -> tone == RenderedLogTone.Error
        ConsoleLogFilter.Warning -> tone == RenderedLogTone.Warning
    }
}

private fun compactTimestamp(raw: String): String {
    val value = raw.trim()
    if (value.length >= 19 && value[4] == '-' && value[7] == '-') {
        return value.substring(5, 19).replace('T', ' ')
    }
    return value
}

@Composable
private fun rememberConsolePalette(): ConsolePalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(dark) {
        if (dark) {
            ConsolePalette(
                backdropTop = Color(0xFF0F1721),
                backdropMid = Color(0xFF131B26),
                backdropBottom = Color(0xFF10151A),
                haloPrimary = Color(0xFF29496E).copy(alpha = 0.28f),
                haloSecondary = Color(0xFF162D47).copy(alpha = 0.36f),
                panel = Color(0xFF1A2430).copy(alpha = 0.94f),
                panelStrong = Color(0xFF1D2732).copy(alpha = 0.98f),
                panelBorder = Color(0xFF2A3442),
                subtleText = Color(0xFFAFBBC9),
                accent = Color(0xFF5F83A8),
                accentSoft = Color(0xFF203244),
                warning = Color(0xFFE1B35C),
                warningSoft = Color(0xFF3C311B),
                danger = Color(0xFFF0A290),
                dangerSoft = Color(0xFF3A2824),
                terminal = Color(0xFF0F151C),
                terminalBorder = Color(0xFF202C39),
                terminalText = Color(0xFFE8EDF4),
                terminalMuted = Color(0xFF8D9AA8),
                terminalError = Color(0xFFFF9A8A),
                terminalWarning = Color(0xFFF3C76F),
            )
        } else {
            ConsolePalette(
                backdropTop = Color(0xFFE9F0F8),
                backdropMid = Color(0xFFF6F9FC),
                backdropBottom = Color(0xFFF2F5F8),
                haloPrimary = Color(0xFFBDD2EA).copy(alpha = 0.48f),
                haloSecondary = Color(0xFFD8E5F3).copy(alpha = 0.74f),
                panel = Color(0xFFFBFDFF).copy(alpha = 0.9f),
                panelStrong = Color(0xFFFFFFFF).copy(alpha = 0.96f),
                panelBorder = Color(0xFFD9E3ED),
                subtleText = Color(0xFF687588),
                accent = Color(0xFF5F83A8),
                accentSoft = Color(0xFFE7EFF8),
                warning = Color(0xFFB97917),
                warningSoft = Color(0xFFFFF0D8),
                danger = Color(0xFFD86F5A),
                dangerSoft = Color(0xFFF8E8E3),
                terminal = Color(0xFF121A23),
                terminalBorder = Color(0xFF273445),
                terminalText = Color(0xFFEAF0F7),
                terminalMuted = Color(0xFF9AA8B7),
                terminalError = Color(0xFFFFA08D),
                terminalWarning = Color(0xFFF4CA76),
            )
        }
    }
}
