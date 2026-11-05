# Replaybox

Glossaire du projet : une app Android TV qui agrège le replay français (TF1+, France TV, puis M6+)
et le lit sans publicités. Le code est en anglais, l'interface utilisateur en français.

## Contenu

**Chaîne** (code: `Channel`):
La source de contenu — TF1, France 2, M6. Un provider par chaîne.
_Éviter_: source, plateforme, fournisseur

**Programme** (code: `Show`):
Une émission ou une série (« Koh-Lanta », « César Wagner »). Le regroupement éditorial de la chaîne.
_Évider_: série (trop étroit — un magazine est un programme), émission, titre

**Saison** (code: `Season`):
Regroupement d'épisodes d'un programme selon la numérotation de la chaîne. Absent quand la chaîne ne
numérote pas (magazines, docs).
_Éviter_: vol., partie

**Épisode** (code: `Episode`):
Une vidéo replay pleine d'un programme, datée de diffusion. Les extraits et bonus des chaînes
(TF1 `EXTRACT`/`BONUS`) sont hors périmètre V1.
_Éviter_: vidéo, contenu, clip

**Flux** (code: `Stream`):
L'URL du manifest (DASH/HLS) jouable d'un épisode à un instant t — signée, à durée de vie limitée.
_Éviter_: lien, URL vidéo

## Usage

**Progression** (code: `Progress`):
Position de lecture d'un épisode, stockée localement sur le boîtier. Ne quitte jamais l'appareil.
_Éviter_: historique, watchlist

**Genre** (code: `Genre`):
Catégorie canonique replaybox (Séries, Films, Policier & Thriller, Comédie, Drame, Documentaire,
Jeunesse, Divertissement, Sport & Info), mappée sur les taxonomies propres de chaque chaîne.
_Éviter_: catégorie (terme des chaînes), tag (champ TF1 non fiable)

## Architecture

**Provider** (code: `Provider`):
Module qui encapsule l'API d'une chaîne : listing catalogue, recherche, résolution de flux. Une
interface, une implémentation par chaîne.
_Éviter_: résolver, backend, connector
