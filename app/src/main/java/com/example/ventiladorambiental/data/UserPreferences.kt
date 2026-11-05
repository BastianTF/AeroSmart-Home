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

class UserPreferences(private val context: Context) {

    companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val SCHEDULE_START = intPreferencesKey("schedule_start")
        private val SCHEDULE_END = intPreferencesKey("schedule_end")
        private val SCHEDULE_DAYS = stringPreferencesKey("schedule_days")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = completed
        }
    }

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
}
