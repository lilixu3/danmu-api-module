@file:OptIn(ExperimentalLayoutApi::class)

package com.danmuapi.manager.ui.screens.console

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.network.HttpResult
import com.danmuapi.manager.ui.screens.console.components.AnimeItem
import com.danmuapi.manager.ui.screens.console.components.ConsoleCard
import com.danmuapi.manager.ui.screens.console.components.EpisodeItem
import com.danmuapi.manager.util.rememberLanIpv4Addresses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder

@Composable
fun PushTabContent(
    serviceRunning: Boolean,
    apiToken: String,
    apiPort: Int,
    requestApi: suspend (
        method: String, path: String, query: Map<String, String?>,
        bodyJson: String?, useAdminToken: Boolean,
    ) -> HttpResult
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val lanIps = rememberLanIpv4Addresses()
    val lanIp = lanIps.firstOrNull()
    val detectedSubnets = remember(lanIps) {
        lanIps.mapNotNull { ip -> val p = ip.split('.'); if (p.size == 4) p.take(3).joinToString(".") else null }.distinct()
    }

    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var animes by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var loadingEpisodes by remember { mutableStateOf(false) }

    val okPushPath = remember { "/action?do=refresh&type=danmaku&path=" }
    var lanPort by remember { mutableStateOf("9978") }
    var danmuSize by remember { mutableStateOf("") }
    var danmuOffset by remember { mutableStateOf("") }

    fun currentPort(): Int = lanPort.trim().toIntOrNull() ?: 9978
    fun buildPushTemplate(host: String, port: Int): String = "http://$host:$port$okPushPath"

    var scanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var foundDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var autoScan by remember { mutableStateOf(true) }
    var lastAutoScanKey by remember { mutableStateOf<String?>(null) }
    var lastPushOk by remember { mutableStateOf<Boolean?>(null) }
    var lastPushMessage by remember { mutableStateOf<String?>(null) }

    val localHosts = remember(lanIps) { (setOf("127.0.0.1") + lanIps).toSet() }

    fun labelForHost(host: String): String = when {
        host == "127.0.0.1" -> "本机 (127.0.0.1)"
        lanIps.contains(host) -> "本机 ($host)"
        else -> host
    }

    fun currentPushTemplate(): String {
        val host = selectedDevice ?: "127.0.0.1"
        return buildPushTemplate(host, currentPort())
    }
    fun search() {
        if (!serviceRunning) { searchError = "服务未运行"; return }
        val kw = keyword.trim()
        if (kw.isBlank()) { searchError = "请输入关键词"; return }
        searching = true; searchError = null; selectedAnime = null; episodes = emptyList(); animes = emptyList()
        scope.launch {
            val res = requestApi("GET", "/api/v2/search/anime", mapOf("keyword" to kw), null, false)
            searching = false
            if (!res.isSuccessful) { searchError = res.error ?: "搜索失败"; return@launch }
            try {
                val obj = JSONObject(res.body)
                val arr = obj.optJSONArray("animes") ?: JSONArray()
                val list = mutableListOf<AnimeItem>()
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    list.add(AnimeItem(
                        animeId = a.optInt("animeId", a.optInt("bangumiId")),
                        title = a.optString("animeTitle", a.optString("title")),
                        typeDesc = a.optString("typeDescription"),
                    ))
                }
                animes = list
                if (list.isEmpty()) searchError = "未找到结果"
            } catch (_: Throwable) { searchError = "解析响应失败" }
        }
    }

    fun loadEpisodes(anime: AnimeItem) {
        if (!serviceRunning) return
        selectedAnime = anime; episodes = emptyList(); loadingEpisodes = true
        scope.launch {
            val res = requestApi("GET", "/api/v2/bangumi/${anime.animeId}", emptyMap<String, String?>(), null, false)
            loadingEpisodes = false
            if (!res.isSuccessful) { searchError = res.error ?: "获取剧集失败"; return@launch }
            try {
                val obj = JSONObject(res.body)
                val bangumi = obj.optJSONObject("bangumi") ?: JSONObject()
                val eps = bangumi.optJSONArray("episodes") ?: JSONArray()
                val list = mutableListOf<EpisodeItem>()
                for (i in 0 until eps.length()) {
                    val e = eps.optJSONObject(i) ?: continue
                    list.add(EpisodeItem(episodeId = e.optInt("episodeId"), title = e.optString("episodeTitle"), episodeNumber = e.optString("episodeNumber")))
                }
                episodes = list
            } catch (_: Throwable) { searchError = "解析剧集失败" }
        }
    }

    fun buildCommentUrl(episodeId: Int): String {
        val host = if (selectedDevice != null && localHosts.contains(selectedDevice)) "127.0.0.1" else lanIp ?: "127.0.0.1"
        return "http://$host:$apiPort/$apiToken/api/v2/comment/$episodeId?format=xml"
    }

    fun pushOne(episode: EpisodeItem) {
        val template = currentPushTemplate()
        val commentUrl = buildCommentUrl(episode.episodeId)
        val sizeText = danmuSize.trim(); val offsetText = danmuOffset.trim()
        fun isFiniteNumber(s: String): Boolean { val d = s.toDoubleOrNull() ?: return false; return d.isFinite() }
        if (sizeText.isNotBlank() && !isFiniteNumber(sizeText)) { lastPushOk = false; lastPushMessage = "弹幕大小格式不正确"; return }
        if (offsetText.isNotBlank() && !isFiniteNumber(offsetText)) { lastPushOk = false; lastPushMessage = "偏移量格式不正确"; return }
        val extras = buildString {
            if (sizeText.isNotBlank()) append("&size=").append(URLEncoder.encode(sizeText, "UTF-8"))
            if (offsetText.isNotBlank()) append("&offset=").append(URLEncoder.encode(offsetText, "UTF-8"))
        }
        val finalUrl = template + URLEncoder.encode(commentUrl, "UTF-8") + extras
        lastPushOk = null; lastPushMessage = "推送中：${episode.episodeNumber.ifBlank { episode.episodeId.toString() }}"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { java.net.URL(finalUrl).openConnection().apply { connectTimeout = 1500; readTimeout = 2500 }.getInputStream().use { it.readBytes() }; true }
                catch (_: Throwable) { false }
            }
            lastPushOk = ok
            lastPushMessage = if (ok) "推送成功：${episode.episodeNumber} ${episode.title}".trim()
            else "推送失败：请确认目标设备可访问（${selectedDevice ?: "127.0.0.1"}:${currentPort()}）"
            if (ok) { clipboard.setText(AnnotatedString(commentUrl)); Toast.makeText(context, "已推送并复制弹幕链接", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(context, "推送失败", Toast.LENGTH_SHORT).show()
        }
    }
    fun scanLan() {
        if (scanning) return
        val port = currentPort()
        fun sortDevices(list: List<String>): List<String> {
            val lanSet = lanIps.toSet()
            return list.distinct().sortedWith(compareBy<String>({ it != "127.0.0.1" }, { !lanSet.contains(it) }, { it }))
        }
        scanning = true; scanProgress = 0; foundDevices = emptyList()
        scope.launch {
            val discovered = mutableListOf<String>()
            val candidates = mutableListOf<String>().apply { addAll(localHosts); detectedSubnets.forEach { s -> for (i in 1..254) add("$s.$i") } }
            val uniq = candidates.distinct(); scanTotal = uniq.size
            val chunkSize = 64; var done = 0
            for (chunk in uniq.chunked(chunkSize)) {
                val tasks = chunk.map { host ->
                    async(Dispatchers.IO) {
                        val ok = try { Socket().use { s -> s.connect(InetSocketAddress(host, port), 250) }; true } catch (_: Throwable) { false }
                        if (ok) host else null
                    }
                }
                tasks.forEach { d -> val host = d.await(); if (host != null) discovered.add(host) }
                done += chunk.size; scanProgress = done; foundDevices = sortDevices(discovered)
            }
            scanning = false
            val sorted = sortDevices(discovered); foundDevices = sorted
            if (sorted.isNotEmpty() && (selectedDevice == null || !sorted.contains(selectedDevice))) selectedDevice = sorted.first()
        }
    }

    LaunchedEffect(selectedAnime?.animeId, lanPort, autoScan, serviceRunning, detectedSubnets) {
        val key = "${selectedAnime?.animeId}:${lanPort.trim()}:${detectedSubnets.joinToString(",")}"
        if (serviceRunning && selectedAnime != null && autoScan && key != lastAutoScanKey) { lastAutoScanKey = key; scanLan() }
    }

    // ── UI ──
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Text("弹幕推送", style = MaterialTheme.typography.titleMedium)
            Text("搜索番剧 → 选择剧集 → 推送到局域网播放器", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!serviceRunning) Text("服务未运行", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (lanIp != null) Text("本机 IP：$lanIp", style = MaterialTheme.typography.bodySmall)
            else Text("未检测到局域网 IPv4", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // Search
        item {
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索番剧") }, placeholder = { Text("例如：鬼灭 / 进击 / 你的名字") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { search() }, enabled = !searching && serviceRunning) {
                    if (searching) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                    Text("搜索")
                }
                OutlinedButton(onClick = { keyword = ""; animes = emptyList(); episodes = emptyList(); selectedAnime = null; searchError = null }) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("清空")
                }
            }
            if (searchError != null) Text(searchError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        // Search results
        if (animes.isNotEmpty()) {
            item { Text("搜索结果", style = MaterialTheme.typography.titleSmall) }
            items(animes) { anime ->
                val isSelected = selectedAnime?.animeId == anime.animeId
                ConsoleCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { loadEpisodes(anime) },
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                    borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(anime.title, style = MaterialTheme.typography.titleSmall)
                    if (anime.typeDesc.isNotBlank()) Text(anime.typeDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("ID: ${anime.animeId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Target device settings
        if (selectedAnime != null) {
            item {
                ConsoleCard {
                    Text("目标设备", style = MaterialTheme.typography.titleSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = lanPort, onValueChange = { lanPort = it.filter { ch -> ch.isDigit() }.take(5) },
                            modifier = Modifier.width(110.dp), singleLine = true, label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = autoScan, onCheckedChange = { autoScan = it })
                            Spacer(Modifier.width(6.dp)); Text("自动扫描", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = danmuSize, onValueChange = { danmuSize = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("弹幕大小") }, placeholder = { Text("留空=默认") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = danmuOffset, onValueChange = { danmuOffset = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("偏移量") }, placeholder = { Text("留空=默认") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scanLan() }, enabled = !scanning) { Text(if (scanning) "扫描中…" else "扫描设备") }
                        if (scanTotal > 0) Text(if (scanning) "$scanProgress/$scanTotal" else "已扫描 $scanTotal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (foundDevices.isNotEmpty()) {
                        Text("发现设备", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            foundDevices.forEach { host ->
                                FilterChip(selected = selectedDevice == host, onClick = { selectedDevice = host }, label = { Text(labelForHost(host)) })
                            }
                        }
                    } else {
                        Text("未发现设备：请确认播放器已开启 ${currentPort()} 端口。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val pushTemplate = currentPushTemplate()
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pushTemplate, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(pushTemplate)) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    if (lastPushMessage != null) {
                        Text(lastPushMessage!!, color = when (lastPushOk) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        // Episodes
        if (episodes.isNotEmpty()) {
            item { Text("剧集列表", style = MaterialTheme.typography.titleSmall) }
            if (loadingEpisodes) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("加载中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(episodes) { ep ->
                val commentUrl = buildCommentUrl(ep.episodeId)
                ConsoleCard(contentPadding = PaddingValues(10.dp)) {
                    Text("${ep.episodeNumber} ${ep.title}".trim(), style = MaterialTheme.typography.titleSmall)
                    Text("弹幕ID: ${ep.episodeId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(commentUrl, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pushOne(ep) }, enabled = serviceRunning) { Text("推送") }
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(commentUrl)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp)); Text("复制链接")
                        }
                    }
                }
            }
        }
    }
}
