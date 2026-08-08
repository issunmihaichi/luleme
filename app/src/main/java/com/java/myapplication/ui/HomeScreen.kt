package com.java.myapplication.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.java.myapplication.data.SessionRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 年月日键，month 为 0-based，与 Calendar 一致 */
data class DayKey(val year: Int, val month: Int, val day: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    records: List<SessionRecord>,
    onAdd: (SessionRecord) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = Calendar.getInstance()
    var shownYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var shownMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var selected by remember { mutableStateOf<DayKey?>(null) }

    fun shiftMonth(delta: Int) {
        var y = shownYear
        var m = shownMonth + delta
        if (m < 0) { m += 12; y -= 1 } else if (m > 11) { m -= 12; y += 1 }
        shownYear = y
        shownMonth = m
    }

    val monthRecords = records.filter { r ->
        val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
        c.get(Calendar.YEAR) == shownYear && c.get(Calendar.MONTH) == shownMonth
    }
    val shownRecords = if (selected == null) monthRecords else monthRecords.filter { r ->
        val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
        DayKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)) == selected
    }

    Column(modifier = modifier.fillMaxSize()) {
        MonthCalendar(
            year = shownYear,
            month = shownMonth,
            records = monthRecords,
            selected = selected,
            onPrev = { shiftMonth(-1) },
            onNext = { shiftMonth(1) },
            onSelectDay = { key -> selected = if (selected == key) null else key }
        )

        // 月度小结
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本月 ${monthRecords.size} 次 · 共 ${monthRecords.sumOf { it.durationMin }} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 记录列表
        Text(
            text = if (selected == null) "🦌 记录（${shownRecords.size} 条）"
            else "${selected!!.month + 1}月${selected!!.day}日 的记录（${shownRecords.size} 条）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        if (shownRecords.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "还没有记录\n点击右下角 ➕ 记录一次 🦌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 96.dp
                )
            ) {
                items(shownRecords, key = { it.id }) { record ->
                    RecordItem(
                        record = record,
                        onDelete = { onDelete(record.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

/* ==================== 月份表 ==================== */

@Composable
private fun MonthCalendar(
    year: Int,
    month: Int,
    records: List<SessionRecord>,
    selected: DayKey?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectDay: (DayKey) -> Unit
) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)          // 1=周日
    val offset = (firstDow + 5) % 7                        // 周一起始的偏移
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7  // 补齐整周

    val today = Calendar.getInstance()
    val todayKey = DayKey(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
    val recordDays = records.map { r ->
        val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
        DayKey(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }.toSet()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            // 月份标题 + 切换（标题带滚动动画）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = year * 100 + month,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically { -it } + fadeOut(tween(150)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "monthTitle"
                ) { key ->
                    Text(
                        text = "${key / 100}年 ${key % 100 + 1}月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "上个月")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "下个月")
                }
            }
            // 星期头（周一 ~ 周日）
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                    Text(
                        text = w,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // 日期格子
            for (row in 0 until totalCells / 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val d = row * 7 + col - offset + 1
                        if (d in 1..daysInMonth) {
                            val key = DayKey(year, month, d)
                            DayCell(
                                modifier = Modifier.weight(1f),
                                day = d,
                                hasRecord = key in recordDays,
                                isToday = key == todayKey,
                                isSelected = key == selected,
                                onClick = { onSelectDay(key) }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    day: Int,
    hasRecord: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        hasRecord -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        hasRecord -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bgColor by animateColorAsState(bg, tween(250), label = "dayBg")
    val fgColor by animateColorAsState(fg, tween(250), label = "dayFg")
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .then(
                    if (isToday && !isSelected)
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$day",
                style = MaterialTheme.typography.bodySmall,
                color = fgColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/* ==================== 记录条目 ==================== */

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun formatTime(ts: Long): String = timeFmt.format(Date(ts))

private fun normalizeUrl(raw: String): String =
    if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"

@Composable
private fun RecordItem(
    record: SessionRecord,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    Card(modifier = modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTime(record.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (record.note.isNotBlank()) {
                    Text(
                        text = record.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (record.url.isNotBlank()) {
                    Text(
                        text = record.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { uriHandler.openUri(normalizeUrl(record.url)) }
                    )
                }
            }
            // 时长徽标
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${record.durationMin} 分钟",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ==================== 添加对话框 ==================== */

@Composable
fun AddRecordDialog(
    onDismiss: () -> Unit,
    onSave: (SessionRecord) -> Unit
) {
    var minutes by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val minutesInt = minutes.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录一次 🦌") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("时长（分钟）") },
                    singleLine = true,
                    isError = minutes.isNotEmpty() && minutesInt <= 0,
                    supportingText = if (minutes.isNotEmpty() && minutesInt <= 0) {
                        { Text("时长必须大于 0") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("网页链接（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (minutesInt > 0) {
                        onSave(
                            SessionRecord(
                                id = System.currentTimeMillis(),
                                timestamp = System.currentTimeMillis(),
                                durationMin = minutesInt,
                                note = note.trim(),
                                url = url.trim()
                            )
                        )
                        onDismiss()
                    }
                },
                enabled = minutesInt > 0
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}