@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danmuapi.manager.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.data.network.HttpResult
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.model.ApiDebugFieldDefinition
import com.danmuapi.manager.core.model.ApiDebugFieldLocation
import com.danmuapi.manager.core.model.ApiDebugFieldType
import com.danmuapi.manager.core.model.ApiDebugPreset
import com.danmuapi.manager.core.model.ApiDebugPresetCatalog
import com.danmuapi.manager.core.model.ApiTestAnimeItem
import com.danmuapi.manager.core.model.ApiTestEpisodeItem
import com.danmuapi.manager.core.model.DanmuTestResult
import com.danmuapi.manager.core.model.EnvVarItem
import com.danmuapi.manager.core.model.ManualDanmuStep
import kotlinx.coroutines.launch

private enum class ApiWorkbenchTab(val label: String) {
    Debug("接口调试"),
    Danmu("弹幕测试"),
}

private enum class DanmuWorkbenchTab(val label: String) {
    Auto("自动匹配"),
    Manual("手动匹配"),
}

@Composable
fun ApiDebugScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var selectedWorkbench by rememberSaveable { mutableStateOf(ApiWorkbenchTab.Debug.name) }
    var selectedDanmuWorkbench by rememberSaveable { mutableStateOf(DanmuWorkbenchTab.Auto.name) }
    var selectedPresetKey by rememberSaveable { mutableStateOf(ApiDebugPresetCatalog.first().key) }
    val selectedPreset = remember(selectedPresetKey) {
        ApiDebugPresetCatalog.firstOrNull { it.key == selectedPresetKey } ?: ApiDebugPresetCatalog.first()
    }
    var presetValues by remember(selectedPreset.key) { mutableStateOf(defaultApiPresetValues(selectedPreset)) }
    var autoFileName by rememberSaveable { mutableStateOf("") }
    var manualKeyword by rememberSaveable { mutableStateOf("") }

    val activeLoadingMessage = when {
        selectedWorkbench == ApiWorkbenchTab.Debug.name && viewModel.apiDebugLoading -> "请求发送中…"
        selectedWorkbench == ApiWorkbenchTab.Danmu.name &&
            selectedDanmuWorkbench == DanmuWorkbenchTab.Auto.name &&
            viewModel.autoDanmuTestLoading -> "正在执行自动匹配并拉取弹幕…"

        selectedWorkbench == ApiWorkbenchTab.Danmu.name &&
            selectedDanmuWorkbench == DanmuWorkbenchTab.Manual.name &&
            viewModel.manualDanmuLoading -> "正在执行手动匹配流程…"

        else -> null
    }

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "API 调试",
            subtitle = "按核心前端的真实调试路径，拆成预设接口与弹幕测试两套工作台。",
            palette = palette,
            leading = {
                SettingsHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    palette = palette,
                    onClick = onBack,
                )
            },
        )

        activeLoadingMessage?.let { message ->
            SettingsBusyStrip(
                message = message,
                palette = palette,
            )
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "工作台",
                subtitle = "接口预设与弹幕流程分开维护，避免同页堆太多无关控件。",
                palette = palette,
            )
            SettingsSegmentedBar(
                options = ApiWorkbenchTab.entries.toList(),
                selected = ApiWorkbenchTab.entries.first { it.name == selectedWorkbench },
                palette = palette,
                onSelect = { selectedWorkbench = it.name },
                label = { it.label },
            )
        }

        when (ApiWorkbenchTab.entries.first { it.name == selectedWorkbench }) {
            ApiWorkbenchTab.Debug -> {
                ApiPresetWorkbench(
                    preset = selectedPreset,
                    fieldValues = presetValues,
                    requestSummary = viewModel.apiDebugRequestSummary,
                    result = viewModel.apiDebugResult,
                    error = viewModel.apiDebugError,
                    loading = viewModel.apiDebugLoading,
                    palette = palette,
                    onPresetSelect = { presetKey ->
                        selectedPresetKey = presetKey
                    },
                    onFieldChange = { key, value ->
                        presetValues = presetValues.toMutableMap().apply { put(key, value) }
                    },
                    onSubmit = {
                        viewModel.runPresetApiDebugRequest(
                            preset = selectedPreset,
                            fieldValues = presetValues,
                        )
                    },
                )
            }

            ApiWorkbenchTab.Danmu -> {
                DanmuTestWorkbench(
                    palette = palette,
                    selectedTab = DanmuWorkbenchTab.entries.first { it.name == selectedDanmuWorkbench },
                    autoFileName = autoFileName,
                    manualKeyword = manualKeyword,
                    autoResult = viewModel.autoDanmuTestResult,
                    autoError = viewModel.autoDanmuTestError,
                    autoLoading = viewModel.autoDanmuTestLoading,
                    manualStep = viewModel.manualDanmuStep,
                    manualAnimeResults = viewModel.manualDanmuSearchResults,
                    manualSelectedAnimeTitle = viewModel.manualDanmuSelectedAnimeTitle,
                    manualEpisodes = viewModel.manualDanmuEpisodes,
                    manualResult = viewModel.manualDanmuResult,
                    manualError = viewModel.manualDanmuError,
                    manualLoading = viewModel.manualDanmuLoading,
                    onSelectTab = { selectedDanmuWorkbench = it.name },
                    onAutoFileNameChange = { autoFileName = it },
                    onManualKeywordChange = { manualKeyword = it },
                    onRunAutoTest = { viewModel.runAutoDanmuTest(autoFileName) },
                    onRunManualSearch = { viewModel.searchManualDanmuAnime(manualKeyword) },
                    onSelectAnime = viewModel::loadManualDanmuEpisodes,
                    onSelectEpisode = viewModel::loadManualDanmuResult,
                    onBackToSearch = viewModel::backManualDanmuToSearch,
                    onBackToAnimeResults = viewModel::backManualDanmuToAnimeResults,
                    onBackToEpisodes = viewModel::backManualDanmuToEpisodes,
                )
            }
        }
    }
}

