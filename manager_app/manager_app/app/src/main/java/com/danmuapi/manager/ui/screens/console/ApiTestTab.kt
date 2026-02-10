@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.danmuapi.manager.ui.screens.console

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.network.HttpResult
import com.danmuapi.manager.ui.screens.console.components.ConsoleCard
import com.danmuapi.manager.ui.screens.console.components.MethodBadge
import com.danmuapi.manager.ui.screens.console.components.ApiEndpoint
import com.danmuapi.manager.ui.screens.console.components.ApiParam
import com.danmuapi.manager.ui.screens.console.components.humanBytes
import com.danmuapi.manager.ui.screens.console.components.prettifyIfJson
import com.danmuapi.manager.ui.screens.console.components.suggestApiExportFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val endpoints = listOf(
    ApiEndpoint(
        key = "searchAnime", name = "搜索动漫", icon = "🔍", method = "GET",
        path = "/api/v2/search/anime", description = "根据关键词搜索动漫（关键词也可为播放链接URL）",
        params = listOf(ApiParam("keyword", "关键词/播放链接URL", "text", true, "例如：生万物 或 http://v.qq.com/…"))
    ),
    ApiEndpoint(
        key = "searchEpisodes", name = "搜索剧集", icon = "📺", method = "GET",
        path = "/api/v2/search/episodes", description = "搜索指定动漫的剧集列表",
        params = listOf(
            ApiParam("anime", "动漫名称", "text", true, "例如：生万物"),
            ApiParam("episode", "集数过滤", "text", false, "可选：纯数字 / movie"),
        )
    ),
    ApiEndpoint(
        key = "matchAnime", name = "匹配动漫", icon = "🎯", method = "POST",
        path = "/api/v2/match", description = "根据文件名智能匹配动漫",
        params = listOf(ApiParam("fileName", "文件名", "text", true, "例如：生万物 S02E08")),
        hasBody = true, bodyTemplate = "{\n  \"fileName\": \"\"\n}"
    ),
    ApiEndpoint(
        key = "getBangumi", name = "获取番剧详情", icon = "📋", method = "GET",
        path = "/api/v2/bangumi/:animeId", description = "获取指定番剧的详细信息",
        params = listOf(ApiParam("animeId", "动漫ID", "text", true, "例如：236379"))
    ),
    ApiEndpoint(
        key = "getComment", name = "获取弹幕", icon = "💬", method = "GET",
        path = "/api/v2/comment/:commentId", description = "获取指定剧集的弹幕数据",
        params = listOf(
            ApiParam("commentId", "弹幕ID", "text", true, "例如：10009"),
            ApiParam("format", "格式", "select", false, options = listOf("json", "xml"), default = "json"),
            ApiParam("segmentflag", "分片标志", "select", false, options = listOf("true", "false")),
        )
    ),
    ApiEndpoint(
        key = "getCommentByUrl", name = "通过URL获取弹幕", icon = "🔗", method = "GET",
        path = "/api/v2/comment", description = "通过视频URL直接获取弹幕",
        params = listOf(
            ApiParam("url", "视频URL", "text", true, "例如：https://example.com/video.mp4"),
            ApiParam("format", "格式", "select", false, options = listOf("json", "xml"), default = "json"),
        )
    ),
    ApiEndpoint(
        key = "getSegmentComment", name = "获取分片弹幕", icon = "🧩", method = "POST",
        path = "/api/v2/segmentcomment", description = "通过请求体获取分片弹幕",
        params = listOf(ApiParam("format", "格式", "select", false, options = listOf("json", "xml"), default = "json")),
        hasBody = true,
        bodyTemplate = "{\n  \"url\": \"\",\n  \"platform\": \"qq\",\n  \"cid\": \"\",\n  \"start\": 0,\n  \"duration\": 600\n}"
    ),
)

