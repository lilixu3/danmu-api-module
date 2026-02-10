package com.danmuapi.manager.ui.screens.console

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.network.HttpResult
import com.danmuapi.manager.ui.screens.console.components.*
import kotlinx.coroutines.launch

/**
 * API 测试 Tab
 */
@Composable
fun ApiTestTabContent(
    serviceRunning: Boolean,
    adminToken: String,
    requestApi: suspend (
        method: String,
        path: String,
        query: Map<String, String?>,
        bodyJson: String?,
        useAdminToken: Boolean,
    ) -> HttpResult
) {
    var method by remember { mutableStateOf("GET") }
    var path by remember { mutableStateOf("/api/v1/match") }
    var queryParams by remember { mutableStateOf("") }
    var requestBody by remember { mutableStateOf("") }
    var useAdmin by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "API 测试工具",
                    style = MaterialTheme.typography.titleLarge
                )

                // HTTP 方法选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("GET", "POST", "PUT", "DELETE").forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m) }
                        )
                    }
                }

                // 路径
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("请求路径") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 查询参数
                OutlinedTextField(
                    value = queryParams,
                    onValueChange = { queryParams = it },
                    label = { Text("查询参数 (key=value&key2=value2)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 请求体
                if (method in listOf("POST", "PUT")) {
                    OutlinedTextField(
                        value = requestBody,
                        onValueChange = { requestBody = it },
                        label = { Text("请求体 (JSON)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )
                }

                // 使用管理员令牌
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("使用管理员令牌")
                    Switch(
                        checked = useAdmin,
                        onCheckedChange = { useAdmin = it },
                        enabled = adminToken.isNotEmpty()
                    )
                }

                // 发送按钮
                Button(
                    onClick = {
                        loading = true
                        scope.launch {
                            val queryMap = queryParams.split("&")
                                .filter { it.contains("=") }
                                .associate {
                                    val (k, v) = it.split("=", limit = 2)
                                    k to v
                                }
                            val result = requestApi(
                                method,
                                path,
                                queryMap,
                                requestBody.takeIf { it.isNotBlank() },
                                useAdmin
                            )
                            response = if (result.error != null) {
                                "错误: ${result.error}"
                            } else {
                                "状态码: ${result.code}\n\n${result.body}"
                            }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serviceRunning && !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("发送请求")
                }

                // 响应
                if (response != null) {
                    HorizontalDivider()
                    Text(
                        text = "响应",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = response!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 推送 Tab
 */
@Composable
fun PushTabContent(
    serviceRunning: Boolean,
    adminToken: String,
    requestApi: suspend (
        method: String,
        path: String,
        query: Map<String, String?>,
        bodyJson: String?,
        useAdminToken: Boolean,
    ) -> HttpResult
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        EmptyState(
            icon = Icons.Default.Send,
            message = "推送功能开发中..."
        )
    }
}

/**
 * 系统 Tab
 */
@Composable
fun SystemTabContent(
    serviceRunning: Boolean,
    adminToken: String,
    sessionAdminToken: String,
    onSetSessionAdminToken: (String) -> Unit,
    onClearSessionAdminToken: () -> Unit,
    serverConfig: com.danmuapi.manager.data.model.ServerConfigResponse?,
    serverConfigLoading: Boolean,
    serverConfigError: String?,
    onRefreshConfig: (useAdminToken: Boolean) -> Unit,
    onSetEnv: (key: String, value: String) -> Unit,
    onDeleteEnv: (key: String) -> Unit,
    onClearCache: () -> Unit,
    onDeploy: () -> Unit,
    validateAdminToken: suspend (token: String) -> Pair<Boolean, String?>
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 管理员令牌
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "管理员令牌",
                    style = MaterialTheme.typography.titleMedium
                )

                var tokenInput by remember { mutableStateOf("") }
                var validating by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("输入管理员令牌") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = sessionAdminToken.isEmpty()
                )

                if (sessionAdminToken.isEmpty()) {
                    Button(
                        onClick = {
                            validating = true
                            scope.launch {
                                val (valid, _) = validateAdminToken(tokenInput)
                                if (valid) {
                                    onSetSessionAdminToken(tokenInput)
                                }
                                validating = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = tokenInput.isNotEmpty() && !validating
                    ) {
                        Text("验证并启用")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { },
                            label = { Text("已启用") },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onClearSessionAdminToken
                        ) {
                            Text("清除")
                        }
                    }
                }
            }
        }

        // 系统操作
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "系统操作",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedButton(
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serviceRunning && adminToken.isNotEmpty()
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("清除缓存")
                }

                OutlinedButton(
                    onClick = onDeploy,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serviceRunning && adminToken.isNotEmpty()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重新部署")
                }
            }
        }
    }
}