@Composable
private fun ApiPresetWorkbench(
    preset: ApiDebugPreset,
    fieldValues: Map<String, String>,
    requestSummary: String,
    result: HttpResult?,
    error: String?,
    loading: Boolean,
    palette: SettingsPalette,
    onPresetSelect: (String) -> Unit,
    onFieldChange: (String, String) -> Unit,
    onSubmit: () -> Unit,
) {
    val visibleFields = remember(preset, fieldValues) {
        preset.fields.filter { shouldShowApiField(preset, it, fieldValues) }
    }

    SettingsPanel(palette = palette) {
        SettingsPanelHeader(
            title = "预设接口",
            subtitle = "只保留核心前端真的在用的 6 个接口。",
            palette = palette,
            trailing = {
                SettingsTag(
                    text = preset.method,
                    palette = palette,
                    tone = palette.accent,
                    containerColor = palette.accentContainer,
                )
            },
        )
        SettingsSegmentedBar(
            options = ApiDebugPresetCatalog,
            selected = preset,
            palette = palette,
            onSelect = { onPresetSelect(it.key) },
            label = { it.title },
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.cardMuted,
            border = BorderStroke(1.dp, palette.mutedBorder),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.subtleText,
                )
                SettingsCodeBlock(
                    text = "${preset.method} ${renderPresetPreviewPath(preset, fieldValues)}",
                    palette = palette,
                )
            }
        }
    }

    SettingsPanel(palette = palette) {
        SettingsPanelHeader(
            title = "请求参数",
            subtitle = if (visibleFields.isEmpty()) {
                "这个接口没有额外参数，直接发送即可。"
            } else {
                "按接口定义动态收起，只显示当前真正需要的参数。"
            },
            palette = palette,
        )

        visibleFields.forEach { field ->
            ApiDebugFieldEditor(
                field = field,
                value = fieldValues[field.key].orEmpty(),
                palette = palette,
                onValueChange = { onFieldChange(field.key, it) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            FilledTonalButton(
                onClick = onSubmit,
                enabled = !loading,
            ) {
                Text(if (loading) "发送中" else "发送请求")
            }
        }
    }

    ApiDebugResultWorkbench(
        requestSummary = requestSummary,
        result = result,
        error = error,
        palette = palette,
    )
}

@Composable
private fun ApiDebugFieldEditor(
    field: ApiDebugFieldDefinition,
    value: String,
    palette: SettingsPalette,
    onValueChange: (String) -> Unit,
) {
    val label = buildString {
        append(field.label)
        if (field.required) append(" *")
    }

    when (field.type) {
        ApiDebugFieldType.Select -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = value == option,
                            onClick = { onValueChange(if (value == option) "" else option) },
                            label = { Text(option) },
                        )
                    }
                }
                field.placeholder.takeIf { it.isNotBlank() }?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.subtleText,
                    )
                }
            }
        }

        ApiDebugFieldType.Json -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                placeholder = { Text(field.placeholder) },
                minLines = 7,
                maxLines = 12,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
            )
        }

        ApiDebugFieldType.Text -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                placeholder = field.placeholder.takeIf { it.isNotBlank() }?.let { placeholder ->
                    { Text(placeholder) }
                },
                minLines = 1,
                maxLines = if (field.location == ApiDebugFieldLocation.Body) 4 else 1,
                textStyle = if (field.location == ApiDebugFieldLocation.Body) {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            )
        }
    }
}

