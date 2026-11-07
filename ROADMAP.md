# ROADMAP — Replaybox

Trois jalons, chacun livré en APK installable et testable sur la Bbox. Un jalon n'est pas « fait »
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

- Provider M6+ : compte Gigya + licence Widevine drmtoday, credentials au Keystore Android
- Live TV (réévaluer la promesse zéro pub : le live des chaînes est SSAI)
- Index TF1 local (~2000 programmes) pour genres fins + recherche
- Profils enfants, Fire TV, mobile
