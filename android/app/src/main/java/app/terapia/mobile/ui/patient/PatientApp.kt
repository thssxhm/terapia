package app.terapia.mobile.ui.patient

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.terapia.mobile.MainActivity
import app.terapia.mobile.data.ChangeRequest
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Emergency
import app.terapia.mobile.data.Mood
import app.terapia.mobile.data.MoodEntry
import app.terapia.mobile.data.Patient
import app.terapia.mobile.data.PatientNote
import app.terapia.mobile.data.Prefs
import app.terapia.mobile.data.Profile
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.Session
import app.terapia.mobile.data.friendlyError
import app.terapia.mobile.data.statusLabel
import app.terapia.mobile.data.supabase
import app.terapia.mobile.ui.chat.ChatScreen
import app.terapia.mobile.ui.components.AsyncContent
import app.terapia.mobile.ui.components.EmptyState
import app.terapia.mobile.ui.components.Field
import app.terapia.mobile.ui.components.Logo
import app.terapia.mobile.ui.components.SectionCard
import app.terapia.mobile.ui.components.rememberAsync
import app.terapia.mobile.ui.psy.SettingsCard
import app.terapia.mobile.ui.theme.EmergencyRed
import app.terapia.mobile.ui.theme.EmergencyYellow
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

private enum class PTab(val label: String) { HOME("Início"), SESSIONS("Sessões"), MOOD("Humor"), CHAT("Mensagens"), SETTINGS("Ajustes") }

private data class PatientCtx(val patient: Patient, val psy: Profile?)

@Composable
fun PatientApp(activity: MainActivity, prefs: Prefs, profile: Profile) {
    val state = rememberAsync(profile.id) {
        val p = Repo.myPatientRecord()
        p?.let { PatientCtx(it, Repo.profile(it.psychologistId)) }
    }
    if (state.data == null && !state.loading && state.error == null) {
        NoPsychologist()
        return
    }
    AsyncContent(state) { ctx -> if (ctx == null) NoPsychologist() else PatientMain(activity, prefs, profile, ctx) }
}