@Composable
private fun ApiDebugResultWorkbench(
    requestSummary: String,
    result: HttpResult?,
    error: String?,
    palette: SettingsPalette,
) {
    val statusTone = when {
        error != null -> palette.danger
        result == null -> palette.subtleText
        result.isSuccessful -> palette.positive
        else -> palette.warning
    }
    val statusContainer = when {
        error != null -> palette.dangerContainer
        result == null -> palette.chip
        result.isSuccessful -> palette.positiveContainer
        else -> palette.warningContainer
    }
    val statusLabel = when {
        error != null -> "失败"
        result == null -> "待发送"
        result.code > 0 -> "HTTP ${result.code}"
        else -> "网络错误"
    }

    SettingsPanel(palette = palette) {
        SettingsPanelHeader(
            title = "结果工作台",
            subtitle = "请求地址、状态和响应正文集中展示，不再拆成多层卡片。",
            palette = palette,
            trailing = {
                SettingsTag(
                    text = statusLabel,
                    palette = palette,
                    tone = statusTone,
                    containerColor = statusContainer,
                )
            },
        )

        if (requestSummary.isBlank() && result == null && error == null) {
            Text(
                text = "选择一个预设接口并发送请求后，这里会展示完整 URL 与响应结果。",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.subtleText,
            )
            return@SettingsPanel
        }

        if (requestSummary.isNotBlank()) {
            SettingsCodeBlock(
                text = requestSummary,
                palette = palette,
            )
        }

        result?.let { httpResult ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsInfoPill(
                    label = "耗时",
                    value = "${httpResult.durationMs} ms",
                    palette = palette,
                    tone = if (httpResult.isSuccessful) palette.positive else palette.warning,
                )
                SettingsInfoPill(
                    label = "类型",
                    value = httpResult.contentType ?: "--",
                    palette = palette,
                )
                SettingsInfoPill(
                    label = "保留",
                    value = "${httpResult.bodyBytesKept} bytes",
                    palette = palette,
                )
                if (httpResult.truncated) {
                    SettingsInfoPill(
                        label = "正文",
                        value = "已截断",
                        palette = palette,
                        tone = palette.warning,
                    )
                }
            }
        }

        if (error != null) {
            SettingsCodeBlock(
                text = error,
                palette = palette,
            )
        } else if (result != null) {
            SettingsCodeBlock(
                text = result.body.ifBlank { "(empty body)" },
                palette = palette,
            )
        }
    }
}

