package com.replaybox.app

import android.app.Application
import androidx.room.Room
import com.replaybox.core.data.CatalogDao
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.data.ReplayboxDatabase
import com.replaybox.core.data.RoomCatalogDao
import com.replaybox.provider.tf1.Tf1Auth
import com.replaybox.provider.tf1.Tf1CredentialStore

class ReplayboxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: ReplayboxApplication

        /** TF1 sign-in session: token cache + encrypted credential store. */
        val tf1Auth: Tf1Auth by lazy { Tf1Auth(Tf1CredentialStore(instance)) }

        private val database: ReplayboxDatabase by lazy {
            Room.databaseBuilder(instance, ReplayboxDatabase::class.java, "replaybox.db")
                .fallbackToDestructiveMigration()
                .build()
        }

        /** Room-backed catalog cache (ADR 0002): catalogue cache-first, Flux live. */
        val catalog: CatalogStore by lazy { CatalogStore(catalogDao()) }

        private fun catalogDao(): CatalogDao = RoomCatalogDao(database, database.rawDao())
    }
}
