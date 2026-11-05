# AGENTS.md

Instructions pour tout agent de code (OpenCode, Claude, Hermes) travaillant dans ce repo.

## Ordre de lecture

1. `CONTEXT.md` — le glossaire fait loi : `Chaîne/Channel`, `Programme/Show`, `Saison/Season`,
   `Épisode/Episode`, `Flux/Stream`, `Progression/Progress`, `Genre/Genre`, `Provider`. Utilise ces
   termes tels quels ; propose un terme nouveau plutôt qu'un synonyme flou.
2. `docs/adr/` — les décisions archi (local-first, cache-first). Un changement qui contredit un ADR
   se signale et s'arrête ; les agents n'éditent pas les ADR.
3. `SPEC.md` — la vérité sur les API des diffuseurs (endpoints, persisted IDs, flux de résolution).
   Ne jamais inventer un endpoint ou un ID de requête : s'il manque, le mesurer d'abord (curl), puis
   ajouter la découverte au SPEC avec la fixture correspondante.

## Langues

Code, commentaires, tests, messages de commit : **anglais**. Chaînes UI : **français**, dans
`values/strings.xml`. Le vocabulaire UI utilise les termes français du glossaire (Programme, Épisode…).

## Tests

- Les parsers Provider se testent sur **fixtures JSON** enregistrées des API réelles — `src/test/resources/`.
- Zéro réseau dans les tests unitaires.
- Un comportement Provider nouveau = une fixture nouvelle. Réparer une casse API = mettre à jour la
  fixture qui montre la casse.
- Le comportement du player se teste sur les ViewModels, pas sur ExoPlayer.

## Conventions

- Commits conventionnels (`feat:`, `fix:`, `docs:`), PR courtes et ciblées sur `main`.
- minSdk 26, appareil de référence : Bbox Android TV 11 — tester via `adb` Wi-Fi avant de pousser.
- Build : `./gradlew assembleDebug` · Tests : `./gradlew testDebugUnitTest`
- Aucun secret dans le repo (V1 : aucun compte ; V2 : credentials M6 au Keystore Android, jamais en
  dur, jamais en log).
- Les docs produit (`PRD.md`, `ROADMAP.md`) et les ADR ne se modifient que sur décision explicite
  d'Arnaud via Hermes.
