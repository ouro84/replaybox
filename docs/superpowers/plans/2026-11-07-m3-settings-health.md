# M3 step 3 — Settings per Provider + Santé Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Paramètres into a list of Providers (TF1+, France TV; M6+ joins in step 4), each with its own page showing its account (when it needs one) and its Santé: last night's CI check plus what this box observed.

**Architecture:** A `ProviderGroup` describes one broadcaster (id matching the health-check probe name, display name, its Chaînes, its optional `ProviderAccount`). Credentials move to a generic `CredentialStore(context, providerId)` (same encrypted prefs file name for TF1, so existing sign-ins survive). Santé = `CiHealthSource` (reads `healthcheck.json` from the `status` branch) + `LocalHealthStore` (fed by a `HealthTrackingProvider` decorator placed under `CachedProvider`), combined by `Health.combine`.

**Tech Stack:** Kotlin, Compose for TV (Material3 TV components already in use), OkHttp, kotlinx.serialization JSON, SharedPreferences / EncryptedSharedPreferences, JUnit4 + Truth.

**Spec:** `ROADMAP.md` §M3 (step « Paramètres par Provider »), `CONTEXT.md` (Santé, Provider), `SPEC.md` §Health-check (file format of `healthcheck.json`, written by the CI PR `ci/healthcheck-status-branch`), `DESIGN.md` (Components, Focus targets, Copy, Failure states), memory rule: alerts are in-app, never system notifications.

## Global Constraints

- UI strings in French in `values/strings.xml`; code/tests/commits in English.
- Credentials only in EncryptedSharedPreferences with a Keystore master key; never logged. TF1 prefs file name stays `replaybox_tf1_credentials` (keys `email`, `password`).
- CI file URL: `https://raw.githubusercontent.com/ouro84/replaybox/status/healthcheck.json`. Format (SPEC §Health-check): `{generatedAt, runUrl, providers:[{provider, label, ok, step, error, checkedAt}]}`. Older than **48 h** (by `generatedAt`) or unreadable → CI state `UNKNOWN`.
- Provider group ids = probe names: `tf1`, `francetv` (later `m6`).
- Badge combination: `KO` if either side is KO; else `OK` if either side is OK; else `UNKNOWN`. (An unknown side is missing evidence, not a failure.)
- Badge shown only in Paramètres (list + Provider page). No new Home alert, no system notification.
- Local Santé: last catalogue refresh outcome and last Flux resolution outcome per group, each with timestamp and short error; a success replaces a previous failure.

## Review Focus

1. Box offline when opening Paramètres → CI line « inconnue », page still opens instantly (fetch never blocks the UI). Test: `CiHealthParserTest.unreadableIsUnknown` + fetch on `Dispatchers.IO` with a 5 s timeout.
2. `healthcheck.json` from 3 days ago with `ok: true` → « inconnue », never a stale OK. Test: `CiHealthParserTest.olderThan48hIsUnknown`.
3. Existing TF1 sign-in after the update → still signed in (same prefs file). Test: `CredentialStoreNamingTest.tf1KeepsLegacyFileName` on the pure name function.
4. Provider whose Flux fails because the account is wrong → local line shows the KO with its message; badge KO. Test: `HealthTrackingProviderTest.streamFailureRecorded`.
5. A France TV Chaîne failing while the 4 others succeed → group catalogue state is KO (one Chaîne down is a real problem). Test: `LocalHealthTest.anyChannelFailureMakesGroupKo` — decide per refresh pass: KO if any Chaîne of the group failed in that pass.

---

### Task 1: Generic credentials + `ProviderAccount`

