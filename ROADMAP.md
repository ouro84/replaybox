# ROADMAP — Replaybox

Les jalons, chacun livré en APK installable et testable sur la Bbox. Un jalon n'est pas « fait »
tant que son critère d'acceptation n'est pas observé sur le vrai boîtier.

## M0 — « Ça marche »

Squelette Compose for TV booté sur la Bbox, interface `Provider`, **provider TF1 complet** :
catalogue → Programme → Saison → Épisode → Flux → lecture Media3.

- [x] APK sideloadé et lancé sur la Bbox
- [x] Parcours TF1 complet joué ce soir-là, sans pub
- [x] Fixtures de parsing TF1 en place

**Critère** : Arnaud a regardé un programme TF1 depuis Replaybox.

## M1 — « Utile tous les soirs »

- [x] Provider France TV complet
- [x] Cache Room + TTL 12 h + mode dégradé (bannière datée)
- [x] Recherche cross-chaînes (debounce, fusion titre normalisé)
- [x] Favoris + reprise de lecture (Progress)
- [x] Genres canoniques (mapping TF1 GT/SGT + FTV sub_categories)
- [x] **Sous-titres FR activés par défaut**, toggle un appui

**Critère** : une semaine d'usage réel sans ouvrir une app officielle.

## M2 — « Produit »

- [x] Auto-update depuis les GitHub Releases
- [x] Health-check nightly en CI + alerte d'échec
- [x] Polish UX (états d'erreur, spinner de résolution de Flux, écrans vides)
- [x] Releases taggées

**Critère** : Definition of Done du PRD vérifiée en conditions réelles.

## M3 — « Plus de catalogue »

Provider M6+ (sorti de V2) et Index local du catalogue, partagé par TF1 et M6+. Décisions du grilling
du 2026-11-07. L'Index suit l'ADR 0002 : TTL 12 h, ancien Index servi si le refresh échoue. La
nouvelle UI d'exploration (grille « Tout voir », filtres) reste en M4 et s'appuiera sur l'Index.

- [ ] **Spike DRM M6+** sur la box, avec le compte d'Arnaud, plateforme `m6group_web` : un Épisode M6
  lu de bout en bout (Gigya → JWT → upfront-token → licence drmtoday) et niveau Widevine confirmé.
  Lecture plafonnée à 720p ou moins : on continue. Aucune licence ou connexion bloquée : M6+ repart
  en V2 (mesures consignées au SPEC) et M3 se réduit à l'Index TF1 et aux Paramètres par Provider.
- [ ] **Index générique + TF1** : un seul indexeur Room + FTS (page de catalogue et mapping de Genres
  fournis par chaque Provider), refresh en arrière-plan par pages de 500. Périmètre TF1 : les 5
  Chaînes du groupe (TF1, TMC, TFX, TF1 Séries Films, LCI), Programmes sans replay exclus,
  partenaires exclus. `shows()` TF1 lit l'Index (rangées d'accueil courtes, ordre éditorial TF1).
  Genres fins par SGT_, « Policier & Thriller » par mots-clés sur titre et description (fixture de
  vrais positifs et de pièges). Recherche TF1 locale (préfixe du titre, puis contient, puis replays
  récents), `searchPrograms` en secours tant que l'Index est vide.
- [ ] **Paramètres par Provider** : une page par Provider avec son compte quand il en faut un
  (credentials génériques au Keystore Android) et sa Santé. Santé = vérification CI de la nuit
  (`healthcheck.json` sur la branche `status`, lu via `raw.githubusercontent.com`, « inconnue »
  au-delà de 48 h) + état observé sur la box ; le badge prend la pire des deux. Badge dans les
  Paramètres uniquement.
- [ ] **Provider M6+ complet** : M6, W9, 6ter, Gulli et originaux M6+ (Paris Première, Téva,
  M6+ Max et podcasts exclus), branché sur l'Index, recherche locale uniquement, intégrales (`vi`)
  seulement, lecture Widevine drmtoday, sonde anonyme dans le health-check nightly.

**Critère** : un soir, depuis la Bbox, Arnaud lit un Épisode M6 ou W9 avec son compte, sous-titres en
place et sans pub ; il trouve par la recherche un Programme TF1 absent des 40 d'avant ; le Genre
« Policier & Thriller » liste des séries TF1 et M6 ; les Paramètres affichent la Santé des trois
Providers.

## M4 — « Design »

Refonte visuelle à partir des maquettes Stitch, cadrée par `PRODUCT.md` (TV 55" à 3 m, WCAG AA avec
contraste texte AAA, voix discrète). Toute donnée affichée (expiration, pistes audio, qualités) doit
d'abord exister dans le `SPEC.md` du Provider concerné.

Fondations :

- [ ] Design system Compose for TV (couleurs, typo, formes, focus) documenté dans `DESIGN.md`
- [ ] États de focus D-pad lisibles à 3 m sur tous les écrans, focus initial défini partout
- [ ] Lisibilité des sous-titres réglable (taille, fond)

Accueil & navigation :

- [ ] Rail de navigation latéral (Accueil, Genres, Recherche, Paramètres)
- [x] Billboard « à la une » avec action Reprendre
- [x] Rail « Reprendre la lecture » en tête d'accueil
- [x] Fond dynamique tiré du visuel focalisé
- [ ] Grille « Tout voir » par Genre

Épisodes enrichis :

- [ ] Badges de fraîcheur (Hier soir, Nouveau, Inédit, J-n avant expiration)
- [x] État de Progression sur chaque Épisode (non commencé, % vu, vu)
- [ ] « Marquer comme vu »

Lecteur complet :

- [x] −10 s / +10 s, recommencer
- [x] Épisode suivant, avec enchaînement automatique
- [x] Choix de la piste audio et de la qualité
- [x] Vitesse de lecture

Recherche guidée :

- [ ] Clavier à l'écran pensé pour le D-pad
- [ ] Filtres rapides par Chaîne et par Genre
- [ ] Meilleur résultat mis en avant

**Critère** : une soirée complète sur la Bbox (accueil → reprise → Épisode suivant → recherche)
sans focus perdu, et sous-titres lus confortablement depuis le canapé.

## V2 (décisions prises, exécution repoussée)

- Live TV (réévaluer la promesse zéro pub : le live des chaînes est SSAI)
- Profils enfants, Fire TV, mobile
