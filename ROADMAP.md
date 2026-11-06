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

- [ ] Auto-update depuis les GitHub Releases
- [ ] Health-check nightly en CI + alerte d'échec
- [ ] Polish UX (états d'erreur, spinner de résolution de Flux, écrans vides)
- [x] Releases taggées

**Critère** : Definition of Done du PRD vérifiée en conditions réelles.

## V2 (décisions prises, exécution repoussée)

- Provider M6+ : compte Gigya + licence Widevine drmtoday, credentials au Keystore Android
- Live TV (réévaluer la promesse zéro pub : le live des chaînes est SSAI)
- Index TF1 local (~2000 programmes) pour genres fins + recherche
- Profils enfants, Fire TV, mobile
