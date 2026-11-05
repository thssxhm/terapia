package app.terapia.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.terapia.mobile.AppLock
import app.terapia.mobile.MainActivity
import app.terapia.mobile.auth.Biometric
import app.terapia.mobile.data.Prefs
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.supabase
import app.terapia.mobile.notifications.Reminders
import app.terapia.mobile.notifications.TerapiaMessagingService
import app.terapia.mobile.ui.auth.AuthFlow
import app.terapia.mobile.ui.auth.RoleSetupScreen
import app.terapia.mobile.ui.auth.SetupScreen
import app.terapia.mobile.ui.components.AsyncContent
import app.terapia.mobile.ui.components.Loading
import app.terapia.mobile.ui.components.Logo
import app.terapia.mobile.ui.components.rememberAsync
import app.terapia.mobile.ui.onboarding.OnboardingScreen
import app.terapia.mobile.ui.patient.PatientApp
import app.terapia.mobile.ui.psy.PsyApp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

@Composable
fun AppRoot(activity: MainActivity, prefs: Prefs) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val status by supabase.auth.sessionStatus.collectAsState()
        when (status) {
            is SessionStatus.Initializing -> Loading()
            is SessionStatus.Authenticated -> {
                val userId = (status as SessionStatus.Authenticated).session.user?.id ?: ""
                AuthenticatedRoot(activity, prefs, userId)
            }
            else -> {
                if (supabase.auth.currentSessionOrNull() != null) {
                    AuthenticatedRoot(activity, prefs, supabase.auth.currentUserOrNull()?.id ?: "")
                } else {
                    AuthFlow()
                }
            }
        }
    }
}

/** Depois do login: decide entre psicólogo, paciente ou escolha de perfil. */
@Composable
private fun AuthenticatedRoot(activity: MainActivity, prefs: Prefs, userId: String) {
    val context = LocalContext.current
    val profileState = rememberAsync(userId) { Repo.myProfile() }
    val allPrefs by prefs.all.collectAsState(initial = null)
    val locked by AppLock.locked.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        TerapiaMessagingService.registerToken()
        Reminders.start(context)
    }

    AsyncContent(profileState) { profile ->
        val p = allPrefs
        if (p == null) { Loading(); return@AsyncContent }
        val biometricOn = p[Prefs.BIOMETRIC] == true
        LaunchedEffect(biometricOn) { if (!biometricOn) AppLock.locked.value = false }

        if (biometricOn && locked) {
            LockScreen(activity)
            return@AsyncContent
        }

        when (profile?.role) {
            "psychologist" -> when {
                p[Prefs.ONBOARDING_PSY] != true ->
                    OnboardingScreen(psychologist = true) { scope.launch { prefs.set(Prefs.ONBOARDING_PSY, true) } }
                p[Prefs.SECURITY_SETUP_DONE] != true -> SetupScreen(activity, prefs, psychologist = true)
                else -> PsyApp(activity, prefs, profile, onProfileChanged = profileState.reload)
            }
            "patient" -> when {
                p[Prefs.ONBOARDING_PATIENT] != true ->
                    OnboardingScreen(psychologist = false) { scope.launch { prefs.set(Prefs.ONBOARDING_PATIENT, true) } }
                p[Prefs.SECURITY_SETUP_DONE] != true -> SetupScreen(activity, prefs, psychologist = false)
                else -> PatientApp(activity, prefs, profile)
            }
            else -> RoleSetupScreen(onDone = profileState.reload)
        }
    }
}

@Composable
private fun LockScreen(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    val unlock = {
        Biometric.prompt(activity, "Desbloquear o Terapia", onSuccess = { AppLock.locked.value = false }, onError = {})
    }
    LaunchedEffect(Unit) { unlock() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(120.dp)
        Spacer(Modifier.height(16.dp))
        Text("Terapia", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = unlock) { Text("Desbloquear") }
        TextButton(onClick = { scope.launch { supabase.auth.signOut() } }) { Text("Sair da conta") }
    }
}

/** Pede a permissão de notificação (Android 13+). */
@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
    return {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onResult(true)
    }
}