@Composable
private fun DanmuTestWorkbench(
    palette: SettingsPalette,
    selectedTab: DanmuWorkbenchTab,
    autoFileName: String,
    manualKeyword: String,
    autoResult: DanmuTestResult?,
    autoError: String?,
    autoLoading: Boolean,
    manualStep: ManualDanmuStep,
    manualAnimeResults: List<ApiTestAnimeItem>,
    manualSelectedAnimeTitle: String,
    manualEpisodes: List<ApiTestEpisodeItem>,
    manualResult: DanmuTestResult?,
    manualError: String?,
    manualLoading: Boolean,
    onSelectTab: (DanmuWorkbenchTab) -> Unit,
    onAutoFileNameChange: (String) -> Unit,
    onManualKeywordChange: (String) -> Unit,
    onRunAutoTest: () -> Unit,
    onRunManualSearch: () -> Unit,
    onSelectAnime: (String) -> Unit,
    onSelectEpisode: (String, String) -> Unit,
    onBackToSearch: () -> Unit,
    onBackToAnimeResults: () -> Unit,
    onBackToEpisodes: () -> Unit,
) {
    SettingsPanel(palette = palette) {
        SettingsPanelHeader(
            title = "弹幕测试",
            subtitle = "自动模式走完整匹配链路，手动模式按步骤核对搜索、番剧和剧集。",
            palette = palette,
        )
        SettingsSegmentedBar(
            options = DanmuWorkbenchTab.entries.toList(),
            selected = selectedTab,
            palette = palette,
            onSelect = onSelectTab,
            label = { it.label },
        )
    }

    when (selectedTab) {
        DanmuWorkbenchTab.Auto -> {
            SettingsPanel(palette = palette) {
                SettingsPanelHeader(
                    title = "自动匹配",
                    subtitle = "输入播放器看到的文件名，模拟真实的自动匹配与拉弹幕流程。",
                    palette = palette,
                )
                OutlinedTextField(
                    value = autoFileName,
                    onValueChange = onAutoFileNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("文件名") },
                    placeholder = { Text("示例: 生万物 S02E08 或 无忧渡.S02E08.2160p.WEB-DL") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onRunAutoTest() }),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(
                        onClick = onRunAutoTest,
                        enabled = !autoLoading,
                    ) {
                        Text(if (autoLoading) "匹配中" else "开始匹配")
                    }
                }
                autoError?.takeIf { it.isNotBlank() }?.let { message ->
                    SettingsTag(
                        text = message,
                        palette = palette,
                        tone = palette.warning,
                        containerColor = palette.warningContainer,
                    )
                }
            }

            autoResult?.let { result ->
                DanmuTestResultPanel(
                    result = result,
                    palette = palette,
                )
            }
        }

        DanmuWorkbenchTab.Manual -> {
            SettingsPanel(palette = palette) {
                SettingsPanelHeader(
                    title = "手动匹配",
                    subtitle = "页内按步骤切换：搜索动漫、选择番剧、选择剧集、确认弹幕结果。",
                    palette = palette,
                )
                ManualStepStrip(
                    currentStep = manualStep,
                    palette = palette,
                )

                when (manualStep) {
                    ManualDanmuStep.Search -> {
                        OutlinedTextField(
                            value = manualKeyword,
                            onValueChange = onManualKeywordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("搜索关键字") },
                            placeholder = { Text("请输入动漫名称") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onRunManualSearch() }),
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            FilledTonalButton(
                                onClick = onRunManualSearch,
                                enabled = !manualLoading,
                            ) {
                                Text(if (manualLoading) "搜索中" else "搜索番剧")
                            }
                        }
                    }

                    ManualDanmuStep.Anime -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "选择番剧",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            OutlinedButton(onClick = onBackToSearch) {
                                Text("重新搜索")
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            manualAnimeResults.forEach { anime ->
                                ApiSelectionCard(
                                    title = anime.animeTitle,
                                    subtitle = buildAnimeSubtitle(anime),
                                    badge = anime.episodeCount?.let { "${it} 集" } ?: anime.animeId,
                                    palette = palette,
                                    onClick = { onSelectAnime(anime.animeId) },
                                )
                            }
                        }
                    }

                    ManualDanmuStep.Episodes -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "选择剧集",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = manualSelectedAnimeTitle.ifBlank { "未命名番剧" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.subtleText,
                                )
                            }
                            OutlinedButton(onClick = onBackToAnimeResults) {
                                Text("返回番剧")
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            manualEpisodes.forEach { episode ->
                                ApiSelectionCard(
                                    title = episode.episodeTitle,
                                    subtitle = "弹幕 ID：${episode.episodeId}",
                                    badge = "EP ${episode.episodeNumber}",
                                    palette = palette,
                                    onClick = {
                                        onSelectEpisode(
                                            episode.episodeId,
                                            buildEpisodeSelectionTitle(
                                                animeTitle = manualSelectedAnimeTitle,
                                                episode = episode,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    ManualDanmuStep.Result -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(onClick = onBackToEpisodes) {
                                Text("返回剧集")
                            }
                        }
                        Text(
                            text = "结果页已单独展开，便于连续看统计和弹幕预览。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.subtleText,
                        )
                    }
                }

                manualError?.takeIf { it.isNotBlank() }?.let { message ->
                    SettingsTag(
                        text = message,
                        palette = palette,
                        tone = palette.warning,
                        containerColor = palette.warningContainer,
                    )
                }
            }

            if (manualStep == ManualDanmuStep.Result) {
                manualResult?.let { result ->
                    DanmuTestResultPanel(
                        result = result,
                        palette = palette,
                    )
                }
            }
        }
    }
}

