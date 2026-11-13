# Synchro facultative via le Drive de l'utilisateur, toujours aucun serveur à nous

Replaybox peut synchroniser les Profils, la Progression, les épingles, les retraits de « À suivre » et
les réglages de sous-titres et de piste audio entre les appareils d'un foyer. Les données sont écrites
dans le dossier `appDataFolder` du Google Drive de l'utilisateur, avec le scope `drive.appdata` (non
sensible, autorisé dans le flux OAuth des appareils à saisie limitée). Aucun serveur ne nous appartient
et aucune donnée ne transite par nous. La synchro est désactivée par défaut et s'active dans
Paramètres > Synchronisation.

Pourquoi : continuité box ↔ téléphone ↔ autre TV, et sauvegarde des données, sans coût d'exploitation
ni responsabilité d'hébergement. L'APK est publique : chaque utilisateur garde ses données dans son
propre compte. Firebase et un backend à nous ont été écartés, car ils nous rendraient hébergeurs de
l'historique de visionnage de tiers. Une synchro directe en réseau local a été écartée aussi : la box en
veille ne synchronise pas.

Décision d'Arnaud du 2026-11-13 (grilling de l'issue #70).

## Conséquences

- L'ADR 0001 reste valable : aucun serveur à nous. Google voit l'historique de l'utilisateur qui active
  la synchro.
- La conséquence « jamais synchronisés » de l'ADR 0002 est remplacée : Progression et épingles sont
  locales, et synchronisées seulement si l'utilisateur l'active.
- Les identifiants des Providers ne quittent jamais l'appareil, ni par la synchro ni par un export.
- La fusion se fait côté client : la dernière modification gagne, avec des traces datées pour les
  suppressions. Le format est versionné et découpé par Profil.
- Le projet Google Cloud qui porte l'identifiant OAuth est publié « En production » (en mode Test, les
  jetons expirent au bout de 7 jours).
