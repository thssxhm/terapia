package app.terapia.mobile.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.terapia.mobile.auth.GoogleAuth
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.friendlyError
import app.terapia.mobile.data.supabase
import app.terapia.mobile.ui.components.Field
import app.terapia.mobile.ui.components.Logo
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Fluxo de entrada: escolha (psicólogo/paciente) → login. */
@Composable
fun AuthFlow() {
    var role by rememberSaveable { mutableStateOf<String?>(null) }
    when (role) {
        null -> WelcomeScreen { role = it }
        "psychologist" -> PsychologistLogin { role = null }
        else -> PatientLogin { role = null }
    }
}

@Composable
private fun WelcomeScreen(onChoose: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(140.dp)
        Spacer(Modifier.height(12.dp))
        Text("Terapia", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "O cuidado entre sessões, conectando psicólogo e paciente.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = { onChoose("psychologist") }, Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.Psychology, null); Spacer(Modifier.size(8.dp)); Text("Sou psicólogo(a)")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onChoose("patient") }, Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.Person, null); Spacer(Modifier.size(8.dp)); Text("Sou paciente")
        }
    }
}

@Composable
private fun AuthScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
        }
        Logo(88.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun ErrorOrInfo(error: String?, info: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)) }
    info?.let { Text(it, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)) }
}

@Composable
private fun GoogleButton(onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = { scope.launch { runCatching { GoogleAuth.signIn(context) }.onFailure { onError(friendlyError(it)) } } },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("G  Entrar com o Google", fontWeight = FontWeight.SemiBold) }
}

// ---------------------------------------------------------------------------------------------
// PSICÓLOGO
// ---------------------------------------------------------------------------------------------
@Composable
private fun PsychologistLogin(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var crp by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        error = null; info = null; busy = true
        scope.launch {
            runCatching { block() }.onFailure { error = friendlyError(it) }
            busy = false
        }
    }

    AuthScaffold("Área do psicólogo", onBack) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Entrar") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Criar conta") })
        }
        Spacer(Modifier.height(16.dp))
        ErrorOrInfo(error, info)

        if (tab == 0) {
            Field(email, { email = it.trim() }, "E-mail", keyboard = KeyboardType.Email)
            Spacer(Modifier.height(8.dp))
            if (!codeSent) {
                Field(password, { password = it }, "Senha", password = true)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { run { supabase.auth.signInWith(Email) { this.email = email; this.password = password } } },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Entrar") }
                TextButton(onClick = {
                    run {
                        supabase.auth.signInWith(OTP) { this.email = email; createUser = false }
                        codeSent = true; info = "Enviamos um código para $email"
                    }
                }, enabled = !busy && email.isNotBlank()) { Text("Esqueci a senha / entrar com código") }
            } else {
                Field(code, { code = it.filter(Char::isDigit).take(6) }, "Código de 6 dígitos", keyboard = KeyboardType.NumberPassword)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { run { verifyCode(email, code) } },
                    enabled = !busy && code.length == 6,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Entrar com código") }
                TextButton(onClick = { codeSent = false }) { Text("Usar senha") }
            }
        } else {
            Field(name, { name = it }, "Nome completo")
            Spacer(Modifier.height(8.dp))
            Field(crp, { crp = it }, "CRP (ex.: 06/123456)")
            Spacer(Modifier.height(8.dp))
            Field(email, { email = it.trim() }, "E-mail", keyboard = KeyboardType.Email)
            Spacer(Modifier.height(8.dp))
            Field(password, { password = it }, "Senha (mín. 6 caracteres)", password = true)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    run {
                        supabase.auth.signUpWith(Email) {
                            this.email = email; this.password = password
                            data = buildJsonObject { put("full_name", name) }
                        }
                        if (supabase.auth.currentSessionOrNull() != null) {
                            Repo.becomePsychologist(name, crp)
                        } else {
                            info = "Conta criada! Abra o link de confirmação enviado para $email e depois entre."
                            tab = 0
                        }
                    }
                },
                enabled = !busy && name.isNotBlank() && crp.isNotBlank() && email.isNotBlank() && password.length >= 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Criar conta") }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        GoogleButton { error = it }
    }
}

// ---------------------------------------------------------------------------------------------
// PACIENTE
// ---------------------------------------------------------------------------------------------
@Composable
private fun PatientLogin(onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("code") }   // code | password
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        error = null; info = null; busy = true
        scope.launch {
            runCatching { block() }.onFailure { error = friendlyError(it) }
            busy = false
        }
    }

    AuthScaffold("Área do paciente", onBack) {
        Text(
            "Use o e-mail cadastrado pelo seu psicólogo. O código de acesso chegou nesse e-mail.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ErrorOrInfo(error, info)
        Field(email, { email = it.trim() }, "E-mail", keyboard = KeyboardType.Email)
        Spacer(Modifier.height(8.dp))
        if (mode == "code") {
            Field(code, { code = it.filter(Char::isDigit).take(6) }, "Código de 6 dígitos", keyboard = KeyboardType.NumberPassword)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { run { verifyCode(email, code) } },
                enabled = !busy && email.isNotBlank() && code.length == 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Entrar") }
            TextButton(onClick = {
                run {
                    supabase.auth.signInWith(OTP) { this.email = email; createUser = false }
                    info = "Enviamos um novo código para $email"
                }
            }, enabled = !busy && email.isNotBlank()) { Text("Enviar novo código") }
            TextButton(onClick = { mode = "password" }) { Text("Já criei uma senha") }
        } else {
            Field(password, { password = it }, "Senha", password = true)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { run { supabase.auth.signInWith(Email) { this.email = email; this.password = password } } },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Entrar") }
            TextButton(onClick = { mode = "code" }) { Text("Entrar com código") }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        GoogleButton { error = it }
    }
}

/** Verifica o código de 6 dígitos enviado por e-mail. */
private suspend fun verifyCode(email: String, code: String) {
    try {
        supabase.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)
    } catch (e: Exception) {
        // Códigos gerados pelo convite são do tipo "magic link"
        supabase.auth.verifyEmailOtp(type = OtpType.Email.MAGIC_LINK, email = email, token = code)
    }
}

// ---------------------------------------------------------------------------------------------
// CONTA SEM PERFIL DEFINIDO
// ---------------------------------------------------------------------------------------------
@Composable
fun RoleSetupScreen(onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(supabase.auth.currentUserOrNull()?.userMetadata?.get("full_name")?.toString()?.trim('"') ?: "") }
    var crp by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(88.dp)
        Spacer(Modifier.height(8.dp))
        Text("Complete seu cadastro", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Se você é psicólogo(a), informe seu nome e CRP. Se você é paciente, peça ao seu psicólogo para cadastrar este e-mail no app.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
        Field(name, { name = it }, "Nome completo")
        Spacer(Modifier.height(8.dp))
        Field(crp, { crp = it }, "CRP")
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    runCatching { Repo.becomePsychologist(name, crp) }
                        .onSuccess { onDone() }
                        .onFailure { error = friendlyError(it) }
                    busy = false
                }
            },
            enabled = !busy && name.isNotBlank() && crp.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Sou psicólogo(a) — continuar") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { onDone() }) { Text("Já fui convidado(a) como paciente — verificar de novo") }
        TextButton(onClick = { scope.launch { supabase.auth.signOut() } }) { Text("Sair") }
    }
}
