package app.terapia.mobile.ui.psy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.terapia.mobile.MainActivity
import app.terapia.mobile.data.AppNotification
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Emergency
import app.terapia.mobile.data.Prefs
import app.terapia.mobile.data.Profile
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.supabase
import app.terapia.mobile.ui.chat.ChatScreen
import app.terapia.mobile.ui.components.Logo
import app.terapia.mobile.ui.theme.EmergencyRed
import app.terapia.mobile.ui.theme.EmergencyYellow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private enum class PsyTab(val label: String) { PATIENTS("Pacientes"), WEEK("Semana"), AGENDA("Agenda"), NOTICES("Avisos"), REMINDERS("Lembretes") }

@Composable
fun PsyApp(activity: MainActivity, prefs: Prefs, profile: Profile, onProfileChanged: () -> Unit) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val emergencies = remember { mutableStateListOf<Emergency>() }
    var unread by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(PsyTab.PATIENTS) }

    suspend fun refreshUnread() {
        unread = runCatching { Repo.notifications().count { it.readAt == null } }.getOrDefault(unread)
    }

    // Emergências pendentes + escuta em tempo real (pop-up)
    LaunchedEffect(Unit) {
        runCatching { Repo.pendingEmergencies() }.onSuccess { list ->
            list.forEach { e -> if (emergencies.none { it.id == e.id }) emergencies.add(e) }
        }
        refreshUnread()
    }
    DisposableEffect(Unit) {
        val channel = supabase.channel("psy-live")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "emergencies" }
            .onEach { action ->
                val e = action.decodeRecord<Emergency>()
                val full = runCatching { Repo.emergency(e.id!!) }.getOrNull() ?: e
                if (emergencies.none { it.id == full.id }) emergencies.add(full)
            }.launchIn(scope)
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "notifications" }
            .onEach { action ->
                action.decodeRecord<AppNotification>()
                unread++
                refreshKey++
            }.launchIn(scope)
        scope.launch { runCatching { channel.subscribe() } }
        onDispose { scope.launch { runCatching { supabase.realtime.removeChannel(channel) } } }
    }

    // Toque em uma notificação do sistema
    val target by activity.notifTarget.collectAsState()
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        activity.notifTarget.value = null
        when {
            t.type.startsWith("emergency") && t.refId != null ->
                runCatching { Repo.emergency(t.refId) }.getOrNull()?.let { e ->
                    if (e.acknowledgedAt == null && emergencies.none { it.id == e.id }) emergencies.add(e)
                }
            t.type == "message" && t.patientId != null -> nav.navigate("chat/${t.patientId}")
            t.type == "mood" && t.patientId != null -> nav.navigate("patient/${t.patientId}?tab=6")
            t.type == "request" -> { tab = PsyTab.NOTICES; nav.popBackStack("home", false) }
            t.type == "session_reminder" -> { tab = PsyTab.AGENDA; nav.popBackStack("home", false) }
        }
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            PsyHome(
                profile = profile,
                tab = tab,
                onTab = { tab = it; if (it == PsyTab.NOTICES) scope.launch { refreshUnread() } },
                unread = unread,
                refreshKey = refreshKey,
                onUnreadChanged = { scope.launch { refreshUnread() } },
                onOpenPatient = { nav.navigate("patient/$it") },
                onOpenChat = { nav.navigate("chat/$it") },
                onAddPatient = { nav.navigate("add_patient") },
                onProfile = { nav.navigate("profile") },
            )
        }
        composable(
            "patient/{id}?tab={tab}",
            arguments = listOf(navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val id = entry.arguments?.getString("id")!!
            val initialTab = entry.arguments?.getString("tab")?.toIntOrNull() ?: 0
            PatientDetailScreen(
                patientId = id,
                initialTab = initialTab,
                onBack = { nav.popBackStack() },
                onChat = { nav.navigate("chat/$id") },
                onEdit = { nav.navigate("edit_patient/$id") },
                onDeleted = { nav.popBackStack("home", false) },
            )
        }
        composable("add_patient") {
            PatientFormScreen(patientId = null, profile = profile, onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
        }
        composable("edit_patient/{id}") { entry ->
            PatientFormScreen(
                patientId = entry.arguments?.getString("id"), profile = profile,
                onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() },
            )
        }
        composable("chat/{id}") { entry ->
            ChatScreen(patientId = entry.arguments?.getString("id")!!, myId = profile.id, title = null, onBack = { nav.popBackStack() })
        }
        composable("profile") {
            PsyProfileScreen(activity, prefs, onBack = { nav.popBackStack() }, onChanged = onProfileChanged)
        }
    }

    // Pop-up de emergência (amarela ou vermelha)
    emergencies.firstOrNull()?.let { e ->
        EmergencyPopup(
            e,
            onChat = {
                scope.launch { runCatching { Repo.acknowledgeEmergency(e.id!!) } }
                emergencies.remove(e)
                nav.navigate("chat/${e.patientId}")
            },
            onAck = {
                scope.launch { runCatching { Repo.acknowledgeEmergency(e.id!!) } }
                emergencies.remove(e)
            },
        )
    }
}

