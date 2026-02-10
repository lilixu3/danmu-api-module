@file:OptIn(ExperimentalLayoutApi::class)

package com.danmuapi.manager.ui.screens.console

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.EnvVarMeta
import com.danmuapi.manager.data.model.ServerConfigResponse
import com.danmuapi.manager.ui.screens.console.components.ConsoleCard
import com.danmuapi.manager.ui.screens.console.components.categoryLabel
import com.danmuapi.manager.ui.screens.console.components.shouldMaskInPreview
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SystemTabContent(
    rootAvailable: Boolean?,
    serviceRunning: Boolean,
    adminTokenFromEnv: String,
    sessionAdminToken: String,
    onSetSessionAdminToken: (String) -> Unit,
    onClearSessionAdminToken: () -> Unit,
    serverConfig: ServerConfigResponse?,
    serverConfigLoading: Boolean,
    serverConfigError: String?,
    onRefreshConfig: (useAdminToken: Boolean) -> Unit,
    onSetEnv: (key: String, value: String) -> Unit,
    onDeleteEnv: (key: String) -> Unit,
    onClearCache: () -> Unit,
    onDeploy: () -> Unit,
    validateAdminToken: suspend (token: String) -> Pair<Boolean, String?>
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasAdminTokenOnServer: Boolean? = serverConfig?.hasAdminToken
    val adminTokenConfigured: Boolean = hasAdminTokenOnServer ?: adminTokenFromEnv.isNotBlank()

    var mode by remember { mutableIntStateOf(if (sessionAdminToken.isNotBlank()) 1 else 0) }
    val hasSessionAdmin = sessionAdminToken.isNotBlank()
    val isAdminModeSelected = mode == 1
    val canAdminOps = serviceRunning && isAdminModeSelected && hasSessionAdmin
    val canEdit = canAdminOps

    var validatingAdmin by remember { mutableStateOf(false) }
    var adminAuthError by remember { mutableStateOf<String?>(null) }
    var setupAdminToken by remember { mutableStateOf("") }
    var revealSetupToken by remember { mutableStateOf(false) }
    var tokenInput by remember(isAdminModeSelected) { mutableStateOf("") }
    var revealToken by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        if (mode == 0 && hasSessionAdmin) { onClearSessionAdminToken(); adminAuthError = null; validatingAdmin = false }
    }
    LaunchedEffect(hasAdminTokenOnServer) {
        if (hasAdminTokenOnServer == false && hasSessionAdmin) { onClearSessionAdminToken(); mode = 0; adminAuthError = null; validatingAdmin = false }
    }

    fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    fun enterAdminMode() {
        val v = tokenInput.trim()
        if (v.isBlank()) { toast("请填写 ADMIN_TOKEN"); return }
        if (!serviceRunning) { toast("服务未运行"); return }
        if (!adminTokenConfigured) { toast("未配置 ADMIN_TOKEN"); return }
        if (validatingAdmin) return
        validatingAdmin = true; adminAuthError = null
        scope.launch {
            val (ok, err) = try { validateAdminToken(v) } catch (t: Throwable) { false to (t.message ?: "验证失败") }
            validatingAdmin = false
            if (ok) { onSetSessionAdminToken(v); toast("已进入管理员模式"); tokenInput = ""; revealToken = false; adminAuthError = null }
            else { adminAuthError = err ?: "ADMIN_TOKEN 输入错误"; toast(adminAuthError!!) }
        }
    }

    var search by remember { mutableStateOf("") }
    var confirmDeleteKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serviceRunning, canAdminOps) { if (serviceRunning) onRefreshConfig(canAdminOps) }

    val meta = serverConfig?.envVarConfig.orEmpty()
    val original = serverConfig?.originalEnvVars.orEmpty()
    val categories = serverConfig?.categorizedEnvVars.orEmpty()
    val effectiveByKey = remember(categories) { categories.values.flatten().associate { it.key to it.value } }
    val edits = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(canEdit) { if (!canEdit) edits.clear() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    fun baseline(key: String): String = original[key] ?: effectiveByKey[key].orEmpty()
    fun getCurrent(key: String): String = edits[key] ?: original[key] ?: effectiveByKey[key].orEmpty()
    fun isChanged(key: String): Boolean = edits.containsKey(key) && edits[key] != baseline(key)
    if (confirmDeleteKey != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteKey = null },
            title = { Text("确认删除") },
            text = { Text("将从 .env 中移除：${confirmDeleteKey}\n\n这会让该项回到默认值（如有）。") },
            confirmButton = { TextButton(onClick = { val key = confirmDeleteKey!!; confirmDeleteKey = null; onDeleteEnv(key) }, enabled = canEdit) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDeleteKey = null }) { Text("取消") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header + mode selector
            item {
                Text("系统配置", style = MaterialTheme.typography.titleMedium)
                if (!serviceRunning) Text("服务未运行", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (rootAvailable == false) Text("未获取 Root", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("预览") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("管理员") })
                }
            }

            // Admin mode setup
            if (mode == 1) {
                item {
                    when {
                        !adminTokenConfigured -> {
                            Text("当前服务端未配置 ADMIN_TOKEN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = setupAdminToken, onValueChange = { setupAdminToken = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("设置 ADMIN_TOKEN") },
                                visualTransformation = if (revealSetupToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { IconButton(onClick = { revealSetupToken = !revealSetupToken }) { Icon(if (revealSetupToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null) } }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { val v = setupAdminToken.trim(); if (v.isBlank()) toast("请填写") else { onSetEnv("ADMIN_TOKEN", v); toast("已提交保存"); setupAdminToken = "" } }, enabled = setupAdminToken.trim().isNotBlank() && serviceRunning) { Text("保存") }
                                OutlinedButton(onClick = { mode = 0 }) { Text("返回预览") }
                            }
                        }
                        !hasSessionAdmin -> {
                            Text("输入 ADMIN_TOKEN 解锁编辑", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = tokenInput, onValueChange = { tokenInput = it; adminAuthError = null },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("ADMIN_TOKEN") },
                                isError = adminAuthError != null,
                                visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { IconButton(onClick = { revealToken = !revealToken }) { Icon(if (revealToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null) } }
                            )
                            if (adminAuthError != null) Text(adminAuthError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { enterAdminMode() }, enabled = tokenInput.trim().isNotBlank() && serviceRunning && !validatingAdmin) {
                                    if (validatingAdmin) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text("验证中…") }
                                    else Text("进入管理员模式")
                                }
                                OutlinedButton(onClick = { mode = 0 }, enabled = !validatingAdmin) { Text("返回预览") }
                            }
                        }
                        else -> {
                            Text("管理员模式已开启", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(onClick = { onClearSessionAdminToken(); mode = 0; toast("已退出") }, enabled = serviceRunning) { Text("退出管理员模式") }
                        }
                    }
                }
            }
            // System operations
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRefreshConfig(canAdminOps) }, enabled = serviceRunning && !serverConfigLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("刷新")
                    }
                    OutlinedButton(onClick = onClearCache, enabled = canAdminOps) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("清理缓存")
                    }
                    OutlinedButton(onClick = onDeploy, enabled = canAdminOps) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("重新部署")
                    }
                }
            }

            // Search filter
            item {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("搜索配置") }, placeholder = { Text("例如：TOKEN / PORT / CACHE") },
                )
                when {
                    serverConfigLoading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("加载中…", style = MaterialTheme.typography.bodySmall) }
                    serverConfigError != null -> Text("加载失败：$serverConfigError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Env var categories
            val q = search.trim().lowercase(Locale.getDefault())
            val isSearching = q.isNotBlank()

            categories.forEach { (category, items) ->
                val filtered = if (q.isBlank()) items else items.filter {
                    it.key.lowercase(Locale.getDefault()).contains(q) ||
                        getCurrent(it.key).lowercase(Locale.getDefault()).contains(q) ||
                        it.description.lowercase(Locale.getDefault()).contains(q)
                }
                if (filtered.isEmpty()) return@forEach

                val isExpanded = isSearching || (expanded[category] ?: false)

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded[category] = !(expanded[category] ?: false) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(categoryLabel(category), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${filtered.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }

                if (!isExpanded) return@forEach

                items(filtered, key = { it.key }) { env ->
                    val metaItem = meta[env.key] ?: EnvVarMeta(category = category, type = env.type, description = env.description)
                    val keyExistsInEnv = original.containsKey(env.key)
                    val rawValue = getCurrent(env.key)
                    val maskedByBackend = keyExistsInEnv && original[env.key].orEmpty().trim().all { it == '*' } && original[env.key].orEmpty().isNotBlank()
                    val maskedForPreview = !canEdit && shouldMaskInPreview(key = env.key, type = metaItem.type.ifBlank { env.type }, description = metaItem.description.ifBlank { env.description }, value = rawValue)
                    val masked = maskedByBackend || maskedForPreview

                    if (canEdit) {
                        EnvEditorRow(
                            category = metaItem.category.ifBlank { category }, keyName = env.key,
                            description = metaItem.description.ifBlank { env.description }, type = metaItem.type,
                            options = metaItem.options, currentValue = rawValue, isDefaultValue = !keyExistsInEnv,
                            min = metaItem.min, max = metaItem.max, masked = maskedByBackend,
                            onValueChange = { edits[env.key] = it },
                            onCopyKey = { clipboard.setText(AnnotatedString(env.key)) },
                            onCopyValue = { clipboard.setText(AnnotatedString(rawValue)) },
                            onSave = { onSetEnv(env.key, getCurrent(env.key)); edits.remove(env.key) },
                            onReset = { if (keyExistsInEnv) confirmDeleteKey = env.key else edits.remove(env.key) },
                            saveEnabled = isChanged(env.key),
                            resetEnabled = keyExistsInEnv || edits.containsKey(env.key),
                        )
                    } else {
                        EnvPreviewRow(
                            category = metaItem.category.ifBlank { category }, keyName = env.key,
                            description = metaItem.description.ifBlank { env.description },
                            type = metaItem.type.ifBlank { env.type }, value = rawValue,
                            isDefaultValue = !keyExistsInEnv, masked = masked,
                            onCopyKey = { clipboard.setText(AnnotatedString(env.key)) },
                            onCopyValue = { if (!masked) clipboard.setText(AnnotatedString(rawValue)) },
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun categoryAccentColor(category: String): androidx.compose.ui.graphics.Color {
    val c = category.lowercase(Locale.getDefault())
    return when {
        c.contains("api") -> MaterialTheme.colorScheme.primary
        c.contains("source") -> MaterialTheme.colorScheme.secondary
        c.contains("match") -> MaterialTheme.colorScheme.tertiary
        c.contains("cache") -> MaterialTheme.colorScheme.secondary
        c.contains("system") -> MaterialTheme.colorScheme.tertiary
        c.contains("danmu") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun EnvPreviewRow(
    category: String, keyName: String, description: String, type: String,
    value: String, isDefaultValue: Boolean, masked: Boolean,
    onCopyKey: () -> Unit, onCopyValue: () -> Unit,
) {
    val accent = categoryAccentColor(category)
    val shown = when { masked -> "（已隐藏）"; value.isBlank() -> "(空)"; else -> value }

    ConsoleCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = accent.copy(alpha = 0.25f),
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(keyName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (isDefaultValue) Text("默认", style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.padding(horizontal = 4.dp))
            Text(type, style = MaterialTheme.typography.labelSmall, color = accent)
            IconButton(onClick = onCopyKey, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) }
        }
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(shown, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !masked && value.isNotBlank()) { onCopyValue() }.padding(8.dp))
        }
    }
}
@Composable
private fun EnvEditorRow(
    category: String, keyName: String, description: String, type: String,
    options: List<String>, currentValue: String, isDefaultValue: Boolean,
    min: Double?, max: Double?, masked: Boolean,
    onValueChange: (String) -> Unit, onCopyKey: () -> Unit, onCopyValue: () -> Unit,
    onSave: () -> Unit, onReset: () -> Unit, saveEnabled: Boolean, resetEnabled: Boolean,
) {
    var reveal by remember { mutableStateOf(false) }
    fun parseCommaList(v: String): List<String> = v.split(',').map { it.trim() }.filter { it.isNotBlank() }
    val accent = categoryAccentColor(category)

    ConsoleCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = accent.copy(alpha = 0.25f),
        contentPadding = PaddingValues(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(keyName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (isDefaultValue) Text("默认", style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.padding(horizontal = 4.dp))
                Text(type, style = MaterialTheme.typography.labelSmall, color = accent)
                IconButton(onClick = onCopyKey, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) }
            }
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (type) {
                "boolean" -> {
                    val checked = currentValue.equals("true", true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = checked, onCheckedChange = { onValueChange(if (it) "true" else "false") })
                        Spacer(Modifier.width(8.dp)); Text(if (checked) "true" else "false", style = MaterialTheme.typography.bodySmall)
                    }
                }
                "select" -> {
                    if (options.isEmpty()) {
                        OutlinedTextField(value = currentValue, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            options.forEach { opt -> FilterChip(selected = currentValue == opt, onClick = { onValueChange(opt) }, label = { Text(opt) }) }
                        }
                    }
                }
                "multi-select" -> {
                    val selected = parseCommaList(currentValue)
                    if (options.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            options.forEach { opt ->
                                val isSel = selected.contains(opt)
                                FilterChip(selected = isSel, onClick = { val next = if (isSel) selected.filterNot { it == opt } else (selected + opt); onValueChange(next.joinToString(",")) }, label = { Text(opt) })
                            }
                        }
                    } else {
                        OutlinedTextField(value = currentValue, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("逗号分隔") })
                    }
                }
                "source-order", "platform-order" -> {
                    val selected = parseCommaList(currentValue)
                    fun commit(list: List<String>) { onValueChange(list.joinToString(",")) }
                    if (selected.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            selected.forEachIndexed { idx, item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    IconButton(onClick = { if (idx > 0) { val n = selected.toMutableList(); val t = n[idx - 1]; n[idx - 1] = n[idx]; n[idx] = t; commit(n) } }, enabled = idx > 0, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    IconButton(onClick = { if (idx < selected.lastIndex) { val n = selected.toMutableList(); val t = n[idx + 1]; n[idx + 1] = n[idx]; n[idx] = t; commit(n) } }, enabled = idx < selected.lastIndex, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    IconButton(onClick = { commit(selected.filterNot { it == item }) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                }
                            }
                        }
                    }
                    if (options.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            options.forEach { opt ->
                                val isSel = selected.contains(opt)
                                FilterChip(selected = isSel, onClick = { val next = if (isSel) selected.filterNot { it == opt } else selected + opt; commit(next) }, label = { Text(opt) })
                            }
                        }
                    } else {
                        OutlinedTextField(value = currentValue, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("逗号分隔") })
                    }
                }
                "number" -> {
                    OutlinedTextField(
                        value = currentValue, onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == '.' || it == '-' }) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            val range = buildString { if (min != null || max != null) { append("范围："); append(min?.toString() ?: "-"); append(" ~ "); append(max?.toString() ?: "-") } }
                            if (range.isNotBlank()) Text(range)
                        }
                    )
                }
                "password" -> {
                    OutlinedTextField(
                        value = currentValue, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = if (!reveal) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "隐藏" else "显示") } }
                    )
                    if (masked) Text("当前值已脱敏，保存会覆盖原值。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                else -> {
                    OutlinedTextField(
                        value = currentValue, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
                        singleLine = type != "json" && type != "color-list",
                        minLines = if (type == "json" || type == "color-list") 3 else 1,
                        maxLines = if (type == "json" || type == "color-list") 8 else 1,
                    )
                    if (masked) Text("当前值已脱敏，保存会覆盖原值。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onCopyValue, enabled = currentValue.isNotBlank(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("复制值")
                }
                Button(onClick = onSave, enabled = saveEnabled, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("保存") }
                OutlinedButton(onClick = onReset, enabled = resetEnabled, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(if (isDefaultValue) "恢复默认" else "重置")
                }
            }
        }
    }
}
