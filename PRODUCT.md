# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

Android TV uniquement (leanback, interface 10-foot). Jetpack Compose avec `androidx.tv:tv-foundation`
et `androidx.tv:tv-material` (1.0.0-alpha07), au-dessus de `compose.material3`. Appareil de
référence : Bbox Android TV 11, minSdk 26. L'entrée se fait exclusivement à la télécommande D-pad :
pas de tactile, pas de pointeur, pas de clavier physique supposé.

## Users

- **Arnaud** : usage quotidien le soir sur la Bbox. Il cherche, lance et reprend des Épisodes.
- **Sa compagne** : regarde avec lui et active **toujours** les sous-titres. La lisibilité des
  sous-titres est le besoin d'accessibilité qui compte le plus dans ce foyer.

L'audience se limite à ce foyer de deux personnes. Il n'y a pas d'autre utilisateur visé.

## Product Purpose

Replaybox réunit le replay français (TF1+ et France TV, puis M6+) dans une seule app Android TV. Elle
joue les Flux directs des chaînes et n'affiche donc aucune publicité. Les apps officielles sont
lentes, chargées, et leur player injecte des pubs (CSAI). Les Flux VOD, eux, n'en contiennent pas.

Succès (Definition of Done du PRD) : un soir de semaine, Arnaud regarde son Programme TF1 ou France
TV sur la Bbox, en reprise de lecture, sous-titres déjà en place, sans croiser une seule pub et sans
ouvrir d'app officielle.

## Positioning

Zéro pub par architecture, pas par blocage : l'app joue directement les Flux VOD propres. Elle est
local-first, sans backend ni tracking (ADR 0001). Son catalogue est cache-first : il reste
affiché, daté, quand l'API d'une chaîne tombe (ADR 0002). Recherche, Genres et reprise
fonctionnent sur toutes les Chaînes à la fois.

## Operating Context

- Salon, le soir, lumière tamisée, canapé à environ 3 m d'une TV 55" (dalle 1080p).
- Visionnage partagé : deux personnes lisent le même écran depuis le canapé.
- Session type : ouvrir l'app, reprendre ou trouver un Programme, choisir un Épisode, regarder.
  L'interface s'interpose entre les spectateurs et la vidéo ; elle doit leur prendre le moins de
  temps possible.
- Les mises à jour s'installent seules depuis les GitHub Releases, sans câble ni store.

## Capabilities and Constraints

- Livré (M0–M2) : Providers TF1 et France TV ; parcours Chaîne → Programme → Saison → Épisode ;
  lecture Media3 ; recherche cross-Chaînes ; favoris (Épingler) ; reprise (Progression) ; Genres
  canoniques ; sous-titres FR activés par défaut avec bascule en un appui ; cache Room avec TTL de
  12 h et mode dégradé signalé par une bannière datée ; auto-update GitHub ; connexion optionnelle
  à un compte TF1+, credentials chiffrés sur le boîtier.
- Le vocabulaire est celui du glossaire `CONTEXT.md` : Chaîne, Programme, Saison, Épisode, Flux,
  Progression, Genre. Les chaînes UI sont en français, dans `values/strings.xml`.
- Hors périmètre V1 (PRD) : M6+, direct, extraits et bonus, profils enfants, comptes ou profils
  utilisateur, mobile, Chromecast, tout backend hébergé.
- Limites des données : le visuel d'un Épisode est `thumbnailUrl` (nullable). Les dates
  d'expiration, les pistes audio et les qualités dépendent de ce que chaque Provider expose. Il
  faut le vérifier dans `SPEC.md` avant que l'UI le promette.
- Tutoiement partout (décision d'Arnaud, 2026-11-06). Les chaînes actuelles au vouvoiement
  (« Saisissez au moins 3 caractères », « bloqué dans votre zone ») sont à reprendre.
- Prochain jalon : M4 « Design », qui reprend les features d'interface anticipées par les
  maquettes Stitch (voir `ROADMAP.md`).

## Brand Commitments

- Nom : **Replaybox**. Logo (2026-11-07) : une flèche de reprise ambre autour d'un triangle de
  lecture, et le mot en Manrope ExtraBold (voir `DESIGN.md`, « Logo and launcher banner »).
- Voix : **discrète**. L'absence de pub se constate, l'écran ne la revendique pas : aucun badge
  « Zéro pub », aucun jargon technique (Media3, DASH, FTS4, latence, « D-pad actif »). Le contenu
  prend la place. Les textes sont courts, en français, et utilisent les termes du glossaire.
- L'identité des chaînes (nom, couleur, logo) sert uniquement à indiquer d'où vient un contenu.
- Style visuel : le **standard du streaming**, exécuté sans faute (choix d'Arnaud, 2026-11-06).
  Références de finition : **Netflix** (grammaire de rails unique, focus rapide, Épisode suivant
  automatique) et l'**app Apple TV** (« À suivre » en premier, grandes cartes lisibles, typographie
  soignée). Défauts du secteur à refuser : bandes-annonces lancées seules, billboard en rotation,
  rails réordonnés par algorithme, badges en pile, et le modèle Prime Video (contenus et
  promotions mélangés, Back imprévisible).

## Evidence on Hand

- Maquettes Stitch (Google), premier jet, base de travail non contraignante : accueil, fiche
  Programme, lecteur, recherche, et une note de design system générée par Stitch. Elles ne sont
  pas versionnées dans le repo. Tous leurs textes, synopsis et métriques sont des placeholders.
- Les visuels et métadonnées réels viennent des Providers à l'exécution. Il n'existe ni
  témoignage, ni presse, ni donnée d'usage, et il ne faut jamais en inventer.

## Product Principles

1. **La vidéo est le produit.** Chaque écran est un chemin court vers la lecture. La reprise est
   toujours à un appui.
2. **Lisible depuis le canapé.** Chaque texte et chaque état de focus se lit à 3 m dans un salon
   sombre, pour deux spectateurs à la fois.
3. **Le focus est l'interface.** Au D-pad, on doit toujours voir où l'on est et où l'on peut
   aller. Un focus perdu est un bug.
4. **Honnête sur l'état des données.** Un catalogue périmé, en échec ou vide s'affiche tel quel,
   avec sa date, jamais caché derrière un spinner.
5. **Confiance tranquille.** Pas de pub, pas d'autopromotion, pas de fanfaronnade technique, pas
   de gamification.

## Accessibility & Inclusion

- **Référentiel :** WCAG 2.2 AA partout, plus le contraste AAA (critère 1.4.6, 7:1) pour tout
  texte courant et toute métadonnée ; 4,5:1 minimum pour le grand texte (≥ 24 sp gras). Les
  éléments non textuels (focus, barres de progression, icônes) visent ≥ 3:1 face aux couleurs
  adjacentes. La conformité AAA complète n'est pas l'objectif.
- **Dimensionnement 10-foot (55" à 3 m) :** corps de texte ≥ 18 sp, métadonnées secondaires
  ≥ 16 sp, rien sous 14 sp, le tout dans la zone sûre d'overscan.
- **Sous-titres :** FR activés par défaut, bascule en un appui. Taille, fond et contraste lisibles
  depuis le canapé ; un réglage de lisibilité fait partie du chantier sous-titres.
- **Focus :** tout élément focusable a un état de focus visible, contrasté à ≥ 3:1, qui ne repose
  pas sur la seule couleur (agrandissement, contour). Chaque écran définit son focus initial, et
  le Back système fonctionne toujours.
- **Indépendance de la couleur :** progression, état vu ou nouveau et sélection ne se codent
  jamais par la couleur seule.
- Respecter le réglage système « Supprimer les animations ».
