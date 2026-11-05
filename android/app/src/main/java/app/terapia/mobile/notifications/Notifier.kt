package app.terapia.mobile.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.terapia.mobile.MainActivity
import app.terapia.mobile.R

object Notifier {
    const val EXTRA_TYPE = "notif_type"
    const val EXTRA_PATIENT = "notif_patient"
    const val EXTRA_REF = "notif_ref"

    fun canNotify(context: Context) =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        type: String? = null,
        patientId: String? = null,
        refId: String? = null,
    ) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_PATIENT, patientId)
            putExtra(EXTRA_REF, refId)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val emergency = channel == NotificationChannels.EMERGENCY
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(if (type == "emergency_red") 0xFFE53935.toInt() else if (emergency) 0xFFFBC02D.toInt() else 0xFF9B7BDB.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (emergency) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (emergency) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) { /* permissão revogada */ }
    }
}
