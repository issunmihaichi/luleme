package com.java.myapplication.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.data.SessionRecord
import java.util.Calendar
import kotlin.math.roundToInt

enum class ReportType(val label: String) {
    WEEK("周报"), MONTH("月报"), YEAR("年报")
}

data class ReportStats(
    val count: Int,
    val totalMin: Int,
    val avgMin: Int,
    val maxMin: Int,
    val minMin: Int,
    val bucketLabels: List<String>,
    val bucketCounts: List<Int>,
    val bucketMinutes: List<Int>,
    val prevCount: Int,
    val prevTotalMin: Int,
    val periodDays: Int
)

/** 报表页：MD3 组件 + 切换/数字/柱状生长动画 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    records: List<SessionRecord>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var type by remember { mutableStateOf(ReportType.WEEK) }
    val stats = remember(records, type) { computeStats(records, type) }

    // 统计数字滚动动画（切换页签时旧值滚动到新值）
    val animCount by animateIntAsState(stats.count, tween(500), label = "count")
    val animTotal by animateIntAsState(stats.totalMin, tween(500), label = "total")
    val animAvg by animateIntAsState(stats.avgMin, tween(500), label = "avg")
    val animMax by animateIntAsState(stats.maxMin, tween(500), label = "max")

    // 页面进入动画
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 16 })
    ) {
        Column(modifier = modifier.fillMaxSize()) {
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
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "报表",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            PrimaryTabRow(selectedTabIndex = type.ordinal) {
                ReportType.entries.forEach { t ->
                    Tab(
                        selected = type == t,
                        onClick = { type = t },
                        text = { Text(t.label) }
                    )
                }
            }
            // 页签切换：滑动 + 淡入淡出
            AnimatedContent(
                targetState = type,
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut(tween(200)))
                },
                label = "reportSwitch"
            ) { t ->
                ReportContent(
                    type = t,
                    stats = stats,
                    count = animCount,
                    total = animTotal,
                    avg = animAvg,
                    max = animMax
                )
            }
        }
    }
}

@Composable
private fun ReportContent(
    type: ReportType,
    stats: ReportStats,
    count: Int,
    total: Int,
    avg: Int,
    max: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 统计卡片 2x2
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Default.Star, "次数", "$count", Modifier.weight(1f))
            StatCard(Icons.Default.Timer, "总时长", "$total 分钟", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Default.Speed, "平均时长", "$avg 分钟", Modifier.weight(1f))
            StatCard(Icons.Default.EmojiEvents, "单次最长", "$max 分钟", Modifier.weight(1f))
        }

        // 分布图
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "${type.label}分布（次数）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                if (stats.count == 0) {
                    Text(
                        text = "本周期暂无记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                } else {
                    BarChart(labels = stats.bucketLabels, values = stats.bucketCounts)
                }
            }
        }

        // 环比对比
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "对比上一${when (type) { ReportType.WEEK -> "周"; ReportType.MONTH -> "月"; ReportType.YEAR -> "年" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                val diff = stats.count - stats.prevCount
                val diffMin = stats.totalMin - stats.prevTotalMin
                val arrowIcon = when {
                    diff > 0 -> Icons.Default.TrendingUp
                    diff < 0 -> Icons.Default.TrendingDown
                    else -> Icons.Default.Remove
                }
                val arrowColor = when {
                    diff > 0 -> MaterialTheme.colorScheme.primary
                    diff < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val animDiff by animateIntAsState(diff, tween(500), label = "diff")
                val animDiffMin by animateIntAsState(diffMin, tween(500), label = "diffMin")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = arrowIcon,
                        contentDescription = null,
                        tint = arrowColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "次数 ${signed(animDiff)} · 时长 ${signed(animDiffMin)} 分钟" +
                            if (stats.prevCount > 0) "（${percent(diff, stats.prevCount)}）" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = arrowColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "日均 ${"%.1f".format(stats.totalMin.toDouble() / stats.periodDays)} 分钟 · 单次最短 ${stats.minMin} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 简易条形图：柱高按最大值归一化，切换数据时柱子从 0 生长 */
@Composable
private fun BarChart(labels: List<String>, values: List<Int>) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(80.dp * (v.toFloat() / max) * progress.value)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (v > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = labels[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/* ==================== 统计计算 ==================== */

private fun computeStats(records: List<SessionRecord>, type: ReportType): ReportStats {
    val start: Calendar
    val end: Calendar
    val prevStart: Calendar
    val prevEnd: Calendar

    fun dayStart(c: Calendar): Calendar = c.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    when (type) {
        ReportType.WEEK -> { // 本周一 00:00 起
            start = dayStart(Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
            })
            end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 7) }
            prevStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -7) }
            prevEnd = start
        }
        ReportType.MONTH -> {
            start = dayStart(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) })
            end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            prevStart = (start.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            prevEnd = start
        }
        ReportType.YEAR -> {
            start = dayStart(Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1) })
            end = (start.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
            prevStart = (start.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
            prevEnd = start
        }
    }

    val curStart = start.timeInMillis
    val curEnd = end.timeInMillis
    val cur = records.filter { it.timestamp in curStart until curEnd }
    val prev = records.filter { it.timestamp in prevStart.timeInMillis until curStart }

    val labels: List<String>
    val counts: MutableList<Int>
    val minutes: MutableList<Int>
    when (type) {
        ReportType.WEEK -> {
            labels = listOf("一", "二", "三", "四", "五", "六", "日")
            counts = MutableList(7) { 0 }
            minutes = MutableList(7) { 0 }
            cur.forEach { r ->
                val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
                val idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
                counts[idx]++
                minutes[idx] += r.durationMin
            }
        }
        ReportType.MONTH -> {
            val daysInMonth = (end.clone() as Calendar)
                .apply { add(Calendar.DAY_OF_MONTH, -1) }
                .get(Calendar.DAY_OF_MONTH)
            labels = (1..daysInMonth).map { if (daysInMonth > 15 && it % 2 == 0) "" else "$it" }
            counts = MutableList(daysInMonth) { 0 }
            minutes = MutableList(daysInMonth) { 0 }
            cur.forEach { r ->
                val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
                val idx = c.get(Calendar.DAY_OF_MONTH) - 1
                counts[idx]++
                minutes[idx] += r.durationMin
            }
        }
        ReportType.YEAR -> {
            labels = (1..12).map { "${it}月" }
            counts = MutableList(12) { 0 }
            minutes = MutableList(12) { 0 }
            cur.forEach { r ->
                val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
                counts[c.get(Calendar.MONTH)]++
                minutes[c.get(Calendar.MONTH)] += r.durationMin
            }
        }
    }

    val total = cur.sumOf { it.durationMin }
    return ReportStats(
        count = cur.size,
        totalMin = total,
        avgMin = if (cur.isNotEmpty()) (total.toDouble() / cur.size).roundToInt() else 0,
        maxMin = cur.maxOfOrNull { it.durationMin } ?: 0,
        minMin = cur.minOfOrNull { it.durationMin } ?: 0,
        bucketLabels = labels,
        bucketCounts = counts,
        bucketMinutes = minutes,
        prevCount = prev.size,
        prevTotalMin = prev.sumOf { it.durationMin },
        periodDays = ((curEnd - curStart) / 86_400_000L).toInt()
    )
}

private fun signed(n: Int) = if (n > 0) "+$n" else "$n"

private fun percent(diff: Int, base: Int): String =
    String.format("%+.0f%%", diff * 100.0 / base)