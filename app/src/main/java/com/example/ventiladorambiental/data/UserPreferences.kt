package com.example.ventiladorambiental.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

/** Horario de ventilación automática. Minutos desde 00:00; días 1 = lunes … 7 = domingo. */
data class Schedule(val startMinutes: Int, val endMinutes: Int, val days: Set<Int>) {
    companion object {
        val DEFAULT = Schedule(8 * 60, 22 * 60, (1..7).toSet())
    }
}

/** Umbrales del modo automático. */
data class AutomationRules(
    val tempThreshold: Int = 26,
    val humidityThreshold: Int = 70,
    val requirePresence: Boolean = true
)

data class UserProfile(val name: String, val phone: String, val email: String)

data class AppSettings(val notificationsEnabled: Boolean, val keepScreenOn: Boolean)

class UserPreferences(private val context: Context) {

    companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val SCHEDULE_START = intPreferencesKey("schedule_start")
        private val SCHEDULE_END = intPreferencesKey("schedule_end")
        private val SCHEDULE_DAYS = stringPreferencesKey("schedule_days")
        private val RULE_TEMP = intPreferencesKey("rule_temp")
        private val RULE_HUMIDITY = intPreferencesKey("rule_humidity")
        private val RULE_PRESENCE = booleanPreferencesKey("rule_presence")
        private val PROFILE_NAME = stringPreferencesKey("profile_name")
        private val PROFILE_PHONE = stringPreferencesKey("profile_phone")
        private val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        private val SETTING_NOTIFICATIONS = booleanPreferencesKey("setting_notifications")
        private val SETTING_KEEP_SCREEN = booleanPreferencesKey("setting_keep_screen")

        const val TEMP_MIN = 20
        const val TEMP_MAX = 34
        const val HUMIDITY_MIN = 40
        const val HUMIDITY_MAX = 90
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED] = completed }
    }

    // ---------- Horario ----------

    val schedule: Flow<Schedule> = context.dataStore.data.map { prefs ->
        val days = prefs[SCHEDULE_DAYS]
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.toSet()
        Schedule(
            startMinutes = (prefs[SCHEDULE_START] ?: Schedule.DEFAULT.startMinutes).coerceIn(0, 1439),
            endMinutes = (prefs[SCHEDULE_END] ?: Schedule.DEFAULT.endMinutes).coerceIn(0, 1439),
            days = if (days.isNullOrEmpty()) Schedule.DEFAULT.days else days
        )
    }

    suspend fun saveSchedule(schedule: Schedule) {
        require(schedule.days.isNotEmpty()) { "El horario necesita al menos un día" }
        context.dataStore.edit { prefs ->
            prefs[SCHEDULE_START] = schedule.startMinutes.coerceIn(0, 1439)
            prefs[SCHEDULE_END] = schedule.endMinutes.coerceIn(0, 1439)
            prefs[SCHEDULE_DAYS] = schedule.days.sorted().joinToString(",")
        }
    }

    // ---------- Reglas del modo automático ----------

    val rules: Flow<AutomationRules> = context.dataStore.data.map { prefs ->
        val d = AutomationRules()
        AutomationRules(
            tempThreshold = (prefs[RULE_TEMP] ?: d.tempThreshold).coerceIn(TEMP_MIN, TEMP_MAX),
            humidityThreshold = (prefs[RULE_HUMIDITY] ?: d.humidityThreshold).coerceIn(HUMIDITY_MIN, HUMIDITY_MAX),
            requirePresence = prefs[RULE_PRESENCE] ?: d.requirePresence
        )
    }

    suspend fun saveRules(rules: AutomationRules) {
        context.dataStore.edit { prefs ->
            prefs[RULE_TEMP] = rules.tempThreshold.coerceIn(TEMP_MIN, TEMP_MAX)
            prefs[RULE_HUMIDITY] = rules.humidityThreshold.coerceIn(HUMIDITY_MIN, HUMIDITY_MAX)
            prefs[RULE_PRESENCE] = rules.requirePresence
        }
    }

    // ---------- Perfil ----------

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            name = prefs[PROFILE_NAME] ?: "",
            phone = prefs[PROFILE_PHONE] ?: "",
            email = prefs[PROFILE_EMAIL] ?: ""
        )
    }

    suspend fun saveProfile(name: String, phone: String) {
        context.dataStore.edit { prefs ->
            prefs[PROFILE_NAME] = name.trim().take(60)
            prefs[PROFILE_PHONE] = phone.trim().take(20)
        }
    }

    suspend fun saveEmail(email: String) {
        context.dataStore.edit { prefs -> prefs[PROFILE_EMAIL] = email.trim().lowercase().take(120) }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs -> prefs.remove(PROFILE_EMAIL) }
    }

    // ---------- Ajustes ----------

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            notificationsEnabled = prefs[SETTING_NOTIFICATIONS] ?: true,
            keepScreenOn = prefs[SETTING_KEEP_SCREEN] ?: false
        )
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SETTING_NOTIFICATIONS] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SETTING_KEEP_SCREEN] = enabled }
    }
}
