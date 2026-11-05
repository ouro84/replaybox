# Catalogue cache-first avec mode dégradé

Les API des diffuseurs sont lentes (graphql TF1 : 1-2 s par appel) et parfois indisponibles. Décision :
le catalogue (chaînes, programmes, épisodes) est servi depuis un cache local Room avec un TTL de 12 h,
rafraîchi au démarrage de l'app. Si le refresh d'un provider échoue, l'app sert le cache et marque la
chaîne « données du [date] » au lieu d'afficher une erreur — une panne d'API ne doit jamais empêcher
une soirée.

La seule lecture jamais faite en direct est la résolution du flux au moment de jouer (les manifests
sont signés et à durée de vie courte). Le health-check nocturne en CI (pousse un épisode par provider
jusqu'à l'URL du manifest) détecte les casses avant l'usage et déclenche la correction — l'utilisateur
n'est jamais le débogueur.

## Conséquences

- Le catalogue affiché peut dater de 12 h max ; le rafraîchissement est explicite et daté à l'écran.
- La progression de lecture et les favoris sont locaux (Room), jamais synchronisés.
