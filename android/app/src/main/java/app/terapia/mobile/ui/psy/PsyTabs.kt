package app.terapia.mobile.ui.psy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.terapia.mobile.data.AppNotification
import app.terapia.mobile.data.ChangeRequest
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Patient
import app.terapia.mobile.data.Profile
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.Session
import app.terapia.mobile.data.friendlyError
import app.terapia.mobile.data.statusLabel
import app.terapia.mobile.notifications.Reminders
import app.terapia.mobile.ui.components.AsyncContent
import app.terapia.mobile.ui.components.Dropdown
import app.terapia.mobile.ui.components.EmptyState
import app.terapia.mobile.ui.components.SectionCard
import app.terapia.mobile.ui.components.rememberAsync
import app.terapia.mobile.ui.theme.EmergencyRed
import app.terapia.mobile.ui.theme.EmergencyYellow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/** Limite efetivo: plano pago ou 10 pacientes durante o teste grátis. */
fun effectiveLimit(p: Profile): Int {
    val trial = p.trialUntil?.let { runCatching { OffsetDateTime.parse(it).toInstant().isAfter(Instant.now()) }.getOrDefault(false) } ?: false
    return maxOf(p.patientLimit, if (trial) 10 else 0)
}

// ---------------------------------------------------------------------------------------------
// PACIENTES
// ---------------------------------------------------------------------------------------------
@Composable
fun PatientsTab(profile: Profile, onOpen: (String) -> Unit, onChat: (String) -> Unit, onAdd: () -> Unit) {
    val state = rememberAsync(Unit) { Repo.myPatients() }
    var query by remember { mutableStateOf("") }
    var limitDialog by remember { mutableStateOf(false) }
    val limit = effectiveLimit(profile)

    Box(Modifier.fillMaxSize()) {
        AsyncContent(state) { patients ->
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SectionCard {
                        Text("${patients.size} de $limit pacientes do seu plano", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { if (limit == 0) 1f else patients.size / limit.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar paciente") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    )
                }
                val filtered = patients.filter { it.fullName.contains(query, ignoreCase = true) }
                if (filtered.isEmpty()) item { EmptyState("Nenhum paciente ainda. Toque em \"Novo paciente\".") }
                items(filtered, key = { it.id }) { p ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(p.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        ListItem(
                            headlineContent = { Text(p.fullName, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                val slots = p.slots.orEmpty().sortedWith(compareBy({ it.weekday }, { it.startTime }))
                                Text(
                                    if (slots.isEmpty()) "Sem horário fixo"
                                    else slots.joinToString(" • ") { "${Dates.weekdayShort(it.weekday)} ${Dates.hhmm(it.startTime)}" },
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onChat(p.id) }) { Icon(Icons.AutoMirrored.Filled.Chat, "Mensagens") }
                            },
                        )
                    }
                }
            }
            if (limitDialog) {
                AlertDialog(
                    onDismissRequest = { limitDialog = false },
                    title = { Text("Limite do plano atingido") },
                    text = { Text("Seu plano permite $limit pacientes. Amplie o plano em Perfil > Plano (vendido de 10 em 10 pacientes).") },
                    confirmButton = { TextButton(onClick = { limitDialog = false }) { Text("OK") } },
                )
            }
            ExtendedFloatingActionButton(
                onClick = { if (patients.size >= limit) limitDialog = true else onAdd() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Novo paciente") },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// VISÃO SEMANAL (dia da semana + horário de cada paciente)
// ---------------------------------------------------------------------------------------------
@Composable
fun WeekTab(onOpen: (String) -> Unit) {
    val state = rememberAsync(Unit) { Repo.myPatients() }
    AsyncContent(state) { patients ->
        val entries = patients.flatMap { p -> p.slots.orEmpty().filter { it.active }.map { p to it } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (entries.isEmpty()) item { EmptyState("Cadastre horários fixos nos pacientes para ver a semana aqui.") }
            (1..7).forEach { day ->
                val list = entries.filter { it.second.weekday == day }.sortedBy { it.second.startTime }
                if (list.isNotEmpty()) item(key = "day$day") {
                    SectionCard(Dates.weekdayName(day)) {
                        list.forEachIndexed { i, (p, slot) ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(Dates.hhmm(slot.startTime), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
                                Text(p.fullName, Modifier.weight(1f))
                                Text("${slot.durationMinutes} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// AGENDA GERAL
// ---------------------------------------------------------------------------------------------
@Composable
fun AgendaTab(onOpen: (String) -> Unit) {
    var past by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Session?>(null) }
    val state = rememberAsync(past) {
        val now = Instant.now()
        if (past) Repo.sessionsBetween(now.minus(90, ChronoUnit.DAYS), now).reversed()
        else Repo.sessionsBetween(now.minus(2, ChronoUnit.HOURS), now.plus(90, ChronoUnit.DAYS))
    }
    Column {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!past, { past = false }, label = { Text("Próximas") })
            FilterChip(past, { past = true }, label = { Text("Anteriores") })
        }
        AsyncContent(state) { sessions ->
            val byDay = sessions.groupBy { Dates.local(it.startsAt).toLocalDate() }
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sessions.isEmpty()) item { EmptyState("Nenhum atendimento.") }
                byDay.forEach { (day, list) ->
                    item(key = day.toString()) {
                        val label = when (day) {
                            LocalDate.now() -> "Hoje"
                            LocalDate.now().plusDays(1) -> "Amanhã"
                            else -> "${Dates.weekdayName(day.dayOfWeek.value)}, ${Dates.date(day)}"
                        }
                        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(list, key = { it.id }) { s -> SessionRow(s, showPatient = true, onClick = { editing = s }, onPatient = { onOpen(s.patientId) }) }
                }
            }
        }
    }
    editing?.let { s ->
        SessionEditDialog(s, onDismiss = { editing = null }, onSaved = { editing = null; state.reload() })
    }
}

@Composable
fun SessionRow(s: Session, showPatient: Boolean, onClick: () -> Unit, onPatient: (() -> Unit)? = null) {
    val cancelled = s.status == "cancelled"
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (cancelled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Dates.time(s.startsAt), fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
            Column(Modifier.weight(1f)) {
                if (showPatient) Text(s.patients?.fullName ?: "Paciente", fontWeight = FontWeight.SemiBold)
                else Text(Dates.full(s.startsAt))
                Text("${statusLabel(s.status)} • ${s.durationMinutes} min", style = MaterialTheme.typography.labelMedium)
            }
            if (onPatient != null) TextButton(onClick = onPatient) { Text("Ver") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// AVISOS (confirmações, cancelamentos, pedidos de ajuste, emergências, mensagens, humor)
// ---------------------------------------------------------------------------------------------
@Composable
fun NoticesTab(refreshKey: Int, onChanged: () -> Unit, onOpenPatient: (String) -> Unit, onOpenChat: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberAsync(refreshKey) { Repo.notifications() }
    var requestFor by remember { mutableStateOf<AppNotification?>(null) }

    AsyncContent(state) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Avisos dos pacientes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { runCatching { Repo.markAllNotificationsRead() }; state.reload(); onChanged() } }) {
                        Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(4.dp)); Text("Marcar lidas")
                    }
                }
            }
            if (list.isEmpty()) item { EmptyState("Nenhum aviso por enquanto.") }
            items(list, key = { it.id }) { n ->
                val color = when (n.type) {
                    "emergency_red" -> EmergencyRed.copy(alpha = .18f)
                    "emergency_yellow" -> EmergencyYellow.copy(alpha = .25f)
                    else -> if (n.readAt == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                }
                Card(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch { runCatching { Repo.markNotificationRead(n.id) }; onChanged() }
                        when {
                            n.type == "request" -> requestFor = n
                            n.type == "message" || n.type.startsWith("emergency") -> n.patientId?.let(onOpenChat)
                            else -> n.patientId?.let(onOpenPatient)
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = color),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title, fontWeight = if (n.readAt == null) FontWeight.Bold else FontWeight.Normal)
                            n.body?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text(Dates.relative(n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (n.type == "request") AssistChip(onClick = { requestFor = n }, label = { Text("Responder pedido") })
                        }
                        IconButton(onClick = { scope.launch { runCatching { Repo.deleteNotification(n.id) }; state.reload(); onChanged() } }) {
                            Icon(Icons.Default.Delete, "Apagar")
                        }
                    }
                }
            }
        }
    }

    requestFor?.let { n -> RequestDialog(n, onDismiss = { requestFor = null; state.reload() }) }
}

/** Responder pedido de cancelamento/ajuste: aceitar, recusar ou remarcar. */
@Composable
private fun RequestDialog(n: AppNotification, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val req = rememberAsync(n.refId) { n.refId?.let { Repo.request(it) } }
    var session by remember { mutableStateOf<Session?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val r: ChangeRequest? = req.data

    fun answer(accepted: Boolean, cancelSession: Boolean = false) = scope.launch {
        runCatching {
            if (cancelSession && r?.sessionId != null) Repo.setSessionStatus(r.sessionId, "cancelled")
            Repo.answerRequest(r!!.id!!, accepted)
        }.onSuccess { onDismiss() }.onFailure { error = friendlyError(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(n.title) },
        text = {
            Column {
                n.body?.let { Text(it) }
                if (r != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Situação: " + when (r.status) { "accepted" -> "aceito"; "declined" -> "recusado"; else -> "pendente" }, fontWeight = FontWeight.SemiBold)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (r?.status == "pending") {
                    Spacer(Modifier.height(12.dp))
                    when (r.kind) {
                        "cancel" -> Button(onClick = { answer(true, cancelSession = true) }, Modifier.fillMaxWidth()) { Text("Aceitar e cancelar sessão") }
                        "reschedule" -> Button(onClick = {
                            scope.launch { session = r.sessionId?.let { runCatching { Repo.session(it) }.getOrNull() } }
                        }, Modifier.fillMaxWidth()) { Text("Remarcar sessão") }
                    }
                    OutlinedButton(onClick = { answer(false) }, Modifier.fillMaxWidth()) { Text("Recusar") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
    session?.let { s ->
        SessionEditDialog(s, onDismiss = { session = null }, onSaved = { session = null; answer(true) })
    }
}

// ---------------------------------------------------------------------------------------------
// LEMBRETES (sininho por paciente)
// ---------------------------------------------------------------------------------------------
@Composable
fun RemindersTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberAsync(Unit) { Repo.myPatients() }
    AsyncContent(state) { patients ->
        var local by remember(patients) { mutableStateOf(patients) }
        fun update(p: Patient, enabled: Boolean, minutes: Int) {
            local = local.map { if (it.id == p.id) it.copy(reminderEnabled = enabled, reminderMinutes = minutes) else it }
            scope.launch {
                runCatching { Repo.setReminder(p.id, enabled, minutes) }
                Reminders.start(context)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "Toque no sininho para ser lembrado(a) dos atendimentos de cada paciente. Opcional.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (local.isEmpty()) item { EmptyState("Nenhum paciente cadastrado.") }
            items(local, key = { it.id }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.fullName, fontWeight = FontWeight.SemiBold)
                            if (p.reminderEnabled) {
                                Dropdown(
                                    "Avisar antes",
                                    listOf(15 to "15 minutos", 30 to "30 minutos", 60 to "1 hora", 120 to "2 horas", 1440 to "1 dia"),
                                    p.reminderMinutes,
                                    { update(p, true, it) },
                                    Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        IconButton(onClick = { update(p, !p.reminderEnabled, p.reminderMinutes) }) {
                            Icon(
                                if (p.reminderEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                if (p.reminderEnabled) "Lembrete ligado" else "Lembrete desligado",
                                tint = if (p.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}