@Composable
private fun NoPsychologist() {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Logo(100.dp)
        Spacer(Modifier.height(16.dp))
        Text("Seu acesso ainda não foi liberado por um psicólogo (ou foi removido).", textAlign = TextAlign.Center)
        TextButton(onClick = { scope.launch { supabase.auth.signOut() } }) { Text("Sair") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientMain(activity: MainActivity, prefs: Prefs, profile: Profile, ctx: PatientCtx) {
    val nav = rememberNavController()
    var tab by rememberSaveable { mutableStateOf(PTab.HOME) }

    val target by activity.notifTarget.collectAsState()
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        activity.notifTarget.value = null
        tab = when (t.type) {
            "message" -> PTab.CHAT
            "request_answer", "session_reminder" -> PTab.SESSIONS
            else -> PTab.HOME
        }
        nav.popBackStack("main", false)
    }

    NavHost(nav, "main") {
        composable("main") {
            Scaffold(
                topBar = {
                    if (tab != PTab.CHAT) TopAppBar(title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Logo(36.dp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                // Nome e CRP do psicólogo no topo do app
                                Text(ctx.psy?.fullName ?: "Seu psicólogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("CRP ${ctx.psy?.crp.orEmpty()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    })
                },
                bottomBar = {
                    NavigationBar {
                        PTab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t, onClick = { tab = t },
                                label = { Text(t.label, maxLines = 1) },
                                icon = {
                                    Icon(
                                        when (t) {
                                            PTab.HOME -> Icons.Default.Home
                                            PTab.SESSIONS -> Icons.Default.EventNote
                                            PTab.MOOD -> Icons.Default.EmojiEmotions
                                            PTab.CHAT -> Icons.AutoMirrored.Filled.Chat
                                            PTab.SETTINGS -> Icons.Default.Settings
                                        }, null,
                                    )
                                },
                            )
                        }
                    }
                },
            ) { padding ->
                Column(Modifier.padding(padding)) {
                    when (tab) {
                        PTab.HOME -> HomeTab(ctx, onSession = { nav.navigate("session/$it") }, onMood = { tab = PTab.MOOD })
                        PTab.SESSIONS -> SessionsTab(ctx) { nav.navigate("session/$it") }
                        PTab.MOOD -> MoodTab(ctx)
                        PTab.CHAT -> ChatScreen(ctx.patient.id, profile.id, ctx.psy?.fullName, onBack = null)
                        PTab.SETTINGS -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                            SettingsCard(activity, prefs, psychologist = false)
                        }
                    }
                }
            }
        }
        composable("session/{id}") { entry ->
            SessionDetailScreen(ctx, entry.arguments?.getString("id")!!, onBack = { nav.popBackStack() })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// INÍCIO: emergência, humor do dia, agenda mensal e próxima sessão
// ---------------------------------------------------------------------------------------------
@Composable
private fun HomeTab(ctx: PatientCtx, onSession: (String) -> Unit, onMood: () -> Unit) {
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(YearMonth.now()) }
    val moods = rememberAsync(month) { Repo.moods(ctx.patient.id, month.atDay(1), month.atEndOfMonth()) }
    val next = rememberAsync(Unit) {
        Repo.sessionsOf(ctx.patient.id).firstOrNull { Dates.instant(it.startsAt).isAfter(Instant.now()) && it.status != "cancelled" }
    }
    var editDay by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EmergencyButtons(ctx.patient.id)

        val todayEntry = moods.data?.firstOrNull { it.entryDate == LocalDate.now().toString() }
        SectionCard("Como você está hoje?") {
            MoodEditor(ctx.patient.id, LocalDate.now(), todayEntry) { moods.reload() }
        }

        SectionCard("Sua agenda de humor") {
            MoodMonthCalendar(month, moods.data.orEmpty(), onMonth = { month = it }, onDay = { editDay = it })
            TextButton(onClick = onMood) { Text("Ver gráfico da semana e enviar ao psicólogo") }
        }

        SectionCard("Próxima sessão") {
            val s = next.data
            if (s == null) Text(if (next.loading) "Carregando…" else "Nenhuma sessão agendada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Column(Modifier.clickable { onSession(s.id) }) {
                Text(Dates.full(s.startsAt), fontWeight = FontWeight.SemiBold)
                Text(statusLabel(s.status), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { onSession(s.id) }) { Text("Abrir sessão") }
            }
        }
    }

    editDay?.let { day ->
        AlertDialog(
            onDismissRequest = { editDay = null },
            title = { Text(Dates.date(day)) },
            text = { MoodEditor(ctx.patient.id, day, moods.data?.firstOrNull { it.entryDate == day.toString() }) { editDay = null; moods.reload() } },
            confirmButton = { TextButton(onClick = { editDay = null }) { Text("Fechar") } },
        )
    }
}

/** Escolha do emoji + anotação do dia. */
@Composable
private fun MoodEditor(patientId: String, day: LocalDate, current: MoodEntry?, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mood by remember(current) { mutableStateOf(current?.mood) }
    var note by remember(current) { mutableStateOf(current?.note.orEmpty()) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MoodPicker(mood) { mood = it }
        Field(note, { note = it }, "Anotações do dia (opcional)", minLines = 2)
        msg?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        Button(onClick = {
            scope.launch {
                msg = runCatching {
                    Repo.saveMood(MoodEntry(patientId = patientId, entryDate = day.toString(), mood = mood!!, note = note.ifBlank { null }))
                    onSaved(); "Registrado ✔ ${Mood.of(mood!!).emoji}"
                }.getOrElse { friendlyError(it) }
            }
        }, enabled = mood != null) { Text(if (current == null) "Registrar" else "Atualizar") }
    }
}

// ---------------------------------------------------------------------------------------------
// BOTÃO DE EMERGÊNCIA (amarelo = pouco urgente, vermelho = muito urgente)
// ---------------------------------------------------------------------------------------------
@Composable
private fun EmergencyButtons(patientId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var level by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(0) }    // 1 = confirmar, 2 = descrever, 3 = enviado
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { level = "yellow"; step = 1 },
            colors = ButtonDefaults.buttonColors(containerColor = EmergencyYellow, contentColor = Color.Black),
            modifier = Modifier.weight(1f).height(64.dp),
        ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Emergência", fontWeight = FontWeight.Bold); Text("pouco urgente", style = MaterialTheme.typography.labelSmall) } }
        Button(
            onClick = { level = "red"; step = 1 },
            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White),
            modifier = Modifier.weight(1f).height(64.dp),
        ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Emergência", fontWeight = FontWeight.Bold); Text("muito urgente", style = MaterialTheme.typography.labelSmall) } }
    }

    val red = level == "red"
    when (step) {
        1 -> AlertDialog(
            onDismissRequest = { step = 0 },
            icon = { Icon(Icons.Default.Warning, null, tint = if (red) EmergencyRed else EmergencyYellow) },
            title = { Text("Enviar emergência?") },
            text = { Text("Tem certeza que deseja enviar uma mensagem de emergência ${if (red) "MUITO URGENTE" else "pouco urgente"} ao seu psicólogo?") },
            confirmButton = { TextButton(onClick = { step = 2 }) { Text("Sim, enviar") } },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Não") } },
        )
        2 -> AlertDialog(
            onDismissRequest = { step = 0 },
            containerColor = if (red) Color(0xFFFFEBEE) else Color(0xFFFFF8E1),
            title = { Text("O que está acontecendo?", color = Color.Black) },
            text = {
                Column {
                    Field(text, { text = it }, "Descreva aqui", minLines = 4)
                    error?.let { Text(it, color = EmergencyRed) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    sending = true
                    scope.launch {
                        runCatching { Repo.sendEmergency(Emergency(patientId = patientId, level = level!!, message = text.trim())) }
                            .onSuccess { step = 3; text = "" }
                            .onFailure { error = friendlyError(it) }
                        sending = false
                    }
                }, enabled = text.isNotBlank() && !sending,
                    colors = ButtonDefaults.buttonColors(containerColor = if (red) EmergencyRed else EmergencyYellow, contentColor = if (red) Color.White else Color.Black),
                ) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancelar", color = Color.Black) } },
        )
        3 -> AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text("Mensagem enviada") },
            text = {
                Column {
                    Text("Seu psicólogo foi avisado. Se você estiver em risco imediato, ligue agora:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:192"))) }) { Text("192 — SAMU") }
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:188"))) }) { Text("188 — CVV (24h, gratuito)") }
                }
            },
            confirmButton = { TextButton(onClick = { step = 0 }) { Text("OK") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// SESSÕES (um bloco por sessão)
// ---------------------------------------------------------------------------------------------
@Composable
private fun SessionsTab(ctx: PatientCtx, onOpen: (String) -> Unit) {
    var past by remember { mutableStateOf(false) }
    val state = rememberAsync(Unit) { Repo.sessionsOf(ctx.patient.id) to Repo.patientNotes(ctx.patient.id).associateBy { it.sessionId } }
    Column {
        Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!past, { past = false }, label = { Text("Próximas") })
            FilterChip(past, { past = true }, label = { Text("Anteriores") })
        }
        AsyncContent(state) { (sessions, notes) ->
            val now = Instant.now().minusSeconds(3600)
            val list = if (past) sessions.filter { !Dates.instant(it.startsAt).isAfter(now) }.reversed()
            else sessions.filter { Dates.instant(it.startsAt).isAfter(now) }
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.isEmpty()) item { EmptyState("Nenhuma sessão.") }
                items(list, key = { it.id }) { s ->
                    val n = notes[s.id]
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(s.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (s.status == "cancelled") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(n?.title?.ifBlank { null } ?: "Sessão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(Dates.full(s.startsAt))
                            Text(statusLabel(s.status), style = MaterialTheme.typography.labelMedium)
                            if (!n?.preNote.isNullOrBlank() || !n?.postNote.isNullOrBlank()) {
                                Text("📝 com anotações", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailScreen(ctx: PatientCtx, sessionId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberAsync(sessionId) {
        Triple(
            Repo.session(sessionId),
            Repo.patientNotes(ctx.patient.id).firstOrNull { it.sessionId == sessionId },
            Repo.requestsOf(ctx.patient.id).filter { it.sessionId == sessionId },
        )
    }
    var requestKind by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Sessão") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        )
    }) { padding ->
        AsyncContent(state) { (s, note, requests) ->
            var title by remember { mutableStateOf(note?.title.orEmpty()) }
            var pre by remember { mutableStateOf(note?.preNote.orEmpty()) }
            var post by remember { mutableStateOf(note?.postNote.orEmpty()) }
            var msg by remember { mutableStateOf<String?>(null) }
            val future = Dates.instant(s.startsAt).isAfter(Instant.now())
            val pending = requests.firstOrNull { it.status == "pending" }
            Column(
                Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard {
                    Text(Dates.full(s.startsAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${statusLabel(s.status)} • ${s.durationMinutes} min")
                    if (pending != null) AssistChip(onClick = {}, label = { Text("Pedido enviado — aguardando o psicólogo") })
                    requests.firstOrNull { it.status != "pending" && it.kind != "confirm" }?.let {
                        Text("Último pedido: " + if (it.status == "accepted") "aceito ✔" else "recusado", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Field(title, { title = it }, "Nome desta sessão (opcional)")
                Field(pre, { pre = it }, "Anotações pré-sessão (o que quero falar)", minLines = 3)
                Field(post, { post = it }, "Anotações pós-sessão (o que ficou)", minLines = 3)
                Text("Suas anotações são privadas: só você vê.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                msg?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                Button(onClick = {
                    scope.launch {
                        msg = runCatching {
                            Repo.savePatientNote(PatientNote(s.id, ctx.patient.id, title.ifBlank { null }, pre, post)); "Salvo ✔"
                        }.getOrElse { friendlyError(it) }
                    }
                }, Modifier.fillMaxWidth()) { Text("Salvar anotações") }

                if (future && s.status != "cancelled" && pending == null) {
                    SectionCard("Precisa de algo?") {
                        if (s.status == "scheduled") OutlinedButton(onClick = { requestKind = "confirm" }, Modifier.fillMaxWidth()) { Text("Confirmar presença") }
                        OutlinedButton(onClick = { requestKind = "reschedule" }, Modifier.fillMaxWidth()) { Text("Pedir alteração de data/horário") }
                        OutlinedButton(onClick = { requestKind = "cancel" }, Modifier.fillMaxWidth()) { Text("Pedir cancelamento", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            requestKind?.let { kind ->
                RequestDialog(kind, onDismiss = { requestKind = null }, onSend = { message ->
                    scope.launch {
                        runCatching { Repo.sendRequest(ChangeRequest(patientId = ctx.patient.id, sessionId = s.id, kind = kind, message = message)) }
                            .onSuccess { requestKind = null; state.reload() }
                            .onFailure { msg = friendlyError(it) }
                    }
                })
            }
        }
    }
}

@Composable
private fun RequestDialog(kind: String, onDismiss: () -> Unit, onSend: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (kind) { "confirm" -> "Confirmar presença"; "cancel" -> "Pedir cancelamento"; else -> "Pedir alteração" }) },
        text = {
            Column {
                Text(
                    when (kind) {
                        "confirm" -> "Seu psicólogo será avisado de que você vai comparecer."
                        "cancel" -> "Seu psicólogo será avisado e precisa aprovar o cancelamento."
                        else -> "Seu psicólogo será avisado de que você precisa alterar a data. Sugira dias/horários:"
                    }
                )
                if (kind != "confirm") {
                    Spacer(Modifier.height(8.dp))
                    Field(text, { text = it }, if (kind == "cancel") "Motivo (opcional)" else "Ex.: posso quinta às 16h", minLines = 2)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSend(text.ifBlank { null }) }) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------------------------
// HUMOR: gráfico semanal + enviar ao psicólogo
// ---------------------------------------------------------------------------------------------
@Composable
private fun MoodTab(ctx: PatientCtx) {
    val scope = rememberCoroutineScope()
    var weekStart by remember { mutableStateOf(Dates.weekStart(LocalDate.now())) }
    val state = rememberAsync(weekStart) {
        Repo.moods(ctx.patient.id, weekStart, weekStart.plusDays(6)) to
            Repo.moodShares(ctx.patient.id).any { it.weekStart == weekStart.toString() }
    }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Text("◀ Anterior") }
            Text("${Dates.dayMonth(weekStart)} – ${Dates.dayMonth(weekStart.plusDays(6))}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { weekStart = weekStart.plusWeeks(1) }, enabled = weekStart < Dates.weekStart(LocalDate.now())) { Text("Próxima ▶") }
        }
        AsyncContent(state) { (entries, shared) ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard("Seu humor na semana") {
                    MoodWeekChart(weekStart, entries)
                    if (entries.isNotEmpty()) {
                        val avg = entries.map { it.mood }.average()
                        Text("Média da semana: ${Mood.of(Math.round(avg).toInt()).emoji} ${Mood.of(Math.round(avg).toInt()).label}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (entries.any { !it.note.isNullOrBlank() }) SectionCard("Anotações") {
                    entries.filter { !it.note.isNullOrBlank() }.forEach {
                        Text("${Mood.of(it.mood).emoji} ${Dates.dayMonth(LocalDate.parse(it.entryDate))}: ${it.note}")
                    }
                }
                when {
                    shared -> Text("✔ Gráfico desta semana enviado ao seu psicólogo.", color = MaterialTheme.colorScheme.secondary)
                    entries.isEmpty() -> Text("Registre seu humor no Início para montar o gráfico.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Button(onClick = { confirm = true }, Modifier.fillMaxWidth()) { Text("Enviar gráfico ao psicólogo") }
                }
                msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "O envio é opcional. Seu psicólogo só vê o humor das semanas que você enviar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Enviar gráfico?") },
            text = { Text("Seu psicólogo verá os emojis e anotações desta semana.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        runCatching { Repo.shareMoodWeek(ctx.patient.id, weekStart) }
                            .onSuccess { state.reload() }.onFailure { msg = friendlyError(it) }
                    }
                }) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
