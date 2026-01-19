package com.danmuapi.manager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.BuildConfig
import com.danmuapi.manager.ui.components.ManagerCard

@Composable
fun AboutScreen(
    paddingValues: PaddingValues,
) {
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "关于", style = MaterialTheme.typography.titleLarge)

        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Danmu API Manager", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "用于管理 Danmu API 模块：服务启停、核心管理、日志查看、配置导入导出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "相关链接", style = MaterialTheme.typography.titleMedium)

                FilledTonalButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Code, contentDescription = null)
                    Text(text = "项目主页", modifier = Modifier.padding(start = 8.dp))
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                }

                FilledTonalButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0"))
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Text(text = "开源协议", modifier = Modifier.padding(start = 8.dp))
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                }

                FilledTonalButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com/privacy"))
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PrivacyTip, contentDescription = null)
                    Text(text = "隐私与权限说明", modifier = Modifier.padding(start = 8.dp))
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
