package app.terapia.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("terapia_prefs")

/** Preferências salvas só no aparelho (tema, tutorial visto, biometria, lembretes). */
class Prefs(private val context: Context) {
    companion object {
        val THEME = stringPreferencesKey("theme")                       // system | light | dark
        val ONBOARDING_PSY = booleanPreferencesKey("onboarding_psy")
        val ONBOARDING_PATIENT = booleanPreferencesKey("onboarding_patient")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val SECURITY_SETUP_DONE = booleanPreferencesKey("security_setup_done")
        val NOTIF_SESSION = booleanPreferencesKey("notif_session")      // lembrete de sessão (paciente)
        val NOTIF_MOOD = booleanPreferencesKey("notif_mood")            // lembrete do humor do dia
        val MOOD_HOUR = intPreferencesKey("mood_hour")
        val NOTIF_SETUP_DONE = booleanPreferencesKey("notif_setup_done")
    }

    val theme: Flow<String> = context.dataStore.data.map { it[THEME] ?: "system" }
    val all: Flow<androidx.datastore.preferences.core.Preferences> = context.dataStore.data

    suspend fun <T> get(key: androidx.datastore.preferences.core.Preferences.Key<T>, default: T): T =
        context.dataStore.data.first()[key] ?: default

    suspend fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun clearUserData() {
        context.dataStore.edit {
            val theme = it[THEME]
            it.clear()
            if (theme != null) it[THEME] = theme
        }
    }
}
