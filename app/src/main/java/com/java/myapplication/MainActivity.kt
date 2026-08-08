package com.java.myapplication

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.java.myapplication.data.RecordStore
import com.java.myapplication.ui.AddRecordDialog
import com.java.myapplication.ui.HomeScreen
import com.java.myapplication.ui.ReportScreen
import com.java.myapplication.ui.SettingsScreen
import com.java.myapplication.ui.theme.MyApplicationTheme

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
    var records by remember { mutableStateOf(store.load()) }
    var page by remember { mutableStateOf(AppPage.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }

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
                        onAdd = { r ->
                            store.add(r)
                            records = store.load()
                        },
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
                store.add(r)
                records = store.load()
                showAddDialog = false
            }
        )
    }
}