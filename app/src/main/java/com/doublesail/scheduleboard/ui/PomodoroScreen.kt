package com.doublesail.scheduleboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doublesail.scheduleboard.pomodoro.PomodoroPhase
import com.doublesail.scheduleboard.pomodoro.PomodoroState
import kotlin.math.ceil

@Composable
fun PomodoroScreen(state: PomodoroState, remainingMs: Long, onToggle: () -> Unit, onReset: () -> Unit,
    onSelect: (PomodoroPhase, Int) -> Unit, onNext: () -> Unit, onBack: () -> Unit,
    precise: Boolean = true, onExactAlarmSettings: () -> Unit = {}, notifications: Boolean = true,
    onNotifications: () -> Unit = {}) {
    var editing by remember { mutableStateOf(false) }
    var minutes by remember(state.durationMs) { mutableStateOf((state.durationMs / 60_000).toString()) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BoxWithConstraints(Modifier.weight(1.15f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val diameter = minOf(maxWidth, maxHeight) - 8.dp
                val progress = (remainingMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                val primary = MaterialTheme.colorScheme.primary
                val track = MaterialTheme.colorScheme.surfaceContainerHighest
                val seconds = ceil(remainingMs / 1000.0).toLong()
                val time = "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
                Box(Modifier.size(diameter).semantics { contentDescription = "${state.phase.label}剩余 $time" }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        val stroke = 10.dp.toPx()
                        val inset = stroke / 2
                        val ringSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, -90f, 360f, false, Offset(inset, inset), ringSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        if (progress > 0) drawArc(primary, -90f, 360f * progress, false, Offset(inset, inset), ringSize,
                            style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (state.finished) "时间到" else state.phase.label, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(time, fontSize = (diameter.value * .205f).sp, fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.SansSerif, color = primary)
                        Text(if (state.running) "保持专注" else if (state.finished) "做得好，放松一下" else "准备好再出发",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("番茄钟", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onBack) { Text("返回日程") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PomodoroPhase.entries.forEach { phase ->
                        FilterChip(selected = state.phase == phase, onClick = { onSelect(phase, phase.minutes) },
                            label = { Text(phase.label) })
                    }
                }
                Text("已完成 ${state.completedFocus} 次专注", style = MaterialTheme.typography.bodyLarge)
                Text("每 4 次专注后长休息；下一阶段由你手动开始。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Button(onClick = onToggle, modifier = Modifier.fillMaxWidth().height(60.dp), shape = MaterialTheme.shapes.extraLarge) {
                    Text(if (state.running) "暂停" else if (state.finished) "再来一次" else if (remainingMs < state.durationMs) "继续" else "开始专注".takeIf { state.phase == PomodoroPhase.FOCUS } ?: "开始休息",
                        style = MaterialTheme.typography.titleLarge)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onReset) { Text("重置") }
                    TextButton(onClick = onNext) { Text("下一阶段") }
                    TextButton(onClick = { editing = true }, enabled = !state.running) { Text("时长") }
                }
                if (!precise) TextButton(onClick = onExactAlarmSettings) { Text("允许精确提醒，避免后台延迟") }
                if (!notifications) TextButton(onClick = onNotifications) { Text("开启结束提醒通知") }
            }
        }
        if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("${state.phase.label}时长") },
            text = { OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("分钟（1–180）") }, singleLine = true) },
            confirmButton = { TextButton(enabled = minutes.toIntOrNull() in 1..180, onClick = {
                onSelect(state.phase, minutes.toInt()); editing = false
            }) { Text("确定") } }, dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } })
    }
}
