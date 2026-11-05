package app.terapia.mobile.ui.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Mood
import app.terapia.mobile.data.MoodEntry
import app.terapia.mobile.ui.theme.Lilac
import app.terapia.mobile.ui.theme.Sky
import java.time.LocalDate
import java.time.YearMonth

/** Seletor com os 5 emojis de humor. */
@Composable
fun MoodPicker(selected: Int?, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Mood.entries.forEach { m ->
            val active = selected == m.value
            Column(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .border(if (active) 2.dp else 0.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .clickable { onSelect(m.value) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(m.emoji, fontSize = 32.sp)
                Text(m.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.size(width = 56.dp, height = 30.dp))
            }
        }
    }
}

/** Gráfico semanal de humor (barras de segunda a domingo). */
@Composable
fun MoodWeekChart(weekStart: LocalDate, entries: List<MoodEntry>) {
    val byDate = entries.associateBy { it.entryDate }
    val maxHeight = 120.dp
    Row(
        Modifier.fillMaxWidth().height(maxHeight + 56.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        (0..6).forEach { i ->
            val day = weekStart.plusDays(i.toLong())
            val e = byDate[day.toString()]
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(e?.let { Mood.of(it.mood).emoji } ?: "·", fontSize = 18.sp)
                Box(
                    Modifier.size(width = 22.dp, height = if (e == null) 4.dp else maxHeight * (e.mood / 5f))
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(
                            if (e == null) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = .3f), MaterialTheme.colorScheme.outline.copy(alpha = .3f)))
                            else Brush.verticalGradient(listOf(Lilac, Sky))
                        )
                )
                Text(Dates.weekdayShort(day.dayOfWeek.value), style = MaterialTheme.typography.labelSmall)
                Text(Dates.dayMonth(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Agenda mensal com o emoji de cada dia. */
@Composable
fun MoodMonthCalendar(month: YearMonth, entries: List<MoodEntry>, onMonth: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit) {
    val byDate = entries.associateBy { it.entryDate }
    val today = LocalDate.now()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior") }
            Text(Dates.monthYear(month.atDay(1)), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { Text(Dates.weekdayShort(it), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        (0 until rows).forEach { r ->
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { c ->
                    val dayNum = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val e = byDate[date.toString()]
                            val isToday = date == today
                            Column(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .clickable(enabled = !date.isAfter(today)) { onDay(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (e != null) Text(Mood.of(e.mood).emoji, fontSize = 18.sp)
                                Text("$dayNum", style = MaterialTheme.typography.labelSmall,
                                    color = if (date.isAfter(today)) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
