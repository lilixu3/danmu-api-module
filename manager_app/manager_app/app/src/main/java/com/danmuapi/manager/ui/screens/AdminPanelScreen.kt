package com.danmuapi.manager.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.danmuapi.manager.ui.components.ManagerCard

private data class AdminSection(val id: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    paddingValues: PaddingValues,
    serviceRunning: Boolean?,
    apiPort: Int,
    apiHost: String,
    token: String,
    adminToken: String,
    onStartService: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val sections = remember {
        listOf(
            AdminSection("preview", "预览"),
            AdminSection("logs", "日志"),
            AdminSection("api", "接口"),
            AdminSection("push", "推送"),
            AdminSection("env", "配置"),
        )
    }

    val safePort = if (apiPort in 1..65535) apiPort else 9321
    val normalToken = token.trim().ifBlank { "87654321" }
    val admin = adminToken.trim()
    val hasAdmin = admin.isNotBlank()

    var section by rememberSaveable { mutableStateOf(sections.first().id) }
    var useAdminToken by rememberSaveable { mutableStateOf(hasAdmin) }

    // If user disabled ADMIN_TOKEN in .env, keep the toggle off.
    LaunchedEffect(hasAdmin) {
        if (!hasAdmin) useAdminToken = false
    }

    val effectiveToken = if (useAdminToken && hasAdmin) admin else normalToken

    // Prefer apiHost if user explicitly bound the service to a specific address.
    // For the common "0.0.0.0" case, use 127.0.0.1 to ensure local access.
    val rawHost = apiHost.trim().removePrefix("http://").removePrefix("https://")
    val host = rawHost.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: "127.0.0.1"

    val baseUrl = "http://$host:$safePort/$effectiveToken"
    val targetUrl = "$baseUrl#$section"

    var progress by remember { mutableStateOf(0) }
    var lastError by remember { mutableStateOf<String?>(null) }

    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.value?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.webChromeClient = null
                    wv.webViewClient = null
                    wv.destroy()
                } catch (_: Throwable) {
                    // ignore
                }
            }
            webViewHolder.value = null
        }
    }

    BackHandler(enabled = webViewHolder.value?.canGoBack() == true) {
        webViewHolder.value?.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Service status
        if (serviceRunning != true) {
            ManagerCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val statusText = when (serviceRunning) {
                        null -> "正在检测服务状态…"
                        false -> "服务未运行，无法打开后台管理页面。"
                        else -> ""
                    }
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onStartService, enabled = serviceRunning == false) {
                            Text("启动服务")
                        }
                    }
                }
            }
        }

        // Quick controls
        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "内置后台管理",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (hasAdmin) {
                        "已在 App 内嵌入 danmu-api 管理界面（Web UI）。默认使用 ADMIN_TOKEN 进入完整功能。"
                    } else {
                        "已在 App 内嵌入 danmu-api 管理界面（Web UI）。当前未配置 ADMIN_TOKEN，部分系统功能可能不可用。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = useAdminToken,
                        onClick = {
                            if (hasAdmin) {
                                useAdminToken = !useAdminToken
                            } else {
                                Toast.makeText(context, "未配置 ADMIN_TOKEN", Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text(if (useAdminToken) "管理员" else "普通") },
                        enabled = hasAdmin,
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        sections.forEach { s ->
                            FilterChip(
                                selected = section == s.id,
                                onClick = { section = s.id },
                                label = { Text(s.label) },
                                enabled = serviceRunning == true,
                            )
                        }
                    }
                }

                Text(
                    text = targetUrl,
                    style = MaterialTheme.typography.labelMedium,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { webViewHolder.value?.goBack() },
                        enabled = webViewHolder.value?.canGoBack() == true,
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { webViewHolder.value?.goForward() },
                        enabled = webViewHolder.value?.canGoForward() == true,
                    ) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(
                        onClick = {
                            lastError = null
                            webViewHolder.value?.reload()
                        },
                        enabled = serviceRunning == true,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(targetUrl))
                            Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                        },
                        enabled = serviceRunning == true,
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(
                        onClick = {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(i)
                        },
                        enabled = serviceRunning == true,
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open in browser")
                    }
                }
            }
        }

        if (serviceRunning == true) {
            if (progress in 1..99) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (lastError != null) {
                ManagerCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "页面加载失败：${lastError}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    lastError = null
                                    webViewHolder.value?.loadUrl(targetUrl)
                                },
                            ) {
                                Text("重试")
                            }
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewHolder.value = this

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.displayZoomControls = false
                        settings.builtInZoomControls = false
                        settings.setSupportZoom(false)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                val host = uri.host ?: return false
                                val uriPort = if (uri.port != -1) uri.port else when (uri.scheme) {
                                    "https" -> 443
                                    "http" -> 80
                                    else -> -1
                                }

                                val normalizedSafeHost = safeHost.trim()
                                val isLocalHost = (host == normalizedSafeHost) ||
                                    (normalizedSafeHost == "127.0.0.1" && host == "localhost") ||
                                    (normalizedSafeHost == "localhost" && host == "127.0.0.1")

                                val isDanmuApiHost = isLocalHost && (uriPort == safePort)

                                // Keep Danmu API UI navigation in-app. Anything else (e.g. GitHub, docs) open externally.
                                return if (!isDanmuApiHost) {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (_: Throwable) {
                                    }
                                    true
                                } else {
                                    false
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                lastError = null
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                // Only capture main frame error to avoid noise.
                                val isMainFrame = request?.isForMainFrame ?: true
                                if (isMainFrame) {
                                    lastError = error?.description?.toString() ?: "unknown"
                                }
                            }
                        }

                        loadUrl(targetUrl)
                    }
                },
                update = { wv ->
                    if (wv.url != targetUrl) {
                        wv.loadUrl(targetUrl)
                    }
                },
            )
        } else {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}
