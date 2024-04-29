package com.voltix.wallet.common

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.voltix.wallet.data.common.data_store.AppDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class VoltixRelay @Inject constructor(private val appDataStore: AppDataStore) {
    var IsRelayEnabled: Boolean
        get() = runBlocking { appDataStore.readData(relayKey, false).first() }
        set(value) = runBlocking {
            appDataStore.editData { preferences ->
                preferences[relayKey] = value
            }
        }

    companion object {
        private val relayKey = booleanPreferencesKey(name = "relay_enabled")
    }
}