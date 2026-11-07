package com.replaybox.core.health

import android.content.Context
import android.content.SharedPreferences

/**
 * Santé observed on this box, per Provider group (`tf1`, `francetv`). Fed by
 * [com.replaybox.provider.api.HealthTrackingProvider]; never leaves the device.
 *
 * Catalogue outcomes are kept per [source] Chaîne and Flux outcomes per group.
 * [read] reports the group's catalogue as KO while any of its Chaînes failed
 * its latest refresh: every refresh pass rewrites every Chaîne, so this is
 * « KO if any Chaîne failed in the last pass » (one Chaîne down is a real
 * problem), and a Chaîne's success replaces its own previous failure.
 */
interface LocalHealthStore {

    enum class Kind { CATALOGUE, STREAM }

    fun record(groupId: String, kind: Kind, ok: Boolean, error: String?, at: Long, source: String)

    fun read(groupId: String): LocalHealth

    companion object {
        internal fun key(groupId: String, kind: Kind, source: String): String = when (kind) {
            Kind.CATALOGUE -> "$groupId|catalogue|$source"
            Kind.STREAM -> "$groupId|stream"
        }

        internal fun catalogPrefix(groupId: String) = "$groupId|catalogue|"

        internal fun streamKey(groupId: String) = "$groupId|stream"

        /** Worst-first fold of a group's per-Chaîne catalogue outcomes. */
        internal fun foldCatalogue(outcomes: Collection<Outcome>): Outcome? =
            outcomes.filter { !it.ok }.maxByOrNull { it.at } ?: outcomes.maxByOrNull { it.at }

        fun encode(outcome: Outcome): String =
            "${if (outcome.ok) 1 else 0}|${outcome.at}|${outcome.error.orEmpty()}"

        fun decode(raw: String): Outcome? {
            val parts = raw.split('|', limit = 3)
            if (parts.size != 3) return null
            val ok = when (parts[0]) { "1" -> true; "0" -> false; else -> return null }
            val at = parts[1].toLongOrNull() ?: return null
            return Outcome(ok, at, parts[2].ifEmpty { null })
        }
    }
}

/** Process-only store, for tests and previews. */
class InMemoryLocalHealthStore : LocalHealthStore {
    private val outcomes = HashMap<String, Outcome>()

    @Synchronized
    override fun record(groupId: String, kind: LocalHealthStore.Kind, ok: Boolean, error: String?, at: Long, source: String) {
        outcomes[LocalHealthStore.key(groupId, kind, source)] = Outcome(ok, at, error)
    }

    @Synchronized
    override fun read(groupId: String): LocalHealth {
        val prefix = LocalHealthStore.catalogPrefix(groupId)
        return LocalHealth(
            catalogue = LocalHealthStore.foldCatalogue(outcomes.filterKeys { it.startsWith(prefix) }.values),
            stream = outcomes[LocalHealthStore.streamKey(groupId)],
        )
    }
}

/** Device store: plain SharedPreferences file `replaybox_health` (no secret in it). */
class SharedPrefsLocalHealthStore(context: Context) : LocalHealthStore {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("replaybox_health", Context.MODE_PRIVATE)
    }

    override fun record(groupId: String, kind: LocalHealthStore.Kind, ok: Boolean, error: String?, at: Long, source: String) {
        prefs.edit()
            .putString(LocalHealthStore.key(groupId, kind, source), LocalHealthStore.encode(Outcome(ok, at, error)))
            .apply()
    }

    override fun read(groupId: String): LocalHealth {
        val prefix = LocalHealthStore.catalogPrefix(groupId)
        val all = prefs.all
        val catalogue = all.filterKeys { it.startsWith(prefix) }.values
            .mapNotNull { (it as? String)?.let(LocalHealthStore::decode) }
        return LocalHealth(
            catalogue = LocalHealthStore.foldCatalogue(catalogue),
            stream = (all[LocalHealthStore.streamKey(groupId)] as? String)?.let(LocalHealthStore::decode),
        )
    }
}
