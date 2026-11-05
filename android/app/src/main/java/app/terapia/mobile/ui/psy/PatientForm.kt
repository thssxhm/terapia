package app.terapia.mobile.ui.psy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.terapia.mobile.BuildConfig
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.InviteRequest
import app.terapia.mobile.data.InviteResponse
import app.terapia.mobile.data.NewSession
import app.terapia.mobile.data.Profile
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.Session
import app.terapia.mobile.data.Slot
import app.terapia.mobile.data.friendlyError
import app.terapia.mobile.ui.components.ConfirmDialog
import app.terapia.mobile.ui.components.DatePickerDialogBox
import app.terapia.mobile.ui.components.Dropdown
import app.terapia.mobile.ui.components.Field
import app.terapia.mobile.ui.components.SectionCard
import app.terapia.mobile.ui.components.TimePickerDialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private val WEEKDAYS = (1..7).map { it to Dates.weekdayName(it) }
private val DURATIONS = listOf(30, 40, 45, 50, 60, 90).map { it to "$it min" }

/** Cadastro (patientId = null) ou edição de paciente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientFormScreen(patientId: String?, profile: Profile, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val slots = remember { mutableStateListOf<Slot>() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf<InviteResponse?>(null) }
    var timeFor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(patientId) {
        if (patientId != null) runCatching { Repo.patient(patientId) }.onSuccess { p ->
            name = p.fullName; age = p.age?.toString().orEmpty(); phone = p.phone.orEmpty(); email = p.email
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (patientId == null) "Novo paciente" else "Editar paciente") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field(name, { name = it }, "Nome completo *")
            Field(age, { age = it.filter(Char::isDigit).take(3) }, "Idade", keyboard = KeyboardType.Number)
            Field(phone, { phone = it }, "Telefone", keyboard = KeyboardType.Phone)
            Field(
                email, { email = it.trim() }, "E-mail *", keyboard = KeyboardType.Email, enabled = patientId == null,
                supporting = if (patientId == null) "O paciente receberá neste e-mail o código de acesso" else "O e-mail não pode ser alterado",
            )

            if (patientId == null) {
                Spacer(Modifier.height(8.dp))
                SectionCard("Dias e horários das sessões") {
                    Text("Você pode adicionar mais de um atendimento por semana e editar depois.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    slots.forEachIndexed { i, s ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Dropdown("Dia", WEEKDAYS, s.weekday, { slots[i] = s.copy(weekday = it) }, Modifier.weight(1.4f))
                            OutlinedButton(onClick = { timeFor = i }, Modifier.weight(0.9f)) { Text(Dates.hhmm(s.startTime)) }
                            IconButton(onClick = { slots.removeAt(i) }) { Icon(Icons.Default.Delete, "Remover") }
                        }
                        Dropdown("Duração", DURATIONS, s.durationMinutes, { slots[i] = s.copy(durationMinutes = it) })
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { slots.add(Slot(weekday = 1, startTime = "14:00:00")) }) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Adicionar dia/horário")
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    error = null; busy = true
                    scope.launch {
                        runCatching {
                            if (patientId == null) {
                                val res = Repo.invitePatient(
                                    InviteRequest(fullName = name.trim(), age = age.toIntOrNull(), phone = phone.ifBlank { null }, email = email.lowercase(), slots = slots.toList())
                                )
                                if (res.error != null) error = res.error else invite = res
                            } else {
                                Repo.updatePatient(patientId, name.trim(), age.toIntOrNull(), phone.ifBlank { null })
                                onDone()
                            }
                        }.onFailure { error = friendlyError(it) }
                        busy = false
                    }
                },
                enabled = !busy && name.isNotBlank() && (patientId != null || email.contains("@")),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (patientId == null) "Cadastrar e enviar convite" else "Salvar") }
        }
    }

    timeFor?.let { i ->
        val t = LocalTime.parse(slots[i].startTime)
        TimePickerDialog(t.hour, t.minute, onPick = { h, m -> slots[i] = slots[i].copy(startTime = "%02d:%02d:00".format(h, m)) }, onDismiss = { timeFor = null })
    }

    invite?.let { res -> InviteResultDialog(res, profile, onClose = { invite = null; onDone() }) }
}

/** Mostra o link da Play Store para enviar ao paciente. */
@Composable
fun InviteResultDialog(res: InviteResponse, profile: Profile, onClose: () -> Unit) {
    val context = LocalContext.current
    val link = res.playStoreUrl?.ifBlank { null } ?: BuildConfig.PLAY_STORE_URL
    val message = "Olá, ${res.patient?.fullName.orEmpty()}! ${profile.fullName.orEmpty()} (CRP ${profile.crp.orEmpty()}) liberou seu acesso ao app Terapia.\n\n" +
        "1. Instale: $link\n2. Abra o app, toque em \"Sou paciente\" e informe seu e-mail (${res.patient?.email.orEmpty()}).\n" +
        "3. Digite o código que chegou no seu e-mail."
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Paciente cadastrado ✔") },
        text = {
            Column {
                Text(
                    if (res.emailSent) "Enviamos o código de acesso e o link do app para ${res.patient?.email}."
                    else "Não foi possível enviar o e-mail automaticamente. Envie o link abaixo e peça para o paciente tocar em \"Enviar novo código\" no app."
                )
                Spacer(Modifier.height(12.dp))
                Text("Link de instalação:", fontWeight = FontWeight.SemiBold)
                Text(link, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copy(context, link) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copiar") }
                    Button(onClick = { share(context, message) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("Enviar") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Concluir") } },
    )
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("link", text))
}

fun share(context: Context, text: String) {
    context.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Enviar convite")
    )
}

// ---------------------------------------------------------------------------------------------
// Editar horário fixo
// ---------------------------------------------------------------------------------------------
@Composable
fun SlotDialog(patientId: String, slot: Slot?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(slot ?: Slot(patientId = patientId, weekday = 1, startTime = "14:00:00")) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (slot == null) "Novo horário fixo" else "Editar horário fixo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("Dia da semana", WEEKDAYS, s.weekday, { s = s.copy(weekday = it) })
                OutlinedButton(onClick = { pickTime = true }, Modifier.fillMaxWidth()) { Text("Horário: ${Dates.hhmm(s.startTime)}") }
                Dropdown("Duração", DURATIONS, s.durationMinutes, { s = s.copy(durationMinutes = it) })
                Text("As próximas sessões (ainda não confirmadas) serão ajustadas automaticamente.", style = MaterialTheme.typography.bodySmall)
                if (slot != null) TextButton(onClick = { confirmDelete = true }) { Text("Excluir horário", color = MaterialTheme.colorScheme.error) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching { if (slot == null) Repo.addSlot(s.copy(patientId = patientId)) else Repo.updateSlot(s) }
                        .onSuccess { onSaved() }.onFailure { error = friendlyError(it) }
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (pickTime) {
        val t = LocalTime.parse(s.startTime)
        TimePickerDialog(t.hour, t.minute, { h, m -> s = s.copy(startTime = "%02d:%02d:00".format(h, m)) }, { pickTime = false })
    }
    if (confirmDelete) {
        ConfirmDialog("Excluir horário?", "As sessões futuras deste horário que não foram confirmadas serão removidas.", "Excluir",
            onConfirm = { scope.launch { runCatching { Repo.deleteSlot(slot!!.id!!) }.onSuccess { onSaved() }.onFailure { error = friendlyError(it) } } },
            onDismiss = { confirmDelete = false })
    }
}

// ---------------------------------------------------------------------------------------------
// Editar sessão (data, horário, duração, status) ou criar sessão avulsa
// ---------------------------------------------------------------------------------------------
@Composable
fun SessionEditDialog(session: Session?, patientId: String? = null, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val start = session?.let { Dates.local(it.startsAt) }
    var date by remember { mutableStateOf(start?.toLocalDate() ?: LocalDate.now()) }
    var time by remember { mutableStateOf(start?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(14, 0)) }
    var duration by remember { mutableStateOf(session?.durationMinutes ?: 50) }
    var status by remember { mutableStateOf(session?.status ?: "scheduled") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session == null) "Nova sessão avulsa" else (session.patients?.fullName ?: "Editar sessão")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, Modifier.fillMaxWidth()) { Text("Data: ${Dates.date(date)}") }
                OutlinedButton(onClick = { pickTime = true }, Modifier.fillMaxWidth()) { Text("Horário: %02d:%02d".format(time.hour, time.minute)) }
                Dropdown("Duração", DURATIONS, duration, { duration = it })
                Dropdown(
                    "Status",
                    listOf("scheduled" to "Agendada", "confirmed" to "Confirmada", "done" to "Realizada", "cancelled" to "Cancelada"),
                    status, { status = it },
                )
                if (session != null) TextButton(onClick = { confirmDelete = true }) { Text("Excluir sessão", color = MaterialTheme.colorScheme.error) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val instant = date.atTime(time).atZone(Dates.zone).toInstant()
                scope.launch {
                    runCatching {
                        if (session == null) Repo.addSession(NewSession(patientId!!, instant.toString(), duration))
                        else Repo.updateSession(session.id, instant, duration, status)
                    }.onSuccess { onSaved() }.onFailure { error = friendlyError(it) }
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (pickDate) DatePickerDialogBox(date, { date = it }, { pickDate = false })
    if (pickTime) TimePickerDialog(time.hour, time.minute, { h, m -> time = LocalTime.of(h, m) }, { pickTime = false })
    if (confirmDelete && session != null) {
        ConfirmDialog("Excluir sessão?", "As anotações desta sessão também serão apagadas.", "Excluir",
            onConfirm = { scope.launch { runCatching { Repo.deleteSession(session.id) }.onSuccess { onSaved() }.onFailure { error = friendlyError(it) } } },
            onDismiss = { confirmDelete = false })
    }
}