@Composable
fun ApiTestTabContent(
    serviceRunning: Boolean,
    adminToken: String,
    requestApi: suspend (
        method: String, path: String, query: Map<String, String?>,
        bodyJson: String?, useAdminToken: Boolean,
    ) -> HttpResult
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val maxClipboardBytes = 500_000

    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var pendingExportName by remember { mutableStateOf("danmu-api.txt") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            val content = pendingExportText ?: return@rememberLauncherForActivityResult
            if (uri == null) return@rememberLauncherForActivityResult
            val name = pendingExportName
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已导出：$name", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导出失败：${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    fun copyToClipboardSafe(label: String, text: String) {
        val size = text.toByteArray(Charsets.UTF_8).size
        if (size > maxClipboardBytes) {
            Toast.makeText(context, "内容过大（约 ${humanBytes(size.toLong())}），建议导出为文件", Toast.LENGTH_LONG).show()
            return
        }
        try {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "已复制：$label", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(context, "复制失败：${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Endpoint selector state ──
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf(endpoints.first().key) }
    val selected = endpoints.first { it.key == selectedKey }

    val paramState = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(selectedKey) {
        paramState.clear()
        selected.params.forEach { p -> if (p.default != null) paramState[p.name] = p.default }
    }

    var bodyText by remember { mutableStateOf(selected.bodyTemplate.orEmpty()) }
    LaunchedEffect(selectedKey) { bodyText = selected.bodyTemplate.orEmpty() }

    var responseRaw by remember { mutableStateOf("") }
    var responsePreview by remember { mutableStateOf("") }
    var responseHint by remember { mutableStateOf<String?>(null) }
    var responseMeta by remember { mutableStateOf("") }
    var responseContentType by remember { mutableStateOf<String?>(null) }
    var responseTruncatedByClient by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmCopyFull by remember { mutableStateOf(false) }
    var useAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(adminToken) { if (adminToken.isBlank()) useAdmin = false }
    fun send() {
        if (!serviceRunning) { error = "服务未运行"; return }
        error = null; loading = true
        responseRaw = ""; responsePreview = ""; responseHint = null
        responseTruncatedByClient = false; responseMeta = ""; responseContentType = null

        scope.launch {
            val method = selected.method
            var path = selected.path
            Regex(":([A-Za-z0-9_]+)").findAll(path).forEach { m ->
                val key = m.groupValues.getOrNull(1).orEmpty()
                path = path.replace(":$key", paramState[key].orEmpty().trim())
            }
            val query = mutableMapOf<String, String?>()
            selected.params.filterNot { selected.path.contains(":${it.name}") }.forEach { p ->
                val v = paramState[p.name]; if (!v.isNullOrBlank()) query[p.name] = v
            }
            val body = if (selected.hasBody) {
                if (selected.key == "matchAnime") {
                    val fn = paramState["fileName"].orEmpty().trim()
                    if (fn.isNotBlank()) JSONObject().apply { put("fileName", fn) }.toString() else bodyText
                } else bodyText
            } else null

            val result = requestApi(method, path, query, body, useAdmin && adminToken.isNotBlank())
            loading = false

            if (result.isSuccessful) {
                responseRaw = result.body
                responseTruncatedByClient = result.truncated
                val sizeInfo = result.bodyBytesKept.takeIf { it > 0L }?.let { " · ${humanBytes(it)}" }.orEmpty()
                val ctInfo = result.contentType?.let { " · $it" }.orEmpty()
                val truncInfo = if (result.truncated) " · 已截断" else ""
                responseMeta = "HTTP ${result.code} · ${result.durationMs}ms$ctInfo$sizeInfo$truncInfo"
                responseContentType = result.contentType
                val pretty = if (!result.truncated) prettifyIfJson(result.body, maxChars = 160_000) else result.body
                val previewMax = 60_000
                responsePreview = if (pretty.length > previewMax) {
                    responseHint = "响应较大：仅预览前 ${previewMax} 字符（建议导出保存完整内容）。"
                    pretty.take(previewMax) + "\n\n…（预览已截断）"
                } else {
                    responseHint = if (result.truncated) "响应过大：已被客户端限制读取约 ${humanBytes(result.bodyBytesKept)}。" else null
                    pretty
                }
            } else {
                responseMeta = "HTTP ${result.code} · ${result.durationMs}ms"
                error = result.error ?: "请求失败"
                responseContentType = result.contentType
                responseRaw = result.body
                responsePreview = if (result.body.length > 60_000) result.body.take(60_000) + "\n\n…（预览已截断）" else result.body
                responseTruncatedByClient = result.truncated
                if (result.truncated) responseHint = "错误响应过大：已被客户端截断读取。"
            }
        }
    }

    // ── Copy-full confirmation dialog ──
    if (confirmCopyFull) {
        AlertDialog(
            onDismissRequest = { confirmCopyFull = false },
            title = { Text("响应较大") },
            text = {
                val size = responseRaw.toByteArray(Charsets.UTF_8).size.toLong()
                Text("当前已读取内容约 ${humanBytes(size)}。\n\n由于系统剪贴板有大小限制，复制完整内容可能导致闪退。\n建议：导出为文件，或仅复制预览。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCopyFull = false
                    val name = suggestApiExportFileName(selected, paramState.toMap(), responseContentType, responseTruncatedByClient)
                    pendingExportName = name; pendingExportText = responseRaw; exportLauncher.launch(name)
                }) { Text("导出") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirmCopyFull = false }) { Text("取消") }
                    TextButton(onClick = { confirmCopyFull = false; copyToClipboardSafe("预览", responsePreview) }) { Text("复制预览") }
                }
            }
        )
    }
    // ── Main UI ──
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Text("接口调试", style = MaterialTheme.typography.titleMedium)
            if (!serviceRunning) {
                Text("服务未运行，无法请求接口。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Endpoint selector (dropdown instead of card list)
        item {
            ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = it }) {
                OutlinedTextField(
                    value = "${selected.icon} ${selected.name}  ${selected.method} ${selected.path}",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("选择接口") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                )
                ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                    endpoints.forEach { ep ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(ep.icon)
                                    Column {
                                        Text(ep.name, style = MaterialTheme.typography.bodyMedium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            MethodBadge(method = ep.method)
                                            Text(ep.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            },
                            onClick = { selectedKey = ep.key; dropdownExpanded = false }
                        )
                    }
                }
            }
        }

        // Description
        if (selected.description.isNotBlank()) {
            item {
                Text(selected.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Parameters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.params.forEach { p ->
                    when (p.type) {
                        "select" -> {
                            Text(p.label, style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                p.options.forEach { opt ->
                                    FilterChip(
                                        selected = (paramState[p.name] ?: p.default) == opt,
                                        onClick = { paramState[p.name] = opt },
                                        label = { Text(opt) }
                                    )
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = paramState[p.name].orEmpty(),
                                onValueChange = { paramState[p.name] = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(p.label + if (p.required) " *" else "") },
                                placeholder = { if (p.placeholder.isNotBlank()) Text(p.placeholder) },
                            )
                        }
                    }
                }

                if (selected.hasBody) {
                    OutlinedTextField(
                        value = bodyText, onValueChange = { bodyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("请求体 (JSON)") }, minLines = 4, maxLines = 10,
                    )
                }
            }
        }
        // Actions row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("使用管理员 Token", style = MaterialTheme.typography.bodySmall)
                Switch(checked = useAdmin, onCheckedChange = { useAdmin = it }, enabled = adminToken.isNotBlank())
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { send() }, enabled = !loading && serviceRunning) {
                    if (loading) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("发送")
                }
                OutlinedButton(onClick = { copyToClipboardSafe("预览", responsePreview) }, enabled = responsePreview.isNotBlank()) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("复制预览")
                }
                if (responseRaw.isNotBlank()) {
                    OutlinedButton(onClick = {
                        val name = suggestApiExportFileName(selected, paramState.toMap(), responseContentType, responseTruncatedByClient)
                        pendingExportName = name; pendingExportText = responseRaw; exportLauncher.launch(name)
                    }, enabled = responseRaw.isNotBlank()) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("导出")
                    }
                    OutlinedButton(onClick = {
                        val bytes = responseRaw.toByteArray(Charsets.UTF_8).size
                        if (bytes > maxClipboardBytes || responseTruncatedByClient) confirmCopyFull = true
                        else copyToClipboardSafe("完整", responseRaw)
                    }, enabled = responseRaw.isNotBlank()) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("复制完整")
                    }
                }
            }
        }

        // Response
        item {
            ConsoleCard {
                Text("响应结果", style = MaterialTheme.typography.titleSmall)
                if (error != null) Text("错误：$error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (responseMeta.isNotBlank()) Text(responseMeta, style = MaterialTheme.typography.labelMedium)
                if (responseHint != null) Text(responseHint!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (responsePreview.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        val scroll = rememberScrollState()
                        SelectionContainer {
                            Text(
                                responsePreview,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 480.dp).verticalScroll(scroll).padding(10.dp),
                                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                } else {
                    Text("暂无响应", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
