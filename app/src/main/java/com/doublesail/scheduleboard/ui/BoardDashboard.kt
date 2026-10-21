package com.doublesail.scheduleboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublesail.scheduleboard.schedule.ScheduleEvent
import com.doublesail.scheduleboard.schedule.countdownText
import com.doublesail.scheduleboard.tasks.BoardTask
import java.time.format.DateTimeFormatter
import kotlin.math.min

/** Native Compose layout in the proportions of the supplied 16:9 design. */
@Composable
fun BoardDashboard(
    state: BoardUiState,
    onOpenSettings: () -> Unit,
    onAddTask: () -> Unit,
    onTaskCompleted: (String, Boolean) -> Unit,
    onEventClick: (ScheduleEvent) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.secondaryContainer, .48f))) {
        val unit = min(maxWidth.value / 1920f, maxHeight.value / 1080f)
        Surface(
            modifier = Modifier.fillMaxSize().padding(horizontal = (59 * unit).dp, vertical = (60 * unit).dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(Modifier.fillMaxSize().padding(start = (50 * unit).dp, end = (40 * unit).dp,
                top = (20 * unit).dp, bottom = (74 * unit).dp)) {
                Column(Modifier.weight(2.8f).fillMaxHeight()) {
                    Text(
                        state.now.format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = (256 * unit).sp, lineHeight = (270 * unit).sp,
                        letterSpacing = (-7 * unit).sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1,
                        modifier = Modifier.offset(x = (-15 * unit).dp, y = (-18 * unit).dp).combinedClickable(
                            onClickLabel = "打开设置", onClick = onOpenSettings,
                            onLongClickLabel = "打开设置", onLongClick = onOpenSettings,
                        ),
                    )
                    state.syncWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.height((466 * unit).dp), verticalArrangement = Arrangement.spacedBy((26 * unit).dp)) {
                        if (state.events.isEmpty()) {
                            Column(Modifier.fillMaxSize().padding((35 * unit).dp), verticalArrangement = Arrangement.Center) {
                                Text(if (state.schedule.sourceName.isEmpty()) "导入你的课表" else "接下来的安排已全部结束",
                                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text(state.schedule.error ?: if (state.schedule.sourceName.isEmpty())
                                    "点击时钟登录教务并同步课表，也可以导入 ICS" else "可以安心休息，或处理右侧待办",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            state.events.forEach { event ->
                                CourseCard(event, countdownText(event, state.now), unit,
                                    Modifier.weight(1f), onClick = { onEventClick(event) })
                            }
                            if (state.events.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.width((60 * unit).dp))
                Box(Modifier.width((12 * unit).dp).fillMaxHeight().padding(top = (46 * unit).dp, bottom = (20 * unit).dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .6f), RoundedCornerShape(100)))
                Spacer(Modifier.width((22 * unit).dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (state.tasks.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                            Text(state.taskError ?: "还没有待办", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onAddTask) { Text("添加待办") }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(state.tasks, key = { it.id }) { task -> TaskRow(task, unit, onTaskCompleted) }
                        }
                    }
                    TextButton(onClick = onAddTask, modifier = Modifier.align(Alignment.End).height(40.dp)) {
                        Text("＋", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(event: ScheduleEvent, countdown: String, unit: Float, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape((52 * unit).dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.fillMaxSize().padding(start = (52 * unit).dp, end = (80 * unit).dp,
            top = (25 * unit).dp, bottom = (25 * unit).dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((36 * unit).dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((12 * unit).dp)) {
                Text(event.title, fontSize = (60 * unit).sp, lineHeight = (74 * unit).sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOf(event.location, event.teacher).filter(String::isNotBlank).joinToString(" · ")
                Text(detail.ifEmpty { event.start.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) },
                    fontSize = (46 * unit).sp, lineHeight = (58 * unit).sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val glyphs = countdown.sumOf { if (it.code < 128) .56 else 1.0 }.toFloat().coerceAtLeast(1f)
            val textSize = min(48f, 328f / glyphs) * unit
            Box(Modifier.width((400 * unit).dp).height((98 * unit).dp)
                .background(MaterialTheme.colorScheme.onPrimaryContainer, RoundedCornerShape(100)), contentAlignment = Alignment.Center) {
                Text(countdown, fontSize = textSize.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TaskRow(task: BoardTask, unit: Float, onTaskCompleted: (String, Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().height((188 * unit).dp).sizeIn(minHeight = 48.dp)
                .toggleable(value = task.completed, role = Role.Checkbox,
                    onValueChange = { onTaskCompleted(task.id, it) }).padding(vertical = (4 * unit).dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((18 * unit).dp),
        ) {
            Text(task.title, modifier = Modifier.weight(1f), fontSize = (55 * unit).sp, lineHeight = (70 * unit).sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null)
            Checkbox(task.completed, onCheckedChange = null,
                colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedColor = MaterialTheme.colorScheme.primary))
        }
        val color = MaterialTheme.colorScheme.secondaryContainer
        Canvas(Modifier.fillMaxWidth().height((4 * unit).dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf((3 * unit).dp.toPx(), (5 * unit).dp.toPx())))
        }
    }
}
