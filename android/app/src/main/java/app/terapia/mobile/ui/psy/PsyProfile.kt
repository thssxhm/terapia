package app.terapia.mobile.ui.psy

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.terapia.mobile.BuildConfig
import app.terapia.mobile.MainActivity
import app.terapia.mobile.auth.Biometric
import app.terapia.mobile.billing.BillingManager
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Prefs
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.friendlyError
import app.terapia.mobile.data.supabase
import app.terapia.mobile.notifications.NotificationChannels
import app.terapia.mobile.notifications.Reminders
import app.terapia.mobile.notifications.TerapiaMessagingService
import app.terapia.mobile.ui.components.AsyncContent
import app.terapia.mobile.ui.components.ConfirmDialog
import app.terapia.mobile.ui.components.Field
import app.terapia.mobile.ui.components.SectionCard
import app.terapia.mobile.ui.components.rememberAsync
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsyProfileScreen(activity: MainActivity, prefs: Prefs, onBack: () -> Unit, onChanged: () -> Unit) {
    val state = rememberAsync(Unit) { Repo.myProfile()!! to Repo.myPatients().size }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Perfil e ajustes") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        )
    }) { padding ->
        AsyncContent(state) { (profile, used) ->
            val scope = rememberCoroutineScope()
            var name by remember { mutableStateOf(profile.fullName.orEmpty()) }
            var crp by remember { mutableStateOf(profile.crp.orEmpty()) }
            var phone by remember { mutableStateOf(profile.phone.orEmpty()) }
            var msg by remember { mutableStateOf<String?>(null) }
            Column(
                Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard("Seus dados") {
                    Field(name, { name = it }, "Nome")
                    Spacer(Modifier.height(8.dp))
                    Field(crp, { crp = it }, "CRP")
                    Spacer(Modifier.height(8.dp))
                    Field(phone, { phone = it }, "Telefone")
                    Spacer(Modifier.height(8.dp))
                    Text("E-mail: ${profile.email.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    msg?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                    Button(onClick = {
                        scope.launch {
                            msg = runCatching { Repo.updateMyProfile(name, crp, phone.ifBlank { null }); onChanged(); "Salvo ✔" }
                                .getOrElse { friendlyError(it) }
                        }
                    }) { Text("Salvar") }
                }
                PlanCard(activity, used, effectiveLimit(profile), profile.patientLimit, profile.trialUntil, onChanged = { state.reload(); onChanged() })
                SettingsCard(activity, prefs, psychologist = true)
            }
        }
    }
}

