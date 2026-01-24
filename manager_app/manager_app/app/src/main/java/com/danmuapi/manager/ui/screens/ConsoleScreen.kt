package com.danmuapi.manager.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

private data class ConsoleSection(val id: String, val label: String)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConsoleScreen(
    paddingValues: PaddingValues,
    serviceRunning: Boolean?,
    apiPort: Int,
    apiHost: String,
    token: String,
    adminToken: String,
    onStartService: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current

    val port = apiPort.coerceIn(1, 65535)
    val host = apiHost.trim().let {
        when (it) {
            "", "0.0.0.0", "::", "::0" -> "127.0.0.1"
            "localhost" -> "127.0.0.1"
            else -> it
        }
    }
    val normalToken = token.trim().ifBlank { "87654321" }
    val adminTokenTrim = adminToken.trim()

    val sections = remember {
        listOf(
            ConsoleSection("preview", "配置预览"),
            ConsoleSection("logs", "日志"),
            ConsoleSection("api", "接口调试"),
            ConsoleSection("push", "推送弹幕"),
            ConsoleSection("env", "系统配置"),
        )
    }

    var selectedSection by remember { mutableStateOf(sections.first().id) }
    var useAdminToken by remember { mutableStateOf(false) }

    // If user selects Env section, prefer admin token automatically.
    LaunchedEffect(selectedSection) {
        if (selectedSection == "env") {
            if (adminTokenTrim.isBlank()) {
                onShowMessage("未配置 ADMIN_TOKEN：系统配置功能需要管理员 Token")
            } else {
                useAdminToken = true
            }
        }
    }

    val effectiveToken = if (useAdminToken && adminTokenTrim.isNotBlank()) adminTokenTrim else normalToken
    val url = remember(host, port, effectiveToken, selectedSection) {
        "http://$host:$port/$effectiveToken#$selectedSection"
    }

    var pageTitle by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    // WebView 深色模式需要读取 MaterialTheme。
    // 注意：MaterialTheme.* 属于 @Composable 读取，不能放在 remember { } 的计算块里。
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Keep a single WebView instance to preserve state across recompositions.
    val webView = remember {
        WebView(ctx).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = false
                loadsImagesAutomatically = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                mediaPlaybackRequiresUserGesture = true
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                    loading = newProgress in 0..99
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    pageTitle = title
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loading = true
                    lastError = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    lastError = description ?: "加载失败（$errorCode）"
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        lastError = error?.description?.toString() ?: "加载失败"
                    }
                }
            }
        }
    }

    // Apply dark mode based on current Compose theme (updates when theme changes).
    LaunchedEffect(isDarkTheme) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            val forceDark = if (isDarkTheme) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
            WebSettingsCompat.setForceDark(webView.settings, forceDark)
        }
    }

    // Destroy WebView when leaving the screen.
    DisposableEffect(Unit) {
        onDispose {
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    // Back: go back in WebView history if possible.
    BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
    }

    // Load when URL changes and service is running.
    LaunchedEffect(url, serviceRunning) {
        if (serviceRunning == true) {
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header / Tips
        Surface(
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Text(
                        text = "内置管理界面",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "这里嵌入了 danmu-api 的 Web 管理后台（与浏览器访问功能一致），无需另开浏览器即可完成日志查看、接口调试、推送弹幕、系统配置等操作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Token selector
            FilterChip(
                selected = !useAdminToken,
                onClick = { useAdminToken = false },
                label = { Text("普通") },
            )
            FilterChip(
                selected = useAdminToken,
                onClick = {
                    if (adminTokenTrim.isBlank()) {
                        onShowMessage("未配置 ADMIN_TOKEN")
                    } else {
                        useAdminToken = true
                    }
                },
                enabled = adminTokenTrim.isNotBlank(),
                label = { Text(if (adminTokenTrim.isBlank()) "管理员（未配置）" else "管理员") },
            )

            Spacer(Modifier.size(4.dp))

            // Section selector
            sections.forEach { s ->
                FilterChip(
                    selected = selectedSection == s.id,
                    onClick = { selectedSection = s.id },
                    label = { Text(s.label) },
                )
            }

            Spacer(Modifier.size(4.dp))

            // WebView actions
            AssistChip(
                onClick = {
                    if (serviceRunning == true) webView.reload() else onShowMessage("服务未运行")
                },
                label = { Text("刷新") },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            )
            AssistChip(
                onClick = {
                    // Open in external browser
                    val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    ctx.startActivity(i)
                },
                label = { Text("浏览器打开") },
                leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                enabled = serviceRunning == true,
            )
        }

        if (serviceRunning != true) {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = when (serviceRunning) {
                            null -> "正在检测服务状态…"
                            false -> "服务未运行"
                            else -> "服务状态未知"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "管理后台需要服务启动后才能访问。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onStartService,
                            enabled = serviceRunning == false,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("启动服务")
                        }
                        OutlinedButton(
                            onClick = { onShowMessage("启动后将自动加载管理界面") },
                        ) {
                            Text("提示")
                        }
                    }
                }
            }
            return@Column
        }

        // WebView container
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
                update = { /* no-op: LaunchedEffect drives loading */ },
            )

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    progress = (progress.coerceIn(0, 100) / 100f),
                )
            }

            if (!lastError.isNullOrBlank()) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "加载失败",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = lastError ?: "未知错误",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "URL：$url",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { webView.loadUrl(url) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("重试")
                            }
                            OutlinedButton(onClick = { lastError = null }) {
                                Text("关闭")
                            }
                        }
                    }
                }
            }
        }
    }
}
