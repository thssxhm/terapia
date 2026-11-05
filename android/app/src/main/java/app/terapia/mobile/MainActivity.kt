package app.terapia.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.terapia.mobile.data.Prefs
import app.terapia.mobile.notifications.Notifier
import app.terapia.mobile.ui.AppRoot
import app.terapia.mobile.ui.theme.TerapiaTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** Para onde ir quando o usuário toca numa notificação. */
data class NotifTarget(val type: String, val patientId: String?, val refId: String?)

/** Controle do bloqueio por biometria (bloqueia ao abrir e após 2 min em segundo plano). */
object AppLock {
    val locked = MutableStateFlow(true)
    private var backgroundAt = 0L
    fun onBackground() { backgroundAt = System.currentTimeMillis() }
    fun onForeground() {
        if (backgroundAt != 0L && System.currentTimeMillis() - backgroundAt > 120_000) locked.value = true
    }
}

class MainActivity : AppCompatActivity() {
    val notifTarget = MutableStateFlow<NotifTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = AppLock.onBackground()
            override fun onStart(owner: LifecycleOwner) = AppLock.onForeground()
        })
        val prefs = Prefs(applicationContext)
        setContent {
            val theme by prefs.theme.collectAsState(initial = "system")
            TerapiaTheme(theme) { AppRoot(this, prefs) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val type = intent?.getStringExtra(Notifier.EXTRA_TYPE) ?: return
        notifTarget.value = NotifTarget(
            type,
            intent.getStringExtra(Notifier.EXTRA_PATIENT)?.ifBlank { null },
            intent.getStringExtra(Notifier.EXTRA_REF)?.ifBlank { null },
        )
    }
}
