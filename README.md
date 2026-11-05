# Replaybox

Agrégateur de replay français (TF1+, France TV, M6+ ensuite) pour Android TV, **sans publicités**.

Le principe : les diffuseurs insèrent les pubs **côté player** (CSAI), pas dans les flux VOD — vérifié
par sonde en novembre 2026 (manifests statiques, durées éditoriales, zéro marqueur pub). Une app qui
joue les flux directs est donc sans pub par architecture, sans blocage ni skip.

## Statut

M0 « Ça marche » en cours : squelette Compose for TV + provider TF1 (catalogue complet sur
fixtures, Flux via la chaîne wat de yt-dlp). Voir ROADMAP.md pour les jalons.

## Docs

- [CONTEXT.md](./CONTEXT.md) — glossaire du projet
- [docs/adr/](./docs/adr/) — décisions d'architecture
- [SPEC.md](./SPEC.md) — vérité sur les API · [PRD.md](./PRD.md) · [ROADMAP.md](./ROADMAP.md)

## Résultats des sondes API (nov. 2026)

| Chaîne | Accès | Compte | Pubs dans le flux |
|---|---|---|---|
| TF1+ | graphql web + wat.tv (via yt-dlp), sans DRM | **requis depuis ~janv. 2026** (mesuré le 05/11/2026 ; sous-ensemble encore jouable en anonyme) | non — vérifié par scan de frames |
| France TV | k7.ftven.fr + hdfauth.ftven.fr, sans DRM | non | non — durées = diffusion |
| M6+ | middleware android, DRM Widevine (drmtoday) | oui (gratuit) | non vérifié au pixel — durées + timeline propres |

Sondes reproductibles : voir l'historique de session du NAS (`/opt/data/cache/scratch/`, purgé à 24 h).
