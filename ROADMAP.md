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

- [x] **Spike DRM M6+** sur la box, avec le compte d'Arnaud, plateforme `m6group_web` : un Épisode M6
  lu de bout en bout (Gigya → JWT → upfront-token → licence drmtoday) et niveau Widevine confirmé.
  Lecture plafonnée à 720p ou moins : on continue. Aucune licence ou connexion bloquée : M6+ repart
  en V2 (mesures consignées au SPEC) et M3 se réduit à l'Index TF1 et aux Paramètres par Provider.
- [x] **Index générique + TF1** : un seul indexeur Room + FTS (page de catalogue et mapping de Genres
  fournis par chaque Provider), refresh en arrière-plan par pages de 500. Périmètre TF1 : les 5
  Chaînes du groupe (TF1, TMC, TFX, TF1 Séries Films, LCI), Programmes sans replay exclus,
  partenaires exclus. `shows()` TF1 lit l'Index (rangées d'accueil courtes, ordre éditorial TF1).
  Genres fins par SGT_, « Policier & Thriller » par mots-clés sur titre et description (fixture de
  vrais positifs et de pièges). Recherche TF1 locale (préfixe du titre, puis contient, puis replays
  récents), `searchPrograms` en secours tant que l'Index est vide.
- [x] **Paramètres par Provider** : une page par Provider avec son compte quand il en faut un
  (credentials génériques au Keystore Android) et sa Santé. Santé = vérification CI de la nuit
  (`healthcheck.json` sur la branche `status`, lu via `raw.githubusercontent.com`, « inconnue »
  au-delà de 48 h) + état observé sur la box ; le badge prend la pire des deux. Badge dans les
  Paramètres uniquement.
- [x] **Provider M6+ complet** : M6, W9, 6ter, Gulli et originaux M6+ (Paris Première, Téva,
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

Décisions du grilling du 2026-11-07 :

- **Fondations d'abord.** Le design system est fait quand `DESIGN.md` n'a plus de section « Not yet
  specified », que tous les écrans (connexions TF1+ et M6+ comprises) lisent les tokens du thème et
  que l'UI tutoie partout. Le focus se prouve par une checklist par écran dans `DESIGN.md` (focus
  initial, cible du Back, focus au retour) vérifiée sur la box, sans tests instrumentés.
- **Sous-titres** : réglage global de l'app, prioritaire sur le style système. Taille (Normal, Grand,
  Très grand) et fond (Contour seul, Bandeau semi-opaque, Bandeau opaque), Grand + semi-opaque par
  défaut. Réglable dans Paramètres > Sous-titres (aperçu en direct) et dans les Options du lecteur.
- **Paramètres** : liste puis page, dans l'ordre Sous-titres, TF1+, France TV, M6+, À propos
  (version, mise à jour, logo).
- **Genres** : entrée du rail de navigation vers une grille 3 × 3 de tuiles texte ; chaque tuile
  ouvre la grille « Tout voir » (6 affiches par ligne, récence puis titre, chips de groupe de
  Chaîne). Carte « Tout voir » au bout des rails d'accueil tronqués seulement. France TV y figure
  avec son cache, sans Index dédié. Jeunesse devient exclusif (aucun autre Genre), son rail reste à
  l'accueil. Policier & Thriller TF1 : SGT natifs seuls pour les films ; pour les séries, un
  mot-clé dans le titre ou deux dans le synopsis.
- **Badges de fraîcheur** : un seul par Épisode, par priorité « Dernier jour » / « Plus que N
  jours » (≤ 7 j), « Hier soir » (veille, à partir de 18 h), « Nouveau » (< 48 h). « Inédit » est
  abandonné tant qu'aucun Provider ne l'expose. Pastille texte sans ambre. Un badge n'existe pour
  un Provider qu'après mesure au `SPEC.md`, sans requête supplémentaire par Épisode. Sur une carte
  « À suivre » en cours, seule l'expiration s'affiche ; un nouvel Épisode porte son badge. Un Épisode expiré sort d'« À suivre » et des listes.
- **« À suivre »** remplace Reprendre : Épisodes en cours et prochain Épisode non vu de chaque
  Programme épinglé, par événement le plus récent. Les épinglés se rafraîchissent en arrière-plan
  au-delà de 12 h. La fiche Programme propose Reprendre, sinon le prochain non vu, sinon le premier
  Épisode. Épisode suivant s'affiche à 10 s de la fin et passe d'une Saison à la suivante.
- **Recherche** : clavier alphabétique intégré à gauche, résultats à droite (requête, chips Chaîne
  et Genre, meilleur résultat sur titre exact ou préfixe, grille de 4). Recherche dès 1 caractère,
  filtres seuls possibles, état complet restauré au retour.
- **Logo** : bannière launcher et icône adaptative, choisies sur trois pistes ; dans l'app, le logo
  n'apparaît que sur « À propos ».
- Release intermédiaire après les sous-titres, release finale en fin de jalon. Les cases se cochent
  après vérification sur la box ; le critère se valide par Arnaud, sur le canapé.

Fondations :

- [x] Design system Compose for TV (couleurs, typo, formes, focus) documenté dans `DESIGN.md`
- [x] États de focus D-pad lisibles à 3 m sur tous les écrans, focus initial défini partout
- [x] Lisibilité des sous-titres réglable (taille, fond)
- [x] Logo et bannière launcher

Accueil & navigation :

- [x] Rail de navigation latéral (Accueil, Genres, Recherche, Paramètres)
- [x] Billboard « à la une » avec action Reprendre
- [x] Rail « À suivre » en tête d'accueil (Épisodes en cours et nouveaux Épisodes des Programmes épinglés)
- [x] Fond dynamique tiré du visuel focalisé
- [x] Grille « Tout voir » par Genre

Catalogue (dettes M3) :

- [x] Genres TF1 corrigés (Policier & Thriller sans faux positifs connus)
- [x] Jeunesse exclusif (plus de programmes enfants dans Séries)
- [x] Images M6 mesurées sur la box (rail affiché en moins de 1 s, cache chaud)

Épisodes enrichis :

- [x] Badges de fraîcheur (Hier soir, Nouveau, J-n avant expiration)
- [x] État de Progression sur chaque Épisode (non commencé, % vu, vu)
- [x] « Marquer comme vu »
- [x] Prochain Épisode non vu proposé sur la fiche Programme

Lecteur complet :

- [x] −10 s / +10 s, recommencer
- [x] Épisode suivant, avec enchaînement automatique
- [ ] Épisode suivant annoncé avant la fin, d'une Saison à la suivante
- [x] Choix de la piste audio et de la qualité
- [x] Vitesse de lecture

Recherche guidée :

- [x] Clavier à l'écran pensé pour le D-pad
- [x] Filtres rapides par Chaîne et par Genre
- [x] Meilleur résultat mis en avant

**Critère** : une soirée complète sur la Bbox (accueil → reprise → Épisode suivant → recherche)
sans focus perdu, et sous-titres lus confortablement depuis le canapé.

## V2 (décisions prises, exécution repoussée)

- Live TV (réévaluer la promesse zéro pub : le live des chaînes est SSAI)
- Profils enfants, Fire TV, mobile
