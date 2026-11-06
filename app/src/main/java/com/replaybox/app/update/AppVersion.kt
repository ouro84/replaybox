package com.replaybox.app.update

/** Semver X.Y.Z of the app, as tagged vX.Y.Z by scripts/release.ps1 (SPEC §Releases). */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val SEMVER = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)$""")

        /** "v0.3.1" or "0.3.1" -> 0.3.1; anything else (pre-release suffix, partial) -> null. */
        fun parse(raw: String): AppVersion? {
            val match = SEMVER.matchEntire(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
