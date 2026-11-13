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
Position de lecture d'un épisode pour un Profil, stockée localement sur l'appareil. Ne le quitte
que si la Synchro est activée (ADR 0003).
_Éviter_: historique, watchlist

**Profil** (code: `Profile`):
Une personne ou un usage du foyer (« Foyer », pour regarder à deux). Porte sa Progression, ses
épingles, ses retraits de « À suivre » et ses réglages de sous-titres et de piste audio. Les
identifiants des diffuseurs et la qualité max restent communs à l'appareil.
_Éviter_: utilisateur, compte (le compte est celui du diffuseur ou de Google)

**Synchro** (code: `Sync`):
Copie facultative des Profils et de leurs données dans le dossier `appDataFolder` du Google Drive de
l'utilisateur, pour les retrouver sur ses autres appareils. La dernière modification gagne.
_Éviter_: backup, cloud, compte Replaybox

**Téléchargement** (code: `Download`):
Un Épisode stocké sur un téléphone ou une tablette pour être lu sans réseau, ou avec seulement une
licence demandée au lancement, selon ce que le Provider autorise. Appartient à l'appareil.
_Éviter_: hors-ligne (désigne aussi le cache catalogue), cache

**Genre** (code: `Genre`):
Catégorie canonique replaybox (Séries, Films, Policier & Thriller, Comédie, Drame, Documentaire,
Jeunesse, Divertissement, Sport & Info), mappée sur les taxonomies propres de chaque chaîne.
_Éviter_: catégorie (terme des chaînes), tag (champ TF1 non fiable)

## Architecture

**Provider** (code: `Provider`):
Module qui encapsule l'API d'une chaîne : listing catalogue, recherche, résolution de flux. Une
interface, une implémentation par chaîne.
_Éviter_: résolver, backend, connector

**Index** (code: `CatalogueIndex`):
Copie locale complète des Programmes d'un Provider, rafraîchie toutes les 12 h, qui alimente le
catalogue, les Genres et la recherche.
_Éviter_: base, crawl

**Santé** (code: `Health`):
État d'un Provider : vérification CI de la nuit combinée à ce qui a été observé sur le boîtier.
_Éviter_: statut, monitoring
