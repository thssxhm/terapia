package app.terapia.mobile.ui.psy

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.terapia.mobile.data.ANAMNESIS_FIELDS
import app.terapia.mobile.data.Anamnesis
import app.terapia.mobile.data.Attachment
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Evolution
import app.terapia.mobile.data.ImportantInfo
import app.terapia.mobile.data.InviteRequest
import app.terapia.mobile.data.Patient
import app.terapia.mobile.data.PsyNote
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.Session
import app.terapia.mobile.data.Slot
import app.terapia.mobile.data.friendlyError
import app.terapia.mobile.pdf.PatientPdfExporter
import app.terapia.mobile.ui.components.AsyncContent
import app.terapia.mobile.ui.components.ConfirmDialog
import app.terapia.mobile.ui.components.DatePickerDialogBox
import app.terapia.mobile.ui.components.Dropdown
import app.terapia.mobile.ui.components.EmptyState
import app.terapia.mobile.ui.components.Field
import app.terapia.mobile.ui.components.SectionCard
import app.terapia.mobile.ui.components.rememberAsync
import app.terapia.mobile.ui.patient.MoodWeekChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate

private val TABS = listOf("Sessões", "Anamnese", "Evolução", "Informações", "Anexos", "Pedidos", "Humor")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    patientId: String,
    initialTab: Int,
    onBack: () -> Unit,
    onChat: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val state = rememberAsync(patientId) { Repo.patient(patientId) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.data?.fullName ?: "Paciente") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = onChat) { Icon(Icons.AutoMirrored.Filled.Chat, "Mensagens") }
                    IconButton(onClick = {
                        exporting = true
                        scope.launch {
                            runCatching { PatientPdfExporter.export(context, patientId) }
                                .onSuccess { PatientPdfExporter.share(context, it) }
                                .onFailure { snackbar.showSnackbar(friendlyError(it)) }
                            exporting = false
                        }
                    }) { Icon(Icons.Default.PictureAsPdf, "Exportar PDF") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mais") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar dados") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Reenviar convite/código") }, onClick = {
                            menu = false
                            scope.launch {
                                val r = runCatching { Repo.invitePatient(InviteRequest(resendPatientId = patientId)) }
                                snackbar.showSnackbar(
                                    r.getOrNull()?.let { if (it.error != null) it.error else if (it.emailSent) "Código reenviado por e-mail" else "Não foi possível enviar o e-mail" }
                                        ?: friendlyError(r.exceptionOrNull()!!)
                                )
                            }
                        })
                        DropdownMenuItem(text = { Text("Excluir paciente", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            AsyncContent(state) { p ->
                when (tab) {
                    0 -> SessionsSection(p, onReloadPatient = state.reload)
                    1 -> AnamnesisSection(p.id)
                    2 -> EvolutionSection(p.id)
                    3 -> InfoSection(p.id)
                    4 -> AttachmentsSection(p.id)
                    5 -> RequestsSection(p.id)
                    6 -> MoodSection(p.id)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Excluir paciente?",
            "Todos os dados, anotações, anexos e mensagens deste paciente serão apagados permanentemente. Dica: exporte o PDF antes.",
            "Excluir",
            onConfirm = {
                scope.launch {
                    runCatching { Repo.deletePatient(state.data!!) }
                        .onSuccess { onDeleted() }
                        .onFailure { snackbar.showSnackbar(friendlyError(it)) }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// SESSÕES + horários fixos + anotações pré/pós sessão
// ---------------------------------------------------------------------------------------------
@Composable
private fun SessionsSection(p: Patient, onReloadPatient: () -> Unit) {
    val state = rememberAsync(p.id) { Repo.sessionsOf(p.id) to Repo.psyNotes(p.id).associateBy { it.sessionId } }
    var slotEdit by remember { mutableStateOf<Slot?>(null) }
    var newSlot by remember { mutableStateOf(false) }
    var newSession by remember { mutableStateOf(false) }
    var notesFor by remember { mutableStateOf<Session?>(null) }
    var editFor by remember { mutableStateOf<Session?>(null) }
    var showPast by remember { mutableStateOf(false) }

    AsyncContent(state) { (sessions, notes) ->
        val now = Instant.now()
        val upcoming = sessions.filter { Dates.instant(it.startsAt).isAfter(now.minusSeconds(3600)) }
        val past = sessions.filter { !Dates.instant(it.startsAt).isAfter(now.minusSeconds(3600)) }.reversed()
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard("Horários fixos") {
                    val slots = p.slots.orEmpty().sortedWith(compareBy({ it.weekday }, { it.startTime }))
                    if (slots.isEmpty()) Text("Nenhum horário fixo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    slots.forEach { s ->
                        Row(Modifier.fillMaxWidth().clickable { slotEdit = s }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("${Dates.weekdayName(s.weekday)} às ${Dates.hhmm(s.startTime)} • ${s.durationMinutes} min", Modifier.weight(1f))
                            Icon(Icons.Default.Edit, "Editar")
                        }
                    }
                    Row {
                        TextButton(onClick = { newSlot = true }) { Text("+ Horário fixo") }
                        TextButton(onClick = { newSession = true }) { Text("+ Sessão avulsa") }
                    }
                }
            }
            item { Text("Próximas sessões", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            if (upcoming.isEmpty()) item { EmptyState("Nenhuma sessão futura.") }
            items(upcoming, key = { it.id }) { s -> SessionNotesCard(s, notes[s.id], { notesFor = s }, { editFor = s }) }
            item {
                TextButton(onClick = { showPast = !showPast }) {
                    Text(if (showPast) "Ocultar sessões anteriores" else "Ver sessões anteriores (${past.size})")
                }
            }
            if (showPast) items(past, key = { "p" + it.id }) { s -> SessionNotesCard(s, notes[s.id], { notesFor = s }, { editFor = s }) }
        }
    }

    val reloadAll = { state.reload(); onReloadPatient() }
    if (newSlot) SlotDialog(p.id, null, { newSlot = false }, { newSlot = false; reloadAll() })
    slotEdit?.let { s -> SlotDialog(p.id, s, { slotEdit = null }, { slotEdit = null; reloadAll() }) }
    if (newSession) SessionEditDialog(null, p.id, { newSession = false }, { newSession = false; state.reload() })
    editFor?.let { s -> SessionEditDialog(s, p.id, { editFor = null }, { editFor = null; state.reload() }) }
    notesFor?.let { s ->
        PsyNotesDialog(s, state.data?.second?.get(s.id), onDismiss = { notesFor = null }, onSaved = { notesFor = null; state.reload() })
    }
}

@Composable
private fun SessionNotesCard(s: Session, note: PsyNote?, onNotes: () -> Unit, onEdit: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onNotes),
        colors = CardDefaults.cardColors(
            containerColor = if (s.status == "cancelled") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Dates.full(s.startsAt), fontWeight = FontWeight.SemiBold)
                    Text("${app.terapia.mobile.data.statusLabel(s.status)} • ${s.durationMinutes} min", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar data/horário") }
            }
            note?.preNote?.takeIf { it.isNotBlank() }?.let { Text("Pré: $it", maxLines = 2, style = MaterialTheme.typography.bodySmall) }
            note?.postNote?.takeIf { it.isNotBlank() }?.let { Text("Pós: $it", maxLines = 2, style = MaterialTheme.typography.bodySmall) }
            if (note == null) Text("Toque para anotações pré e pós sessão", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PsyNotesDialog(s: Session, note: PsyNote?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pre by remember { mutableStateOf(note?.preNote.orEmpty()) }
    var post by remember { mutableStateOf(note?.postNote.orEmpty()) }
    var addInfo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Dates.short(s.startsAt)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(pre, { pre = it }, "Anotações pré-sessão", minLines = 3)
                Field(post, { post = it }, "Anotações pós-sessão", minLines = 4)
                OutlinedButton(onClick = { addInfo = true }) { Text("+ Informação importante (nome, data…)") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching { Repo.savePsyNote(PsyNote(s.id, s.patientId, pre, post)) }
                        .onSuccess { onSaved() }.onFailure { error = friendlyError(it) }
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (addInfo) InfoDialog(s.patientId, null, { addInfo = false }, { addInfo = false })
}

// ---------------------------------------------------------------------------------------------
// ANAMNESE
// ---------------------------------------------------------------------------------------------
@Composable
private fun AnamnesisSection(patientId: String) {
    val scope = rememberCoroutineScope()
    val state = rememberAsync(patientId) { Repo.anamnesis(patientId) }
    AsyncContent(state) { a ->
        val values = remember(a) { mutableStateMapOf<String, String>().apply { putAll(a.data) } }
        var msg by remember { mutableStateOf<String?>(null) }
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ANAMNESIS_FIELDS.forEach { (key, label) ->
                Field(values[key].orEmpty(), { values[key] = it }, label, minLines = 2)
            }
            msg?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Button(onClick = {
                scope.launch {
                    msg = runCatching { Repo.saveAnamnesis(Anamnesis(patientId, values.toMap())); "Anamnese salva ✔" }
                        .getOrElse { friendlyError(it) }
                }
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("Salvar anamnese") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// EVOLUÇÃO
// ---------------------------------------------------------------------------------------------
@Composable
private fun EvolutionSection(patientId: String) {
    val scope = rememberCoroutineScope()
    val state = rememberAsync(patientId) { Repo.evolutions(patientId) }
    var editing by remember { mutableStateOf<Evolution?>(null) }
    AsyncContent(state) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(onClick = { editing = Evolution(patientId = patientId, entryDate = LocalDate.now().toString(), content = "") }) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Nova evolução")
                }
            }
            if (list.isEmpty()) item { EmptyState("Nenhum registro de evolução.") }
            items(list, key = { it.id!! }) { e ->
                SectionCard(Dates.date(e.entryDate), Modifier.clickable { editing = e }) {
                    Text(e.content)
                }
            }
        }
    }
    editing?.let { e ->
        var date by remember(e) { mutableStateOf(LocalDate.parse(e.entryDate)) }
        var text by remember(e) { mutableStateOf(e.content) }
        var pickDate by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (e.id == null) "Nova evolução" else "Editar evolução") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) { Text("Data: ${Dates.date(date)}") }
                    Field(text, { text = it }, "Evolução do paciente", minLines = 6)
                    if (e.id != null) TextButton(onClick = {
                        scope.launch { runCatching { Repo.deleteEvolution(e.id) }; editing = null; state.reload() }
                    }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { Repo.saveEvolution(e.copy(entryDate = date.toString(), content = text)) }
                        editing = null; state.reload()
                    }
                }, enabled = text.isNotBlank()) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
        if (pickDate) DatePickerDialogBox(date, { date = it }, { pickDate = false })
    }
}

// ---------------------------------------------------------------------------------------------
// INFORMAÇÕES IMPORTANTES (nomes, datas e outros)
// ---------------------------------------------------------------------------------------------
@Composable
private fun InfoSection(patientId: String) {
    val state = rememberAsync(patientId) { Repo.importantInfo(patientId) }
    var editing by remember { mutableStateOf<ImportantInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    AsyncContent(state) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Nova informação") }
            }
            if (list.isEmpty()) item { EmptyState("Registre nomes importantes, datas marcantes e outros dados relevantes.") }
            listOf("nome" to "Nomes importantes", "data" to "Datas importantes", "outro" to "Outras informações").forEach { (cat, title) ->
                val group = list.filter { it.category == cat }
                if (group.isNotEmpty()) item(key = cat) {
                    SectionCard(title) {
                        group.forEach { i ->
                            Column(Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 6.dp)) {
                                Text(i.title + (i.refDate?.let { " — ${Dates.date(it)}" } ?: ""), fontWeight = FontWeight.SemiBold)
                                i.details?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) InfoDialog(patientId, null, { creating = false }, { creating = false; state.reload() })
    editing?.let { InfoDialog(patientId, it, { editing = null }, { editing = null; state.reload() }) }
}

@Composable
private fun InfoDialog(patientId: String, info: ImportantInfo?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(info?.category ?: "nome") }
    var title by remember { mutableStateOf(info?.title.orEmpty()) }
    var details by remember { mutableStateOf(info?.details.orEmpty()) }
    var date by remember { mutableStateOf(info?.refDate?.let { LocalDate.parse(it) }) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (info == null) "Nova informação importante" else "Editar informação") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("Tipo", listOf("nome" to "Nome importante", "data" to "Data importante", "outro" to "Outro"), category, { category = it })
                Field(title, { title = it }, when (category) { "nome" -> "Nome (ex.: Maria — mãe)"; "data" -> "Evento (ex.: aniversário)"; else -> "Título" })
                Field(details, { details = it }, "Detalhes", minLines = 2)
                OutlinedButton(onClick = { pickDate = true }) { Text(date?.let { "Data: ${Dates.date(it)}" } ?: "Adicionar data (opcional)") }
                if (info != null) TextButton(onClick = {
                    scope.launch { runCatching { Repo.deleteInfo(info.id!!) }; onSaved() }
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        Repo.saveInfo(ImportantInfo(info?.id, patientId, category, title, details.ifBlank { null }, date?.toString()))
                    }
                    onSaved()
                }
            }, enabled = title.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (pickDate) DatePickerDialogBox(date ?: LocalDate.now(), { date = it }, { pickDate = false })
}

// ---------------------------------------------------------------------------------------------
// ANEXOS (PDF / DOC / DOCX)
// ---------------------------------------------------------------------------------------------
@Composable
private fun AttachmentsSection(patientId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberAsync(patientId) { Repo.attachments(patientId) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Attachment?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; error = null
        scope.launch {
            runCatching {
                val cr = context.contentResolver
                val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "arquivo"
                val mime = cr.getType(uri)
                val bytes = withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { it.readBytes() } }
                require(bytes.size <= 20 * 1024 * 1024) { "Arquivo maior que 20 MB." }
                Repo.uploadAttachment(patientId, name, mime, bytes)
            }.onFailure { error = friendlyError(it) }
            busy = false
            state.reload()
        }
    }

    AsyncContent(state) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(onClick = {
                    picker.launch(arrayOf(
                        "application/pdf", "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ))
                }, enabled = !busy) {
                    Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(4.dp)); Text("Anexar PDF ou DOC")
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (list.isEmpty()) item { EmptyState("Nenhum anexo.") }
            items(list, key = { it.id!! }) { a ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    ListItem(
                        modifier = Modifier.clickable {
                            scope.launch {
                                runCatching {
                                    val bytes = Repo.downloadAttachment(a)
                                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                    val file = File(dir, a.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                                    withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mimeType ?: "*/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                }.onFailure { error = friendlyError(it) }
                            }
                        },
                        headlineContent = { Text(a.fileName) },
                        supportingContent = {
                            Text(listOfNotNull(a.createdAt?.let { Dates.relative(it) }, a.sizeBytes?.let { "${it / 1024} KB" }).joinToString(" • "))
                        },
                        trailingContent = { IconButton(onClick = { toDelete = a }) { Icon(Icons.Default.Delete, "Excluir") } },
                    )
                }
            }
        }
    }
    toDelete?.let { a ->
        ConfirmDialog("Excluir anexo?", a.fileName, "Excluir",
            onConfirm = { scope.launch { runCatching { Repo.deleteAttachment(a) }; state.reload() } },
            onDismiss = { toDelete = null })
    }
}

// ---------------------------------------------------------------------------------------------
// PEDIDOS (histórico de confirmações, cancelamentos e ajustes)
// ---------------------------------------------------------------------------------------------
@Composable
private fun RequestsSection(patientId: String) {
    val state = rememberAsync(patientId) { Repo.requestsOf(patientId) }
    AsyncContent(state) { list ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) item { EmptyState("Nenhum pedido deste paciente.") }
            items(list, key = { it.id!! }) { r ->
                SectionCard(
                    when (r.kind) { "confirm" -> "Confirmação de presença"; "cancel" -> "Pedido de cancelamento"; else -> "Pedido de ajuste de horário" }
                ) {
                    r.message?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    Text(
                        (r.createdAt?.let { Dates.relative(it) } ?: "") + " • " +
                            when (r.status) { "accepted" -> "Aceito"; "declined" -> "Recusado"; else -> "Pendente (responda em Avisos)" },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// HUMOR (semanas que o paciente enviou)
// ---------------------------------------------------------------------------------------------
@Composable
private fun MoodSection(patientId: String) {
    val state = rememberAsync(patientId) {
        val shares = Repo.moodShares(patientId)
        val moods = if (shares.isEmpty()) emptyList() else Repo.moods(patientId, LocalDate.parse(shares.last().weekStart), LocalDate.now())
        shares to moods
    }
    AsyncContent(state) { (shares, moods) ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shares.isEmpty()) item { EmptyState("O paciente ainda não enviou nenhum gráfico de humor.") }
            items(shares, key = { it.weekStart }) { s ->
                val start = LocalDate.parse(s.weekStart)
                SectionCard("Semana de ${Dates.dayMonth(start)} a ${Dates.dayMonth(start.plusDays(6))}") {
                    val week = moods.filter { LocalDate.parse(it.entryDate) in start..start.plusDays(6) }
                    MoodWeekChart(start, week)
                    week.filter { !it.note.isNullOrBlank() }.forEach {
                        Text("${Dates.dayMonth(LocalDate.parse(it.entryDate))}: ${it.note}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
