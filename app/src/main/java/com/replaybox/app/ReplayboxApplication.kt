package com.replaybox.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.replaybox.BuildConfig
import com.replaybox.app.update.AppUpdater
import com.replaybox.app.update.AppVersion
import com.replaybox.app.update.GitHubUpdateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import com.replaybox.core.data.CatalogDao
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.data.ProgressRecorder
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
                .addMigrations(ReplayboxDatabase.MIGRATION_1_2, ReplayboxDatabase.MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }

        /** Room-backed catalog cache (ADR 0002): catalogue cache-first, Flux live. */
        val catalog: CatalogStore by lazy { CatalogStore(catalogDao()) }

        private fun catalogDao(): CatalogDao = RoomCatalogDao(database, database.rawDao())

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Remembered audio kind and quality cap (decision Q3). */
        val playbackPreferences: com.replaybox.app.ui.PlaybackPreferenceStore by lazy {
            com.replaybox.app.ui.SharedPrefsPlaybackPreferenceStore(instance)
        }

        /** Playhead writes that outlive the player screen (see ProgressRecorder). */
        val progress: ProgressRecorder by lazy { ProgressRecorder(catalog, appScope) }

        /** Self-update from the GitHub Releases (SPEC §Auto-update). */
        val updater: AppUpdater by lazy {
            AppUpdater(
                current = AppVersion.parse(BuildConfig.VERSION_NAME)
                    ?: error("versionName ${BuildConfig.VERSION_NAME} is not X.Y.Z"),
                source = GitHubUpdateSource(userAgent = "Replaybox/${BuildConfig.VERSION_NAME}"),
                directory = File(instance.cacheDir, "updates"),
                log = { Log.i("AppUpdater", it) },
            )
        }

        /** Launch-time check; outlives the Activity so a download is not cut by navigation. */
        fun checkForUpdate() {
            appScope.launch { updater.check() }
        }
    }
}