**Files:**
- Create: `app/src/main/java/com/replaybox/core/account/CredentialStore.kt` (moved from `provider/tf1/Tf1CredentialStore.kt`, constructor `(context: Context, providerId: String)`, `companion fun fileName(providerId: String) = "replaybox_${providerId}_credentials"`)
- Create: `app/src/main/java/com/replaybox/core/account/ProviderAccount.kt`
- Delete: `app/src/main/java/com/replaybox/provider/tf1/Tf1CredentialStore.kt`
- Modify: `app/src/main/java/com/replaybox/provider/tf1/Tf1Auth.kt` (takes `CredentialStore`, implements `ProviderAccount`), `app/src/main/java/com/replaybox/app/ReplayboxApplication.kt`
- Test: `app/src/test/java/com/replaybox/core/account/CredentialStoreNamingTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  interface ProviderAccount {
      fun storedEmail(): String?
      fun hasCredentials(): Boolean
      suspend fun signIn(email: String, password: String) // throws with a user-readable French message on failure
      fun signOut()
  }
  ```
- [ ] Step 1: test `tf1KeepsLegacyFileName`: `CredentialStore.fileName("tf1") == "replaybox_tf1_credentials"`; `fileName("m6") == "replaybox_m6_credentials"`.
- [ ] Step 2: run → FAIL. Step 3: implement (pure move + rename, no behavior change). Step 4: full suite + assembleDebug → PASS.
- [ ] Step 5: commit `refactor: generic per-Provider credential store`

### Task 2: Santé model, CI parser, local store, tracking decorator

