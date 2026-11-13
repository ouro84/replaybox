# PRD — Replaybox

## Problème

Le replay français est éclaté entre des apps officielles lentes, envahies de bloatware et semées de
coupures publicitaires. Le contenu lui-même est propre : les pubs sont injectées **par le player** des
apps officielles (CSAI), jamais cousues dans les flux VOD — vérifié par sondes en novembre 2026. Une
app qui joue les flux directs n'a donc pas à choisir entre confort et pubs.

## Solution

Replaybox : une application Android TV unique qui agrège le replay de TF1+, France TV (puis M6+),
avec une interface rapide et sans publicité — par architecture, pas par blocage.

## Utilisateurs

- **Arnaud** : usage quotidien le soir sur sa Bbox Android TV 11 ; cherche, lance, reprend.
- **Sa compagne** : regarde avec lui ; active **toujours** les sous-titres → sous-titres FR **activés
  par défaut**, désactivables en un appui.

## Histoires utilisateurs (V1)

1. En tant que spectateur, je veux parcourir les programmes d'une chaîne par Programme → Saison → Épisode, pour trouver ce que je veux sans friction.
2. En tant que spectateur, je veux que les sous-titres français soient activés par défaut, puis que l'app retienne mon dernier choix (activés ou non, et leur type), pour ne jamais y penser.
3. En tant que spectateur, je veux reprendre un épisode là où je l'ai laissé, pour enchaîner les soirs.
4. En tant que spectateur, je veux une recherche cross-chaînes, pour ne pas savoir dans quelle app est le programme.
5. En tant que spectateur, je veux filtrer par Genre (Séries, Films, Policier & Thriller, Comédie, Drame, Documentaire, Jeunesse, Divertissement, Sport & Info), pour explorer sans connaître les titres.
6. En tant que spectateur, je veux un catalogue qui s'affiche instantanément, même si une API de diffuseur est cassée (mode dégradé daté), pour que ma soirée ne dépende pas de TF1.
7. En tant que spectateur, je veux épingler des favoris, pour éviter de re-naviguer.
8. En tant que propriétaire du boîtier, je veux que l'app se mette à jour toute seule depuis les Releases GitHub, pour ne jamais brancher de câble.

## Hors périmètre V1

M6+ (V2, DRM+compte), live TV, extraits/bonus des chaînes, profils enfants,
Chromecast, tout backend hébergé par nous.

Chromecast reste hors périmètre (décision du 2026-11-13, issue #61 fermée) : un receiver custom exige
l'inscription payante Google Cast, et le receiver par défaut ne lit pas le Widevine de TF1+ et M6+.
Sur une TV équipée d'un Chromecast avec Google TV, on installe Replaybox directement.

## Après la V1 — mobile et tablette

Décision d'Arnaud du 2026-11-10 : la V1/MVP est livrée. Mobile et tablette sortent du hors-périmètre :
la même APK s'installe sur un téléphone ou une tablette Android 8+ et s'utilise au doigt, en paysage.
Sur la box, rien ne change. Jalon livré en version 1.0.0.

## Après la 1.0 — profils, synchro, lecteur mobile, hors-ligne

Décision d'Arnaud du 2026-11-13 (grilling des issues #59, #60, #61, #62, #70). Le compte utilisateur
entre dans le périmètre sous la forme d'une synchro facultative via le Google Drive de l'utilisateur,
sans serveur à nous (ADR 0003). Les identifiants des diffuseurs ne quittent jamais l'appareil. Aucune
fonction ne contourne la politique de licence d'un diffuseur : une fonction qu'un Provider refuse est
absente pour ses Épisodes.

9. En tant que membre du foyer, je veux mon propre Profil (Progression, épingles, sous-titres), pour
   que ce que je regarde seul ne se mélange pas à ce que l'on regarde à deux.
10. En tant que spectateur, je veux retrouver mes épingles, ma Progression et mes réglages sur la box,
    sur mon téléphone et sur une autre TV équipée de Replaybox, pour reprendre où que je sois.
11. En tant que spectateur sur téléphone, je veux que la vidéo continue en picture-in-picture quand je
    quitte l'app, et que le son continue quand l'écran s'éteint.
12. En tant que spectateur sur téléphone, je veux télécharger un Épisode pour le regarder dans le
    train, et qu'il disparaisse une fois vu.

Releases visées : 1.1.0 (Profils et synchro), 1.2.0 (lecteur mobile), 1.3.0 (hors-ligne). Le détail
est dans `ROADMAP.md`.

## Definition of Done (V1)

Un soir, Arnaud regarde son programme TF1 ou France TV **depuis la Bbox**, en reprise de lecture,
sous-titres déjà en place, **sans croiser une seule pub et sans jamais ouvrir une app officielle**.
