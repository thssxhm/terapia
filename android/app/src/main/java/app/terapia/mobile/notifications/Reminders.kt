package app.terapia.mobile.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.terapia.mobile.data.Dates
import app.terapia.mobile.data.Prefs
import app.terapia.mobile.data.Repo
import app.terapia.mobile.data.supabase
import io.github.jan.supabase.auth.auth
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Lembretes locais (funcionam sem internet no horário do lembrete):
 *  - psicólogo: lembrete das sessões dos pacientes com o "sininho" ligado
 *  - paciente: lembrete de sessão (1h antes) e lembrete diário do humor
 * A lista é sincronizada ao abrir o app e a cada 12 horas.
 */
object Reminders {
    private const val TAG_SESSION = "session_reminder"
    private const val SYNC_PERIODIC = "reminder_sync_periodic"
    private const val SYNC_NOW = "reminder_sync_now"
    private const val MOOD_DAILY = "mood_daily"

    fun start(context: Context) {
        val wm = WorkManager.getInstance(context)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderSyncWorker>(12, TimeUnit.HOURS).setConstraints(net).build()
        )
        wm.enqueueUniqueWork(
            SYNC_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderSyncWorker>().setConstraints(net).build()
        )
    }

    fun stopAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(SYNC_PERIODIC)
        wm.cancelUniqueWork(MOOD_DAILY)
        wm.cancelAllWorkByTag(TAG_SESSION)
    }

    fun scheduleMood(context: Context, enabled: Boolean, hour: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(MOOD_DAILY); return }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        wm.enqueueUniquePeriodicWork(
            MOOD_DAILY, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ShowReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        "channel" to NotificationChannels.MOOD,
                        "title" to "Como você está hoje? 💬",
                        "body" to "Toque para registrar seu humor do dia.",
                        "type" to "mood_reminder",
                    )
                ).build()
        )
    }

    internal fun scheduleSession(context: Context, sessionId: String, at: Instant, title: String, body: String) {
        val delay = ChronoUnit.SECONDS.between(Instant.now(), at)
        if (delay <= 0) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "session_$sessionId", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ShowReminderWorker>()
                .addTag(TAG_SESSION)
                .setInitialDelay(delay, TimeUnit.SECONDS)
                .setInputData(
                    Data.Builder()
                        .putString("channel", NotificationChannels.REMINDERS)
                        .putString("title", title)
                        .putString("body", body)
                        .putString("type", "session_reminder")
                        .build()
                ).build()
        )
    }

    internal fun cancelSessionReminders(context: Context) =
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_SESSION)
}

/** Mostra a notificação de lembrete. */
class ShowReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Notifier.show(
            applicationContext,
            id = (inputData.getString("title") + id).hashCode(),
            channel = inputData.getString("channel") ?: NotificationChannels.REMINDERS,
            title = inputData.getString("title") ?: "Terapia",
            body = inputData.getString("body").orEmpty(),
            type = inputData.getString("type"),
        )
        return Result.success()
    }
}

/** Busca as próximas sessões e agenda os lembretes. */
class ReminderSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        sync()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    private suspend fun sync() {
        supabase.auth.awaitInitialization()
        if (supabase.auth.currentUserOrNull() == null) return
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val profile = Repo.myProfile() ?: return
        val now = Instant.now()
        val sessions = Repo.sessionsBetween(now, now.plus(4, ChronoUnit.DAYS)).filter { it.status != "cancelled" }

        Reminders.cancelSessionReminders(ctx)
        if (profile.role == "psychologist") {
            val patients = Repo.myPatients().filter { it.reminderEnabled }.associateBy { it.id }
            sessions.forEach { s ->
                val p = patients[s.patientId] ?: return@forEach
                Reminders.scheduleSession(
                    ctx, s.id, Dates.instant(s.startsAt).minus(p.reminderMinutes.toLong(), ChronoUnit.MINUTES),
                    "Atendimento em ${p.reminderMinutes} min",
                    "${p.fullName} — ${Dates.short(s.startsAt)}",
                )
            }
        } else if (profile.role == "patient") {
            if (prefs.get(Prefs.NOTIF_SESSION, true)) {
                sessions.forEach { s ->
                    Reminders.scheduleSession(
                        ctx, s.id, Dates.instant(s.startsAt).minus(60, ChronoUnit.MINUTES),
                        "Sua sessão é daqui a 1 hora",
                        Dates.full(s.startsAt),
                    )
                }
            }
            Reminders.scheduleMood(ctx, prefs.get(Prefs.NOTIF_MOOD, true), prefs.get(Prefs.MOOD_HOUR, 20))
        }
    }
}
