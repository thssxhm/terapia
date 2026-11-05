package app.terapia.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.terapia.mobile.data.ChatMessage
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.friendlyError
import app.terapia.mobile.data.supabase
import app.terapia.mobile.ui.components.EmptyState
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Chat entre psicólogo e paciente (mensagens em tempo real + push quando o app está fechado). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(patientId: String, myId: String, title: String?, onBack: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(title) }
    val listState = rememberLazyListState()

    LaunchedEffect(patientId) {
        runCatching {
            if (name == null) name = Repo.patient(patientId).fullName
            messages.clear(); messages.addAll(Repo.messages(patientId))
            Repo.markMessagesRead(patientId)
        }.onFailure { error = friendlyError(it) }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    DisposableEffect(patientId) {
        val channel = supabase.channel("chat-$patientId")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("patient_id", FilterOperator.EQ, patientId)
        }.onEach { action ->
            val m = action.decodeRecord<ChatMessage>()
            if (messages.none { it.id == m.id }) messages.add(m)
            if (m.senderId != myId) runCatching { Repo.markMessagesRead(patientId) }
        }.launchIn(scope)
        scope.launch { runCatching { channel.subscribe() } }
        onDispose { scope.launch { runCatching { supabase.realtime.removeChannel(channel) } } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name ?: "Mensagens") },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (messages.isEmpty()) item { EmptyState(error ?: "Nenhuma mensagem ainda. Escreva a primeira!") }
                items(messages, key = { it.id ?: it.hashCode().toString() }) { m -> Bubble(m, mine = m.senderId == myId) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f),
                    placeholder = { Text("Mensagem") }, maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                )
                IconButton(
                    onClick = {
                        val body = text.trim()
                        if (body.isEmpty()) return@IconButton
                        text = ""
                        scope.launch {
                            runCatching { Repo.sendMessage(patientId, body) }
                                .onFailure { error = friendlyError(it); text = body }
                            // Garante a lista atualizada mesmo se o tempo real estiver desconectado
                            runCatching { Repo.messages(patientId) }.onSuccess { messages.clear(); messages.addAll(it) }
                        }
                    },
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(m.body)
            m.createdAt?.let {
                Text(
                    Dates.relative(it) + if (mine && m.readAt != null) " ✓✓" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
