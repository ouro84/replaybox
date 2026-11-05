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
2. En tant que spectateur, je veux que les sous-titres français soient activés par défaut, pour ne jamais y penser.
3. En tant que spectateur, je veux reprendre un épisode là où je l'ai laissé, pour enchaîner les soirs.
4. En tant que spectateur, je veux une recherche cross-chaînes, pour ne pas savoir dans quelle app est le programme.
5. En tant que spectateur, je veux filtrer par Genre (Séries, Films, Policier & Thriller, Comédie, Drame, Documentaire, Jeunesse, Divertissement, Sport & Info), pour explorer sans connaître les titres.
6. En tant que spectateur, je veux un catalogue qui s'affiche instantanément, même si une API de diffuseur est cassée (mode dégradé daté), pour que ma soirée ne dépende pas de TF1.
7. En tant que spectateur, je veux épingler des favoris, pour éviter de re-naviguer.
8. En tant que propriétaire du boîtier, je veux que l'app se mette à jour toute seule depuis les Releases GitHub, pour ne jamais brancher de câble.

## Hors périmètre V1

M6+ (V2, DRM+compte), live TV, extraits/bonus des chaînes, profils enfants, mobile/tablette,
Chromecast, tout backend hébergé, tout compte utilisateur.

## Definition of Done (V1)

Un soir, Arnaud regarde son programme TF1 ou France TV **depuis la Bbox**, en reprise de lecture,
sous-titres déjà en place, **sans croiser une seule pub et sans jamais ouvrir une app officielle**.
