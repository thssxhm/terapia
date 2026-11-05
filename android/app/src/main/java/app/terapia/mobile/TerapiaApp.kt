package app.terapia.mobile

import android.app.Application
import app.terapia.mobile.notifications.NotificationChannels

class TerapiaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
    }
}