@Composable
private fun EmergencyPopup(e: Emergency, onChat: () -> Unit, onAck: () -> Unit) {
    val red = e.level == "red"
    val color = if (red) EmergencyRed else EmergencyYellow
    AlertDialog(
        onDismissRequest = {},
        containerColor = color.copy(alpha = 0.97f),
        icon = { Icon(Icons.Default.Warning, null, tint = if (red) Color.White else Color.Black, modifier = Modifier.size(40.dp)) },
        title = {
            Text(
                if (red) "EMERGÊNCIA — muito urgente" else "Emergência — pouco urgente",
                color = if (red) Color.White else Color.Black, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(e.patients?.fullName ?: "Paciente", color = if (red) Color.White else Color.Black, fontWeight = FontWeight.SemiBold)
                e.createdAt?.let { Text(Dates.relative(it), color = if (red) Color.White else Color.Black) }
                Spacer(Modifier.height(8.dp))
                Text(e.message, color = if (red) Color.White else Color.Black, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            Button(onClick = onChat, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Icon(Icons.AutoMirrored.Filled.Chat, null); Spacer(Modifier.width(6.dp)); Text("Responder")
            }
        },
        dismissButton = { TextButton(onClick = onAck) { Text("Ciente", color = if (red) Color.White else Color.Black) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PsyHome(
    profile: Profile,
    tab: PsyTab,
    onTab: (PsyTab) -> Unit,
    unread: Int,
    refreshKey: Int,
    onUnreadChanged: () -> Unit,
    onOpenPatient: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onAddPatient: () -> Unit,
    onProfile: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Logo(36.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(profile.fullName ?: "Terapia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("CRP ${profile.crp.orEmpty()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = { IconButton(onClick = onProfile) { Icon(Icons.Default.AccountCircle, "Perfil") } },
            )
        },
        bottomBar = {
            NavigationBar {
                PsyTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTab(t) },
                        label = { Text(t.label, maxLines = 1) },
                        icon = {
                            val icon = when (t) {
                                PsyTab.PATIENTS -> Icons.Default.Groups
                                PsyTab.WEEK -> Icons.Default.ViewWeek
                                PsyTab.AGENDA -> Icons.Default.CalendarMonth
                                PsyTab.NOTICES -> Icons.Default.Notifications
                                PsyTab.REMINDERS -> Icons.Default.NotificationsActive
                            }
                            if (t == PsyTab.NOTICES && unread > 0) {
                                BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else "$unread") } }) { Icon(icon, null) }
                            } else Icon(icon, null)
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.Top) {
            when (tab) {
                PsyTab.PATIENTS -> PatientsTab(profile, onOpenPatient, onOpenChat, onAddPatient)
                PsyTab.WEEK -> WeekTab(onOpenPatient)
                PsyTab.AGENDA -> AgendaTab(onOpenPatient)
                PsyTab.NOTICES -> NoticesTab(refreshKey, onUnreadChanged, onOpenPatient, onOpenChat)
                PsyTab.REMINDERS -> RemindersTab()
            }
        }
    }
}
