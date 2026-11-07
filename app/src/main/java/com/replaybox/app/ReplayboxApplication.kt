package com.replaybox.app

import android.app.Application
import android.content.Context
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
import com.replaybox.core.account.CredentialStore
import com.replaybox.core.data.CatalogDao
import com.replaybox.core.health.CiHealthSource
import com.replaybox.core.health.LocalHealthStore
import com.replaybox.core.health.SharedPrefsLocalHealthStore
import com.replaybox.core.data.CatalogStore
import com.replaybox.core.data.ProgressRecorder
import com.replaybox.core.data.ReplayboxDatabase
import com.replaybox.core.data.RoomCatalogDao
import com.replaybox.provider.m6.M6Auth
import com.replaybox.provider.tf1.Tf1Auth
import java.util.UUID

class ReplayboxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: ReplayboxApplication

        /** TF1 sign-in session: token cache + encrypted credential store. */
        val tf1Auth: Tf1Auth by lazy { Tf1Auth(CredentialStore(instance, "tf1")) }

        /** M6+ sign-in session (Paramètres > M6+); the M6 Chaînes exist only while it has credentials. */
        val m6Auth: M6Auth by lazy { M6Auth(CredentialStore(instance, "m6"), deviceId = ::m6DeviceId) }

        /**
         * `x-auth-device-id` of the M6+ chain: a random UUID made once per install and kept in
         * plain prefs (an install identifier, not a secret; reinstalling makes a new one).
         */
        @Synchronized
        private fun m6DeviceId(): String {
            val prefs = instance.getSharedPreferences("replaybox_m6_device", Context.MODE_PRIVATE)
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            return id
        }

        private const val KEY_DEVICE_ID = "device_id"

        private val database: ReplayboxDatabase by lazy {
            Room.databaseBuilder(instance, ReplayboxDatabase::class.java, "replaybox.db")
                .addMigrations(
                    ReplayboxDatabase.MIGRATION_1_2,
                    ReplayboxDatabase.MIGRATION_2_3,
                    ReplayboxDatabase.MIGRATION_3_4,
                    ReplayboxDatabase.MIGRATION_4_5,
                )
                .fallbackToDestructiveMigration()
                .build()
        }

        /** Santé observed on this box (catalogue refreshes and Flux resolutions per Provider group). */
        val localHealth: LocalHealthStore by lazy { SharedPrefsLocalHealthStore(instance) }

        /** Last night's CI check, from the public `status` branch (SPEC §Health-check). */
        val ciHealth: CiHealthSource by lazy { CiHealthSource() }

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
