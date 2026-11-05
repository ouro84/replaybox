# Local-first : l'app parle aux API des diffuseurs, zéro backend

Replaybox est une application Android TV autonome : chaque chaîne est un `Provider` embarqué qui parle
directement aux API publiques du diffuseur (graphql TF1, k7/hdfauth France TV, middleware M6+). Aucun
serveur à nous : pas de NAS dans la boucle de lecture, pas de proxy, pas de relais.

Pourquoi : zéro coût d'exploitation, zéro point de défaillance chez nous, et même modèle éprouvé que
Catch-up TV & More. La contrepartie assumée est que quand un diffuseur casse son API, l'app casse avec
— surveillé par un health-check nocturne en CI, corrigé par une release (l'app se met à jour toute
seule).

## Conséquences

- Les flux joués vont du boîtier directement aux CDN des diffuseurs.
- Les credentials éventuels (M6+ en V2) restent sur le boîtier, chiffrés au Keystore Android.
- La santé des providers est une dépendance critique : voir ADR 0002 pour le mode dégradé.
