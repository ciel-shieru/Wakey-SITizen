package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ConcessionPassCosts(
    val toCampusCostInput: String = "",
    val fromCampusCostInput: String = ""
)

private val TO_CAMPUS_COST = stringPreferencesKey("concession_to_campus_cost")
private val FROM_CAMPUS_COST = stringPreferencesKey("concession_from_campus_cost")

fun Flow<Preferences>.concessionPassCostsFlow(): Flow<ConcessionPassCosts> = map { preferences ->
    ConcessionPassCosts(
        toCampusCostInput = preferences[TO_CAMPUS_COST].orEmpty(),
        fromCampusCostInput = preferences[FROM_CAMPUS_COST].orEmpty()
    )
}

/** Saves only the calculator's own inputs; this must not trigger alarm config sync. */
suspend fun DataStore<Preferences>.saveConcessionPassCosts(
    toCampusCostInput: String,
    fromCampusCostInput: String
) {
    edit { preferences ->
        if (toCampusCostInput.isEmpty()) {
            preferences.remove(TO_CAMPUS_COST)
        } else {
            preferences[TO_CAMPUS_COST] = toCampusCostInput
        }

        if (fromCampusCostInput.isEmpty()) {
            preferences.remove(FROM_CAMPUS_COST)
        } else {
            preferences[FROM_CAMPUS_COST] = fromCampusCostInput
        }
    }
}