@Composable
private fun PlanCard(activity: MainActivity, used: Int, limit: Int, paidLimit: Int, trialUntil: String?, onChanged: () -> Unit) {
    val billing = remember { BillingManager(activity) { activity.runOnUiThread(onChanged) } }
    DisposableEffect(Unit) { billing.connect(); onDispose { billing.disconnect() } }
    val products by billing.products.collectAsState()
    val message by billing.message.collectAsState()

    SectionCard("Plano") {
        Text("$used de $limit pacientes em uso", fontWeight = FontWeight.SemiBold)
        if (paidLimit == 0 && trialUntil != null) {
            Text("Teste grátis com 10 pacientes até ${Dates.date(trialUntil)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text("Os planos são vendidos de 10 em 10 pacientes. Escolha quantos pacientes você atende:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (products.isEmpty()) {
            Text("Carregando planos da Google Play… (os planos só aparecem quando o app é instalado pela Play Store)", style = MaterialTheme.typography.bodySmall)
        }
        products.forEach { p ->
            val n = p.productId.removePrefix("plano_")
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Até $n pacientes", fontWeight = FontWeight.SemiBold)
                    Text(billing.priceOf(p) + " / mês", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { billing.buy(activity, p) }, enabled = n.toIntOrNull() != paidLimit) {
                    Text(if (n.toIntOrNull() == paidLimit) "Atual" else "Assinar")
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=${activity.packageName}")))
        }) { Text("Gerenciar/cancelar assinatura na Play Store") }
    }
}

/** Ajustes comuns (tema, notificações, biometria, sair, excluir conta). */
@Composable
fun SettingsCard(activity: MainActivity, prefs: Prefs, psychologist: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by prefs.all.collectAsState(initial = null)
    val theme = all?.get(Prefs.THEME) ?: "system"
    val biometric = all?.get(Prefs.BIOMETRIC) == true
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var warnOff by remember { mutableStateOf(false) }

    SectionCard("Aparência") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "Automático", "light" to "Claro", "dark" to "Escuro").forEach { (v, l) ->
                FilterChip(theme == v, { scope.launch { prefs.set(Prefs.THEME, v) } }, label = { Text(l) })
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionCard("Notificações") {
        if (!psychologist) {
            val session = all?.get(Prefs.NOTIF_SESSION) ?: true
            val mood = all?.get(Prefs.NOTIF_MOOD) ?: true
            val hour = all?.get(Prefs.MOOD_HOUR) ?: 20
            Text("Recomendamos manter os lembretes ativados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lembrete de sessão (1h antes)", Modifier.weight(1f))
                Switch(session, { on ->
                    if (!on) warnOff = true
                    scope.launch { prefs.set(Prefs.NOTIF_SESSION, on); Reminders.start(context) }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lembrete do humor do dia", Modifier.weight(1f))
                Switch(mood, { on ->
                    if (!on) warnOff = true
                    scope.launch { prefs.set(Prefs.NOTIF_MOOD, on); Reminders.scheduleMood(context, on, hour) }
                })
            }
            if (mood) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Horário do lembrete do humor: ${hour}h", Modifier.weight(1f))
                TextButton(onClick = { val h = (hour + 23) % 24; scope.launch { prefs.set(Prefs.MOOD_HOUR, h); Reminders.scheduleMood(context, true, h) } }) { Text("−") }
                TextButton(onClick = { val h = (hour + 1) % 24; scope.launch { prefs.set(Prefs.MOOD_HOUR, h); Reminders.scheduleMood(context, true, h) } }) { Text("+") }
            }
        } else {
            Text("Os lembretes de atendimento são configurados na aba Lembretes (sininho).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Os sons seguem o padrão do Android. Para personalizar:", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { NotificationChannels.openSettings(context) }) { Text("Sons e alertas do Android") }
    }
    Spacer(Modifier.height(16.dp))
    SectionCard("Segurança e conta") {
        if (Biometric.isAvailable(context)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Desbloquear com biometria", Modifier.weight(1f))
                Switch(biometric, { on ->
                    if (on) Biometric.prompt(activity, "Ativar biometria", { scope.launch { prefs.set(Prefs.BIOMETRIC, true) } }, {})
                    else scope.launch { prefs.set(Prefs.BIOMETRIC, false) }
                })
            }
        }
        var pw by remember { mutableStateOf("") }
        var pwMsg by remember { mutableStateOf<String?>(null) }
        Field(pw, { pw = it }, "Nova senha", password = true)
        pwMsg?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        OutlinedButton(onClick = {
            scope.launch { pwMsg = runCatching { supabase.auth.updateUser { password = pw }; pw = ""; "Senha alterada ✔" }.getOrElse { friendlyError(it) } }
        }, enabled = pw.length >= 6) { Text("Alterar senha") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            scope.launch { prefs.set(if (psychologist) Prefs.ONBOARDING_PSY else Prefs.ONBOARDING_PATIENT, false) }
        }) { Text("Ver instruções de uso novamente") }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SITE_URL + "/privacidade")))
        }) { Text("Política de privacidade") }
        TextButton(onClick = { confirmLogout = true }) { Text("Sair da conta") }
        TextButton(onClick = { confirmDelete = true }) { Text("Excluir minha conta", color = MaterialTheme.colorScheme.error) }
    }

    if (warnOff) {
        ConfirmDialog("Lembrete desativado", "É indicado manter essas notificações ativadas para não perder sessões e registros de humor.",
            "Entendi", onConfirm = {}, onDismiss = { warnOff = false })
    }
    if (confirmLogout) {
        ConfirmDialog("Sair da conta?", "Você precisará entrar novamente.", "Sair", onConfirm = {
            Reminders.stopAll(context)
            TerapiaMessagingService.unregisterToken {
                scope.launch { prefs.clearUserData(); supabase.auth.signOut() }
            }
        }, onDismiss = { confirmLogout = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Excluir conta permanentemente?",
            if (psychologist) "Todos os seus pacientes, prontuários, anexos e mensagens serão apagados. Esta ação não pode ser desfeita. Lembre-se de exportar os PDFs antes (o CFP exige a guarda dos registros por 5 anos)."
            else "Seus registros de humor, anotações e mensagens serão apagados. Esta ação não pode ser desfeita.",
            "Excluir",
            onConfirm = {
                Reminders.stopAll(context)
                scope.launch { runCatching { Repo.deleteMyAccount() }; prefs.clearUserData() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
