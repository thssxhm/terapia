package app.terapia.mobile.notifications

import app.terapia.mobile.data.Repo
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Recebe os pushes enviados pela Edge Function "push" (Firebase Cloud Messaging). */
class TerapiaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { Repo.saveDeviceToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val type = d["type"] ?: return
        Notifier.show(
            context = this,
            id = (d["notification_id"] ?: d["ref_id"] ?: type).hashCode(),
            channel = d["channel"] ?: NotificationChannels.NOTICES,
            title = d["title"] ?: "Terapia",
            body = d["body"].orEmpty(),
            type = type,
            patientId = d["patient_id"],
            refId = d["ref_id"],
        )
    }

    companion object {
        /** Registra o token deste celular para receber push (chamado após o login). */
        fun registerToken() {
            try {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        runCatching { Repo.saveDeviceToken(token) }
                    }
                }
            } catch (_: IllegalStateException) {
                // Firebase não configurado (falta app/google-services.json)
            }
        }

        fun unregisterToken(onDone: () -> Unit) {
            try {
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            runCatching { Repo.removeDeviceToken(token) }
                            onDone()
                        }
                    }
                    .addOnFailureListener { onDone() }
            } catch (_: IllegalStateException) {
                onDone()
            }
        }
    }
}
