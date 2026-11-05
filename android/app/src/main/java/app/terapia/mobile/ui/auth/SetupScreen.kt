package app.terapia.mobile.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import app.terapia.mobile.AppLock
import app.terapia.mobile.MainActivity
import app.terapia.mobile.auth.Biometric
import app.terapia.mobile.data.Prefs
import app.terapia.mobile.data.friendlyError
import app.terapia.mobile.data.supabase
import app.terapia.mobile.notifications.Reminders
import app.terapia.mobile.ui.components.Field
import app.terapia.mobile.ui.components.SectionCard
import app.terapia.mobile.ui.rememberNotificationPermission
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/**
 * Configuração inicial (aparece uma vez):
 *  - criar senha / biometria (o login com Google funciona com o mesmo e-mail)
 *  - lembretes de sessão e humor (paciente), com aviso para mantê-los ligados
 *  - pedido de permissão de notificação
 */
@Composable
fun SetupScreen(activity: MainActivity, prefs: Prefs, psychologist: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var passwordMsg by remember { mutableStateOf<String?>(null) }
    var biometric by remember { mutableStateOf(false) }
    var sessionReminder by remember { mutableStateOf(true) }
    var moodReminder by remember { mutableStateOf(true) }
    var warnOff by remember { mutableStateOf(false) }
    val askPermission = rememberNotificationPermission {
        scope.launch {
            prefs.set(Prefs.NOTIF_SESSION, sessionReminder)
            prefs.set(Prefs.NOTIF_MOOD, moodReminder)
            prefs.set(Prefs.BIOMETRIC, biometric)
            prefs.set(Prefs.SECURITY_SETUP_DONE, true)
            if (biometric) AppLock.locked.value = false
            Reminders.start(context)
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text("Vamos configurar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SectionCard("Como entrar nas próximas vezes") {
            Text(
                "Você pode criar uma senha, entrar com a conta Google do mesmo e-mail ou usar a biometria do celular.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Field(password, { password = it }, "Criar/alterar senha (opcional)", password = true)
            passwordMsg?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        passwordMsg = runCatching { supabase.auth.updateUser { this.password = password }; "Senha salva ✔" }
                            .getOrElse { friendlyError(it) }
                    }
                },
                enabled = password.length >= 6,
            ) { Text("Salvar senha") }
            Spacer(Modifier.height(12.dp))
            if (Biometric.isAvailable(context)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Desbloquear com biometria", Modifier.weight(1f))
                    Switch(checked = biometric, onCheckedChange = { on ->
                        if (on) Biometric.prompt(activity, "Ativar biometria", onSuccess = { biometric = true }, onError = {})
                        else biometric = false
                    })
                }
            } else {
                Text("Biometria indisponível neste celular.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!psychologist) {
            Spacer(Modifier.height(16.dp))
            SectionCard("Notificações") {
                Text(
                    "Recomendamos manter os lembretes ativados: eles ajudam você a não perder sessões e a registrar o humor todos os dias.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lembrete de sessão", Modifier.weight(1f))
                    Switch(sessionReminder, { sessionReminder = it; if (!it) warnOff = true })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lembrete do humor do dia (20h)", Modifier.weight(1f))
                    Switch(moodReminder, { moodReminder = it; if (!it) warnOff = true })
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            SectionCard("Notificações") {
                Text(
                    "Na próxima etapa o Android vai pedir permissão para notificar. Permita para receber emergências, mensagens, confirmações e pedidos de ajuste dos pacientes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = askPermission, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continuar") }
    }

    if (warnOff) {
        AlertDialog(
            onDismissRequest = { warnOff = false },
            title = { Text("Tem certeza?") },
            text = { Text("É indicado manter essas notificações ativadas. Você pode mudar isso depois em Ajustes.") },
            confirmButton = { TextButton(onClick = { warnOff = false }) { Text("Entendi") } },
            dismissButton = {
                TextButton(onClick = { sessionReminder = true; moodReminder = true; warnOff = false }) { Text("Manter ativadas") }
            },
        )
    }
}
