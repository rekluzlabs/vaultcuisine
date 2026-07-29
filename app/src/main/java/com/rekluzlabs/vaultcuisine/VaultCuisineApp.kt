package com.rekluzlabs.vaultcuisine

import android.app.Application
import com.rekluzlabs.vaultcuisine.data.AppPreferences
import com.rekluzlabs.vaultcuisine.data.local.AppDatabase
import com.rekluzlabs.vaultcuisine.timer.TimerNotificationHelper

class VaultCuisineApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        TimerNotificationHelper.createChannels(this)
    }
}