**Files:**
- Create: `app/src/main/java/com/replaybox/core/health/Health.kt` — `enum class HealthState { OK, KO, UNKNOWN }`; `data class CiHealth(state, step: String?, error: String?, checkedAt: Long?)`; `data class LocalHealth(catalogue: Outcome?, stream: Outcome?)`, `data class Outcome(ok: Boolean, at: Long, error: String?)`; `object Health { fun combine(ci: HealthState, local: HealthState): HealthState; fun localState(local: LocalHealth): HealthState }` (`localState`: KO if the latest catalogue or stream outcome is a failure, OK if any outcome exists and none failed, else UNKNOWN).
- Create: `app/src/main/java/com/replaybox/core/health/CiHealthParser.kt` — `fun parse(json: String?, now: Long): Map<String, CiHealth>`; empty map means unknown for everyone.
- Create: `app/src/main/java/com/replaybox/core/health/CiHealthSource.kt` — OkHttp GET of the URL (5 s call timeout, `Dispatchers.IO`), returns the body or null; in-memory cache for the process lifetime, refetched when Paramètres opens if older than 10 min.
- Create: `app/src/main/java/com/replaybox/core/health/LocalHealthStore.kt` — `interface LocalHealthStore { fun record(groupId: String, kind: Kind, ok: Boolean, error: String?, at: Long); fun read(groupId: String): LocalHealth }`, `enum Kind { CATALOGUE, STREAM }`; `SharedPrefsLocalHealthStore(context)` (file `replaybox_health`) + `InMemoryLocalHealthStore` in main (used by tests and previews).
- Create: `app/src/main/java/com/replaybox/provider/api/HealthTrackingProvider.kt` — `class HealthTrackingProvider(inner: Provider, groupId: String, store: LocalHealthStore, clock: () -> Long = System::currentTimeMillis) : Provider`: records `shows()` and `stream()` outcomes (not `seasons`/`episodes`/`search`), rethrows; `CancellationException` is not recorded. Error text = exception class simple name + message, cut to 120 chars.
- Modify: `app/src/main/java/com/replaybox/core/data/CatalogStore.kt` `refreshListings` — group-level catalogue outcome per pass: a `refreshListings` pass records through the decorator automatically (each Chaîne's `origin.shows`); make the store compute KO-if-any by recording the pass result once per group: add optional `onPassDone: (failedChannelIds: Set<String>) -> Unit` **or** keep per-Chaîne records and let `LocalHealth` keep the worst of the pass — pick the simpler one that satisfies Review Focus 5 and document it in KDoc.
- Test: `CiHealthParserTest` (`freshOkAndKo`, `olderThan48hIsUnknown`, `unreadableIsUnknown` for null / `"{"` / missing `providers`, `unknownProviderIgnored`), `HealthTest` (`combineTable`: the 9 pairs per the Global Constraints rule; `localStateLatestFailureWins`), `HealthTrackingProviderTest` (`showsSuccessRecorded`, `streamFailureRecorded` with message, `cancellationNotRecorded`), `LocalHealthTest.anyChannelFailureMakesGroupKo`.
- [ ] Steps: failing tests → run (FAIL) → implement → run (PASS) → commit `feat: Santé model with CI and on-box signals`

### Task 3: Provider groups wiring

**Files:**
- Create: `app/src/main/java/com/replaybox/app/ui/ProviderGroups.kt` — `data class ProviderGroup(val id: String, val name: String, val channelIds: List<String>, val account: ProviderAccount?)`; `ProviderGroups.all`: `tf1` « TF1+ » (TF1 group Chaînes, `ReplayboxApplication.tf1Auth`), `francetv` « France TV » (FTV Chaînes, no account).
- Modify: `app/src/main/java/com/replaybox/app/ui/Providers.kt` — wrap each network Provider: `CachedProvider(HealthTrackingProvider(inner, groupId, ReplayboxApplication.localHealth), catalog)`.
- Modify: `app/src/main/java/com/replaybox/app/ReplayboxApplication.kt` — `localHealth: LocalHealthStore` and `ciHealth: CiHealthSource` singletons.
- [ ] Step: build + full suite green → commit `feat: group Providers for Paramètres and track their Santé`

### Task 4: Paramètres screens

**Files:**
- Create: `app/src/main/java/com/replaybox/app/ui/SettingsScreen.kt` — list of `ProviderGroups.all` as focusable rows (initial focus on the first row): name, badge (dot + label), account summary.
- Create: `app/src/main/java/com/replaybox/app/ui/ProviderScreen.kt` — Santé section with the two lines, then the account form when `account != null`.
- Modify: `app/src/main/java/com/replaybox/app/ui/LoginScreen.kt` → extract `AccountForm(account: ProviderAccount, providerName: String, onDone: () -> Unit)` used by `ProviderScreen`; remove the standalone screen.
- Modify: `app/src/main/java/com/replaybox/app/ui/MainActivity.kt` — `Screen.Settings`, `Screen.ProviderDetail(groupId: String)`; `RailDestination.SETTINGS → Screen.Settings`.
- Modify: `app/src/main/res/values/strings.xml` — exact copy:
  - `settings_title` « Paramètres »
  - `health_ok` « Fonctionne », `health_ko` « En panne », `health_unknown` « État inconnu »
  - `health_ci_line` « Vérification de cette nuit : %1$s », `health_box_line` « Sur cette box : %1$s »
  - `health_ci_unknown` « inconnue », `health_ci_failed` « échec à l’étape %1$s (%2$s) », `health_checked_at` « le %1$s à %2$s »
  - `health_box_none` « rien d’observé pour l’instant », `health_box_catalogue` « catalogue %1$s », `health_box_stream` « lecture %1$s »
  - `account_signed_in` « Connecté : %1$s », `account_signed_out` « Non connecté », `account_none` « Sans compte »
  - `login_title` becomes « Connexion %1$s » (the Provider name is the argument)
- Follow `DESIGN.md` (focus targets, copy, failure states, colors from `Theme.kt`); badge colors: OK uses the theme's success/primary tone, KO the error color, UNKNOWN the neutral/secondary text color; never color alone — the label is always shown.
- [ ] Step 1: build + suite green. Step 2: box check on `192.168.1.182:5555` (HDMI-CEC stays off): Paramètres lists TF1+ and France TV with badges; TF1+ page shows the two Santé lines and the account form with the stored e-mail; D-pad reaches every control, Back returns to the list then to Home; screenshots (strip ~33 bytes before `\x89PNG`). Step 3: commit `feat: Paramètres per Provider with Santé badge`.

### Task 5: SPEC + PR

- [ ] Update `SPEC.md` (new §Paramètres et Santé: groups, sources, 48 h rule, combination rule, local signal definition).
- [ ] Push `feat/m3-settings-health`, PR body (what, tests, box check, screenshots paths), merge when green and box-checked (Arnaud's autonomy grant, 2026-11-07).
