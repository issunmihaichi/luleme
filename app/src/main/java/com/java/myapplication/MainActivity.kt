package com.java.myapplication

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.java.myapplication.data.MissavScraper
import com.java.myapplication.data.RecordStore
import com.java.myapplication.data.SessionRecord
import com.java.myapplication.ui.AddRecordDialog
import com.java.myapplication.ui.HomeScreen
import com.java.myapplication.ui.ReportScreen
import com.java.myapplication.ui.SettingsScreen
import com.java.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppPage { HOME, REPORT, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LuleApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuleApp() {
    val context = LocalContext.current
    val store = remember { RecordStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf(store.load()) }
    var page by remember { mutableStateOf(AppPage.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }
    var xpMode by remember { mutableStateOf(store.isXpMode()) }

    // 保存记录；XP 开启且 URL 域名含 missav 时，后台抓取页面元数据回填
    val persistAndScrape: (SessionRecord) -> Unit = { r ->
        store.add(r)
        records = store.load()
        val uri = runCatching { Uri.parse(r.url) }.getOrNull()
        val host = uri?.host?.lowercase()
        val scheme = uri?.scheme?.lowercase()
        val isMissav = (scheme == "https" || scheme == "http") &&
            (host == "missav.ws" || host == "missav.com" ||
                host?.endsWith(".missav.ws") == true || host?.endsWith(".missav.com") == true)
        if (xpMode && isMissav && host != null) {
            // 用白名单校验过的 host 重建规范化 https URL，避免校验与请求解析器分歧
            val path = uri.encodedPath.orEmpty()
            val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
            val safeUrl = "https://$host$path$query"
            scope.launch {
                val meta = withContext(Dispatchers.IO) { MissavScraper.parse(safeUrl) }
                // 仅当抓到至少一个有效字段时才回填，避免用空值覆盖用户已填内容
                if (meta != null && (meta.title.isNotBlank() || meta.code.isNotBlank() ||
                        meta.actress.isNotBlank() || meta.genres.isNotEmpty())
                ) {
                    store.update(
                        r.copy(
                            title = meta.title,
                            code = meta.code,
                            actress = meta.actress,
                            genres = meta.genres
                        )
                    )
                    records = store.load()
                }
            }
        }
    }

    // 系统返回手势/返回键：在报表、设置页时回到主页而不是退出应用
    BackHandler(enabled = page != AppPage.HOME) {
        page = AppPage.HOME
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🦌了么") },
                actions = {
                    IconButton(onClick = { page = AppPage.REPORT }) {
                        Icon(Icons.Default.BarChart, contentDescription = "报表")
                    }
                    IconButton(onClick = {
                        page = if (page == AppPage.SETTINGS) AppPage.HOME else AppPage.SETTINGS
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            // FAB 仅在主页显示，带缩放 + 淡入淡出动画
            AnimatedVisibility(
                visible = page == AppPage.HOME,
                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(250)),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200))
            ) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加记录")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // 全局页面切换：滑动 + 淡入淡出
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut(tween(200)))
                },
                label = "pageSwitch"
            ) { p ->
                when (p) {
                    AppPage.HOME -> HomeScreen(
                        records = records,
                        onAdd = persistAndScrape,
                        onDelete = { id ->
                            store.remove(id)
                            records = store.load()
                        }
                    )
                    AppPage.REPORT -> ReportScreen(
                        records = records,
                        onBack = { page = AppPage.HOME }
                    )
                    AppPage.SETTINGS -> SettingsScreen(
                        recordCount = records.size,
                        totalMinutes = records.sumOf { it.durationMin },
                        xpEnabled = xpMode,
                        onXpChange = { enabled ->
                            store.setXpMode(enabled)
                            xpMode = enabled
                        },
                        onExportJson = { store.exportJson() },
                        onImportJson = { json, replace ->
                            val n = store.importJson(json, replace)
                            records = store.load()
                            n
                        },
                        onClearData = {
                            store.clear()
                            records = store.load()
                        },
                        onBack = { page = AppPage.HOME }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecordDialog(
            onDismiss = { showAddDialog = false },
            onSave = { r ->
                persistAndScrape(r)
                showAddDialog = false
            }
        )
    }
}