@Composable
private fun DanmuTestResultPanel(
    result: DanmuTestResult,
    palette: SettingsPalette,
) {
    val metrics = listOf(
        "弹幕总量" to result.stats.commentCount.toString(),
        "视频时长" to result.stats.durationLabel,
        "平均密度" to result.stats.averageDensityLabel,
        "高峰区间" to result.stats.hotMomentLabel,
    )

    SettingsPanel(palette = palette) {
        SettingsPanelHeader(
            title = result.title,
            subtitle = "保留统计摘要和弹幕预览，先看结果再决定是否继续深挖。",
            palette = palette,
            trailing = {
                SettingsTag(
                    text = "${result.preview.size} 条预览",
                    palette = palette,
                    tone = palette.accent,
                    containerColor = palette.accentContainer,
                )
            },
        )

        result.matchSummary?.let { summary ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = palette.cardMuted,
                border = BorderStroke(1.dp, palette.mutedBorder),
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = summary.animeTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = summary.episodeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.subtleText,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingsInfoPill(
                            label = "剧集 ID",
                            value = summary.episodeId,
                            palette = palette,
                        )
                        summary.matchCount?.let { count ->
                            SettingsInfoPill(
                                label = "候选数",
                                value = count.toString(),
                                palette = palette,
                                tone = palette.accent,
                            )
                        }
                        SettingsInfoPill(
                            label = "模式分布",
                            value = result.stats.modeBreakdownLabel,
                            palette = palette,
                        )
                    }
                }
            }
        }

        metrics.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { (label, value) ->
                    DanmuMetricCard(
                        label = label,
                        value = value,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Text(
            text = "弹幕预览",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            result.preview.take(20).forEach { item ->
                DanmuPreviewRow(
                    timeLabel = item.timeLabel,
                    modeLabel = item.modeLabel,
                    colorHex = item.colorHex,
                    text = item.text,
                    palette = palette,
                )
            }
        }
        if (result.preview.size > 20) {
            Text(
                text = "仅展示前 20 条预览，保留更高的信息密度。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.subtleText,
            )
        }
    }
}

