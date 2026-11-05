package app.terapia.mobile.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Canais de notificação. Usam o som/vibração PADRÃO do Android; o usuário pode
 * personalizar cada canal em Configurações > Apps > Terapia > Notificações.
 * Os IDs precisam ser iguais aos usados na Edge Function "push".
 */
object NotificationChannels {
    const val EMERGENCY = "emergencias"
    const val MESSAGES = "mensagens"
    const val NOTICES = "avisos"
    const val REMINDERS = "lembretes"
    const val MOOD = "humor"

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(EMERGENCY, "Emergências", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alertas de emergência enviados pelos pacientes"
                    enableVibration(true)
                },
                NotificationChannel(MESSAGES, "Mensagens", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Novas mensagens no chat"
                },
                NotificationChannel(NOTICES, "Avisos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Confirmações, cancelamentos, pedidos de horário e gráficos de humor"
                },
                NotificationChannel(REMINDERS, "Lembretes de sessão", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Lembretes antes dos atendimentos"
                },
                NotificationChannel(MOOD, "Humor do dia", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Lembrete diário para registrar como você está"
                },
            )
        )
    }

    /** Abre a tela do sistema para personalizar sons e alertas do app. */
    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
