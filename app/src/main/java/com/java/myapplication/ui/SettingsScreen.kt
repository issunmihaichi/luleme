package com.java.myapplication.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 设置页：统计 / 备份（导入导出）/ 占位项 / 清空数据 */
@Composable
fun SettingsScreen(
    recordCount: Int,
    totalMinutes: Int,
    xpEnabled: Boolean,
    onXpChange: (Boolean) -> Unit,
    onExportJson: () -> String,
    onImportJson: (json: String, replace: Boolean) -> Int,
    onClearData: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    // 导出：系统文件选择器决定保存位置
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(onExportJson().toByteArray())
            } != null
        } catch (e: Exception) {
            false
        }
        Toast.makeText(context, if (ok) "导出成功" else "导出失败", Toast.LENGTH_SHORT).show()
    }

    // 导入：系统文件选择器选取备份文件
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (json.isNullOrBlank()) {
            Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
        } else {
            pendingImportJson = json
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 标题行 + 返回
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        ListItem(
            headlineContent = { Text("记录统计") },
            supportingContent = { Text("共 $recordCount 次 · 累计 $totalMinutes 分钟") }
        )
        HorizontalDivider()

        // ===== 备份 =====
        ListItem(
            headlineContent = { Text("导出备份") },
            supportingContent = { Text("保存为 JSON 文件") },
            modifier = Modifier.clickable {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                exportLauncher.launch("luleme_backup_$stamp.json")
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("导入备份") },
            supportingContent = { Text("从 JSON 文件恢复记录") },
            modifier = Modifier.clickable {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("主题") },
            supportingContent = { Text("跟随系统（敬请期待）") }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("提醒") },
            supportingContent = { Text("按时🦌提醒（敬请期待）") }
        )
        HorizontalDivider()

        // ===== XP 模式 =====
        ListItem(
            headlineContent = { Text("XP 模式") },
            supportingContent = {
                Text(
                    if (xpEnabled) "开启：提交 missav 链接时自动抓取标题/品番/女優/标签"
                    else "关闭：missav 链接仅存 URL"
                )
            },
            trailingContent = {
                Switch(
                    checked = xpEnabled,
                    onCheckedChange = onXpChange
                )
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("清空所有记录") },
            supportingContent = { Text("删除全部数据，不可恢复") },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            modifier = Modifier.clickable { confirmClear = true }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("关于") },
            supportingContent = { Text("🦌了么 v1.1 · 最小实现") }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("确认清空？") },
            text = { Text("将删除全部记录，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearData()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }

    // 导入策略选择
    pendingImportJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text("导入备份") },
            text = { Text("选择导入方式：\n\n· 合并：保留现有记录，按 ID 去重\n· 替换：清空现有记录后导入") },
            confirmButton = {
                TextButton(onClick = {
                    val n = onImportJson(json, false)
                    pendingImportJson = null
                    Toast.makeText(context, "已合并导入 $n 条记录", Toast.LENGTH_SHORT).show()
                }) { Text("合并") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val n = onImportJson(json, true)
                    pendingImportJson = null
                    Toast.makeText(context, "已替换导入 $n 条记录", Toast.LENGTH_SHORT).show()
                }) { Text("替换") }
            }
        )
    }
}