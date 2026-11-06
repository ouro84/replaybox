package com.replaybox.app.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonObject
import java.io.File

/** Network seam of the updater (SPEC §Auto-update). Tests use a fake; zero network. */
interface UpdateSource {
    /** The latest published release, or null when there is none. */
    suspend fun latestRelease(): JsonObject?

    /** Streams [url] into [target], following redirects. */
    suspend fun download(url: String, target: File)
}

sealed interface UpdateState {
    /** Up to date, offline, or nothing usable published: show nothing. */
    object Idle : UpdateState

    /** A newer release APK is fully downloaded and waits for the user's press. */
    data class Ready(val version: AppVersion, val apk: File) : UpdateState
}

/**
 * Launch-time self-update from the GitHub Releases. One GET per check; the APK
 * is downloaded only when the tag is strictly newer than the running version,
 * and reused if a complete copy is already on disk. Every failure is silent
 * (logged): an update must never get in the way of an evening.
 * The install intent is never fired from here — only on a user press (Home banner).
 */
class AppUpdater(
    private val current: AppVersion,
    private val source: UpdateSource,
    private val directory: File,
    private val log: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val mutex = Mutex()

    suspend fun check() {
        if (!mutex.tryLock()) return // a check is already running
        try {
            _state.value = runCatching { resolve() }
                .onFailure { log("update check failed: $it") }
                .getOrNull() ?: UpdateState.Idle
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun resolve(): UpdateState {
        val release = source.latestRelease()?.let(GitHubReleaseParser::parse)
        if (release == null || release.version <= current) {
            purge(keep = null)
            return UpdateState.Idle
        }
        directory.mkdirs()
        val apk = File(directory, "replaybox-${release.version}.apk")
        purge(keep = apk)
        if (apk.length() != release.apkSize) {
            val partial = File(directory, apk.name + ".part")
            partial.delete()
            try {
                source.download(release.apkUrl, partial)
                if (partial.length() != release.apkSize) {
                    log("update ${release.version}: size ${partial.length()} != ${release.apkSize}, discarded")
                    return UpdateState.Idle
                }
                apk.delete()
                if (!partial.renameTo(apk)) return UpdateState.Idle
            } finally {
                partial.delete()
            }
        }
        log("update ${release.version} ready")
        return UpdateState.Ready(release.version, apk)
    }

    /** Deletes every downloaded or partial APK except [keep]. */
    private fun purge(keep: File?) {
        directory.listFiles()?.forEach { file -> if (file != keep) file.delete() }
    }
}