@Composable
private fun DanmuMetricCard(
    label: String,
    value: String,
    palette: SettingsPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = palette.cardMuted,
        border = BorderStroke(1.dp, palette.mutedBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = palette.subtleText,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun DanmuPreviewRow(
    timeLabel: String,
    modeLabel: String,
    colorHex: String,
    text: String,
    palette: SettingsPalette,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.cardMuted,
        border = BorderStroke(1.dp, palette.mutedBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsTag(
                text = timeLabel,
                palette = palette,
                tone = palette.accent,
                containerColor = palette.accentContainer,
            )
            SettingsTag(
                text = modeLabel,
                palette = palette,
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(color = parsePreviewColor(colorHex), shape = CircleShape),
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ApiSelectionCard(
    title: String,
    subtitle: String,
    badge: String,
    palette: SettingsPalette,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = palette.cardMuted,
        border = BorderStroke(1.dp, palette.mutedBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.subtleText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SettingsTag(
                text = badge,
                palette = palette,
                tone = palette.accent,
                containerColor = palette.accentContainer,
            )
        }
    }
}

@Composable
private fun ManualStepStrip(
    currentStep: ManualDanmuStep,
    palette: SettingsPalette,
) {
    val steps = listOf(
        ManualDanmuStep.Search to "搜索",
        ManualDanmuStep.Anime to "番剧",
        ManualDanmuStep.Episodes to "剧集",
        ManualDanmuStep.Result to "结果",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        steps.forEachIndexed { index, (step, label) ->
            val active = step == currentStep
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (active) palette.accentContainer else palette.cardMuted,
                border = BorderStroke(1.dp, if (active) palette.accent.copy(alpha = 0.22f) else palette.mutedBorder),
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "0${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) palette.accent else palette.subtleText,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (active) palette.accent else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsSegmentedBar(
    options: List<T>,
    selected: T,
    palette: SettingsPalette,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = palette.cardMuted,
        border = BorderStroke(1.dp, palette.mutedBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val active = option == selected
                Surface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (active) palette.accent else Color.Transparent,
                    shadowElevation = 0.dp,
                ) {
                    Text(
                        text = label(option),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun defaultApiPresetValues(preset: ApiDebugPreset): Map<String, String> {
    return preset.fields.associate { field ->
        field.key to when {
            field.type == ApiDebugFieldType.Json -> field.placeholder
            field.type == ApiDebugFieldType.Select -> ""
            else -> ""
        }
    }
}

private fun shouldShowApiField(
    preset: ApiDebugPreset,
    field: ApiDebugFieldDefinition,
    fieldValues: Map<String, String>,
): Boolean {
    if (preset.key == "getComment" && field.key == "duration") {
        val format = fieldValues["format"].orEmpty().lowercase()
        return format.isBlank() || format == "json"
    }
    return true
}

private fun renderPresetPreviewPath(
    preset: ApiDebugPreset,
    fieldValues: Map<String, String>,
): String {
    var resolvedPath = preset.path
    val queryParts = mutableListOf<String>()

    preset.fields.forEach { field ->
        val rawValue = fieldValues[field.key].orEmpty().trim()
        when (field.location) {
            ApiDebugFieldLocation.Path -> {
                resolvedPath = resolvedPath.replace(
                    ":${field.key}",
                    rawValue.ifBlank { "{${field.label}}" },
                )
            }

            ApiDebugFieldLocation.Query -> {
                if (rawValue.isNotBlank()) {
                    queryParts += "${field.key}=$rawValue"
                }
            }

            else -> Unit
        }
    }

    return buildString {
        append(resolvedPath)
        if (queryParts.isNotEmpty()) {
            append("?")
            append(queryParts.joinToString("&"))
        }
    }
}

private fun buildAnimeSubtitle(item: ApiTestAnimeItem): String {
    return buildString {
        append("动漫 ID：")
        append(item.animeId)
        item.episodeCount?.let {
            append(" · 共 ")
            append(it)
            append(" 集")
        }
    }
}

private fun buildEpisodeSelectionTitle(
    animeTitle: String,
    episode: ApiTestEpisodeItem,
): String {
    val baseTitle = episode.episodeTitle.ifBlank { "第${episode.episodeNumber}集" }
    return if (animeTitle.isBlank()) {
        baseTitle
    } else {
        "$animeTitle · $baseTitle"
    }
}

private fun parsePreviewColor(colorHex: String): Color {
    return runCatching {
        val normalized = colorHex.removePrefix("#")
        Color(normalized.toLong(16).toInt() or 0xFF000000.toInt())
    }.getOrElse {
        Color.White
    }
}

@Composable
fun ServerEnvScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val scope = rememberCoroutineScope()
    var tokenCandidate by remember(viewModel.sessionAdminToken) { mutableStateOf(viewModel.sessionAdminToken) }
    var tokenFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var tokenVerifying by rememberSaveable { mutableStateOf(false) }
    var editingEnv by remember { mutableStateOf<EnvVarItem?>(null) }
    var editValue by rememberSaveable { mutableStateOf("") }
    var deletingEnv by remember { mutableStateOf<EnvVarItem?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var expandedEnvKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel.status?.isRunning) {
        if (viewModel.status?.isRunning == true &&
            viewModel.serverConfig == null &&
            !viewModel.serverConfigLoading
        ) {
            viewModel.refreshServerConfig(useAdminToken = viewModel.sessionAdminToken.isNotBlank())
        }
    }

    if (editingEnv != null) {
        val target = editingEnv!!
        AlertDialog(
            onDismissRequest = { editingEnv = null },
            title = { Text("编辑 ${target.key}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    target.description.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (target.options.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            target.options.forEach { option ->
                                androidx.compose.material3.FilterChip(
                                    selected = editValue == option,
                                    onClick = { editValue = option },
                                    label = { Text(option) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("变量值") },
                        minLines = if (target.type == "json" || target.value.length > 60) 3 else 1,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setServerEnvVar(target.key, editValue)
                        editingEnv = null
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEnv = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (deletingEnv != null) {
        val target = deletingEnv!!
        AlertDialog(
            onDismissRequest = { deletingEnv = null },
            title = { Text("删除环境变量") },
            text = { Text("确认删除 ${target.key}？该操作会直接调用服务端接口。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteServerEnvVar(target.key)
                        deletingEnv = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEnv = null }) {
                    Text("取消")
                }
            },
        )
    }

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "服务端环境",
            subtitle = "管理员模式、系统动作和环境变量都压进更清晰的工具页结构。",
            palette = palette,
            leading = {
                SettingsHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    palette = palette,
                    onClick = onBack,
                )
            },
        )

        viewModel.busyMessage?.takeIf { it.isNotBlank() }?.let { message ->
            SettingsBusyStrip(message = message, palette = palette)
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "管理员模式",
                subtitle = "会话权限单独控制，不写入本地。",
                palette = palette,
                trailing = {
                    SettingsTag(
                        text = if (viewModel.sessionAdminToken.isBlank()) "普通模式" else "管理员模式",
                        palette = palette,
                        tone = if (viewModel.sessionAdminToken.isBlank()) palette.subtleText else palette.positive,
                    )
                },
            )
            OutlinedTextField(
                value = tokenCandidate,
                onValueChange = { tokenCandidate = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("会话 ADMIN_TOKEN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        tokenVerifying = true
                        tokenFeedback = null
                        scope.launch {
                            val (ok, error) = viewModel.validateAdminToken(tokenCandidate)
                            if (ok) {
                                viewModel.setSessionAdminToken(tokenCandidate)
                                viewModel.refreshServerConfig(useAdminToken = true)
                                tokenFeedback = "管理员模式已启用"
                            } else {
                                tokenFeedback = error
                            }
                            tokenVerifying = false
                        }
                    },
                    enabled = !tokenVerifying,
                ) {
                    Text(if (tokenVerifying) "验证中" else "验证并启用")
                }
                OutlinedButton(
                    onClick = {
                        tokenCandidate = ""
                        tokenFeedback = "已退出管理员模式"
                        viewModel.clearSessionAdminToken()
                        viewModel.refreshServerConfig(useAdminToken = false)
                    },
                ) {
                    Text("退出管理员模式")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsInfoPill(
                    label = "环境内置",
                    value = if (viewModel.adminToken.isBlank()) "未配置" else "已配置",
                    palette = palette,
                    tone = if (viewModel.adminToken.isBlank()) palette.subtleText else palette.accent,
                )
                SettingsInfoPill(
                    label = "当前会话",
                    value = if (viewModel.sessionAdminToken.isBlank()) "普通" else "管理员",
                    palette = palette,
                    tone = if (viewModel.sessionAdminToken.isBlank()) palette.subtleText else palette.positive,
                )
            }
            tokenFeedback?.takeIf { it.isNotBlank() }?.let { feedback ->
                SettingsTag(
                    text = feedback,
                    palette = palette,
                    tone = if (feedback.contains("已")) palette.positive else palette.warning,
                    containerColor = if (feedback.contains("已")) palette.positiveContainer else palette.warningContainer,
                )
            }
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "系统动作",
                subtitle = "高风险操作统一收敛，不再和变量列表混排。",
                palette = palette,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.refreshServerConfig(useAdminToken = viewModel.sessionAdminToken.isNotBlank())
                    },
                ) {
                    Text("刷新配置")
                }
                OutlinedButton(onClick = viewModel::clearServerCache) {
                    Text("清理缓存")
                }
                OutlinedButton(onClick = viewModel::deployServer) {
                    Text("重新部署")
                }
            }
            viewModel.serverConfig?.let { config ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsInfoPill(
                        label = "版本",
                        value = config.version ?: "--",
                        palette = palette,
                        tone = palette.accent,
                    )
                    SettingsInfoPill(
                        label = "仓库",
                        value = config.repository ?: "--",
                        palette = palette,
                    )
                    SettingsInfoPill(
                        label = "分类",
                        value = config.categorizedEnvVars.size.toString(),
                        palette = palette,
                    )
                }
                config.notice?.takeIf { it.isNotBlank() }?.let { notice ->
                    ServerNoticeStrip(
                        notice = notice,
                        palette = palette,
                    )
                }
            }
        }

        when {
            viewModel.serverConfigLoading && viewModel.serverConfig == null -> {
                SettingsPanel(palette = palette) {
                    SettingsPanelHeader(
                        title = "读取配置中",
                        subtitle = "正在请求服务端环境配置。",
                        palette = palette,
                    )
                }
            }

            viewModel.serverConfigError != null -> {
                SettingsPanel(palette = palette) {
                    SettingsPanelHeader(
                        title = "读取配置失败",
                        subtitle = viewModel.serverConfigError.orEmpty(),
                        palette = palette,
                        trailing = {
                            SettingsTag(
                                text = "失败",
                                palette = palette,
                                tone = palette.danger,
                                containerColor = palette.dangerContainer,
                            )
                        },
                    )
                }
            }

            viewModel.serverConfig == null -> {
                SettingsPanel(palette = palette) {
                    SettingsPanelHeader(
                        title = "暂无系统配置",
                        subtitle = "先刷新配置，或确认服务已经运行。",
                        palette = palette,
                    )
                }
            }

            else -> {
                val config = viewModel.serverConfig!!
                val categories = remember(config.categorizedEnvVars) {
                    config.categorizedEnvVars.entries.map { it.key to it.value }
                }
                val categoryNames = remember(categories) { categories.map { it.first } }

                LaunchedEffect(categoryNames.joinToString(separator = "|")) {
                    if (selectedCategory !in categoryNames) {
                        selectedCategory = categoryNames.firstOrNull().orEmpty()
                    }
                    if (expandedEnvKey != null &&
                        categories.none { (_, items) -> items.any { it.key == expandedEnvKey } }
                    ) {
                        expandedEnvKey = null
                    }
                }

                val currentItems = config.categorizedEnvVars[selectedCategory].orEmpty()

                SettingsPanel(palette = palette) {
                    SettingsPanelHeader(
                        title = "环境变量",
                        subtitle = "按分类切换，只维护当前分组。",
                        palette = palette,
                        trailing = {
                            Text(
                                text = if (selectedCategory.isBlank()) "0 项" else "${currentItems.size} 项",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.subtleText,
                            )
                        },
                    )
                    if (categories.isEmpty()) {
                        Text(
                            text = "当前没有可维护的环境变量分类。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.subtleText,
                        )
                    } else {
                        EnvCategorySwitcher(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            palette = palette,
                            onSelect = { category ->
                                selectedCategory = category
                                expandedEnvKey = null
                            },
                        )
                        if (currentItems.isEmpty()) {
                            Text(
                                text = "这个分类当前没有可维护变量。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.subtleText,
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                currentItems.forEach { item ->
                                    EnvVarWorkbenchCard(
                                        item = item,
                                        expanded = expandedEnvKey == item.key,
                                        palette = palette,
                                        onToggle = {
                                            expandedEnvKey = if (expandedEnvKey == item.key) null else item.key
                                        },
                                        onEdit = {
                                            editingEnv = item
                                            editValue = item.value
                                        },
                                        onDelete = { deletingEnv = item },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerNoticeStrip(
    notice: String,
    palette: SettingsPalette,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = palette.cardMuted,
        border = BorderStroke(1.dp, palette.mutedBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "服务通知",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = palette.subtleText,
            )
        }
    }
}

@Composable
private fun EnvCategorySwitcher(
    categories: List<Pair<String, List<EnvVarItem>>>,
    selectedCategory: String,
    palette: SettingsPalette,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { (category, items) ->
            val selected = category == selectedCategory
            Surface(
                onClick = { onSelect(category) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) palette.accentContainer else palette.card,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) palette.accent.copy(alpha = 0.18f) else palette.cardBorder,
                ),
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = category.ifBlank { "未分类" },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) palette.accent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = items.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.accent else palette.subtleText,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvVarWorkbenchCard(
    item: EnvVarItem,
    expanded: Boolean,
    palette: SettingsPalette,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = if (expanded) palette.card else palette.cardMuted,
        border = BorderStroke(1.dp, if (expanded) palette.cardBorder else palette.mutedBorder),
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
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.key,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildEnvValueSummary(item.value),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    buildEnvMetaSummary(item)?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.subtleText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = palette.subtleText,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsDivider(palette)
                    item.type.takeIf { it.isNotBlank() && it != "text" }?.let { type ->
                        SettingsValueRow(
                            label = "类型",
                            value = type.uppercase(),
                            palette = palette,
                        )
                    }
                    EnvValuePreview(
                        value = item.value,
                        palette = palette,
                        maxLines = 8,
                    )
                    if (item.options.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item.options.forEach { option ->
                                SettingsTag(
                                    text = option,
                                    palette = palette,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onEdit) {
                            Text("编辑")
                        }
                        TextButton(onClick = onDelete) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvValuePreview(
    value: String,
    palette: SettingsPalette,
    maxLines: Int = 4,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = palette.cardMuted,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = value.ifBlank { "(empty)" },
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun buildEnvValueSummary(value: String): String {
    val normalized = value
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    return when {
        normalized.isBlank() -> "未设置"
        normalized.length <= 72 -> normalized
        else -> normalized.take(72).trimEnd() + "…"
    }
}

private fun buildEnvMetaSummary(item: EnvVarItem): String? {
    val parts = buildList {
        item.description.takeIf { it.isNotBlank() }?.let(::add)
        if (item.options.isNotEmpty()) {
            add("${item.options.size} 个可选值")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}
