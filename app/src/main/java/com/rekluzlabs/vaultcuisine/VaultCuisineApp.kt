package com.rekluzlabs.vaultcuisine

import android.app.Application
import com.rekluzlabs.vaultcuisine.data.local.AppDatabase

class VaultCuisineApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
