# Fiche de défense : Series TV

## 1. Présentation en 30 secondes

« Series TV est une application de suivi de séries. Chaque utilisateur a sa propre liste, suit sa progression épisode par épisode et peut importer des séries depuis l'API TMDB. L'accès est protégé par JWT, les mots de passe sont hachés avec BCrypt, et les comptes doivent être approuvés par un administrateur. Le backend est en Spring Boot avec une architecture Controller, Service, Repository ; le frontend est en JavaScript natif. »

## 2. Architecture : le rôle de chaque couche

| Couche | Rôle | Exemple dans le projet |
|---|---|---|
| Controller | Reçoit la requête HTTP, valide le corps (`@Valid`), choisit le code de réponse. Aucune logique métier. | `SerieController`, `AuthController` |
| Service | Règles métier, vérifications avant écriture, transactions. | `VisionnageService` (fin de saison, doublon), `AuthService` |
| Repository | Accès aux données avec Spring Data JPA, sans SQL écrit à la main. | `SerieRepository.findByIdAndUtilisateurId` |
| Entity | Tables de la base, relations. | `Serie`, `Utilisateur` |
| DTO et mappers | Les entités ne sortent jamais de l'API : on ne renvoie que ce qu'on veut exposer (jamais le mot de passe). | `SerieRequest`, `SerieResponse` |

## 3. Entités et relations

- **Utilisateur 1 — N Serie** : `@ManyToOne` côté `Serie` (clé étrangère `utilisateur_id`).
- **Serie 1 — N Saison** et **Saison 1 — N Episode** : `@OneToMany` avec `cascade = ALL` et `orphanRemoval`.
- **Utilisateur N — N Episode** par l'entité **Visionnage** : je n'ai pas utilisé `@ManyToMany` directement parce que la relation porte une donnée (la date de visionnage). Une contrainte d'unicité sur (utilisateur, épisode) empêche de marquer deux fois le même épisode.
- Clés primaires : `@Id` avec `GenerationType.IDENTITY`.
- Contraintes d'intégrité : `pseudo` et `email` uniques, colonnes `nullable = false`.

## 4. Ordre de la démonstration (collection Thunder Client)

1. **Inscription** d'alice : 201, statut `EN_ATTENTE`, pas de token.
2. **Connexion d'alice** : 403 avec le message « en attente d'approbation ».
3. **Connexion de l'admin**, puis **approbation d'alice**, puis connexion d'alice : 200 avec token.
4. **CRUD** d'une série : créer (201), lister, détail, modifier, statut, avis, supprimer (204).
5. **Erreurs** : titre vide (400), série inexistante (404), statut invalide (400), épisode déjà vu (409).
6. **Progression** : marquer un épisode vu, puis la progression (50 %).
7. **Sécurité** : sans token (403), alice sur la liste de l'admin (403), alice sur une route admin (403), l'admin sur la liste d'alice (200).
8. **Suppression** d'une série avec épisodes vus, puis du compte d'alice par l'admin (204).
9. Montrer les données dans MySQL après chaque opération (critère « persistance »).

## 5. Comment fonctionne la sécurité

1. À la connexion, `AuthService` vérifie email et mot de passe (comparaison BCrypt faite par Spring Security), puis le statut du compte, puis génère un JWT signé (email, date d'expiration : 24 h).
2. À chaque requête, `JwtAuthenticationFilter` lit l'en-tête `Authorization: Bearer ...`, vérifie la signature et l'expiration, recharge l'utilisateur **en base**, et refuse s'il n'est plus `APPROUVE`. Le rôle est donc toujours celui de la base, pas celui du moment de la connexion.
3. `SecurityConfig` : sessions désactivées (stateless), `/api/auth/**` public, `/api/admin/**` réservé au rôle `ADMIN`, tout le reste exige une authentification.
4. Pour chaque ressource d'un utilisateur, `@PreAuthorize("@securite.peutAcceder(#userId)")` compare l'utilisateur du token à celui de l'URL (un administrateur passe toujours). Une série ou une saison d'un autre utilisateur donne 403 ou 404.
5. Injection SQL : impossible ici, car toutes les requêtes passent par Spring Data (méthodes dérivées) ou par du JPQL avec paramètres nommés.

## 6. Choix techniques et pourquoi

- **JWT plutôt que sessions** : API REST sans état, facile à tester avec Thunder Client.
- **CSRF désactivé** : le token est envoyé dans un en-tête par le JavaScript, pas dans un cookie envoyé automatiquement par le navigateur.
- **DTO** : séparer ce qui est stocké de ce qui est exposé, et valider les entrées avec Bean Validation.
- **`GlobalExceptionHandler`** : un seul endroit pour transformer chaque exception en réponse JSON cohérente avec le bon code.
- **`ddl-auto=update`** : Hibernate crée les tables à partir des entités (c'est la consigne), pratique ici mais à éviter en production.
- **Codes HTTP** : 400 données invalides, 401 identifiants incorrects, 403 accès refusé, 404 introuvable, 409 conflit (doublon, règle métier).

## 7. Questions probables

- **Différence entre 401 et 403 ?** 401 : on ne sait pas qui vous êtes (mauvais identifiants). 403 : on sait qui vous êtes mais vous n'avez pas le droit.
- **Que fait `mappedBy` ?** Il indique que la relation est portée par l'autre côté (la clé étrangère est dans la table `serie`), pour ne pas créer une seconde table de jointure.
- **Pourquoi `@Transactional` sur les services ?** Une opération métier (par exemple supprimer une série et ses épisodes) est tout ou rien.
- **Pourquoi hacher un mot de passe plutôt que le chiffrer ?** Le hachage est à sens unique : même avec la base, on ne retrouve pas le mot de passe. BCrypt ajoute un sel et est volontairement lent.
- **Que fait `@Valid` ?** Il déclenche les contraintes (`@NotBlank`, `@Size`, `@Email`...) du DTO ; en cas d'échec, `MethodArgumentNotValidException` devient une réponse 400 avec le détail des champs.
- **Que se passe-t-il si un token est volé ?** Il reste valide jusqu'à son expiration. Mitigation partielle : le statut et le rôle sont revérifiés en base à chaque requête, donc un compte refusé ou supprimé perd l'accès immédiatement.
- **Pourquoi `Visionnage` est une entité ?** Parce que la relation porte une donnée (la date) et une contrainte d'unicité.

## 8. Limites à connaître (mieux vaut les dire avant qu'on vous les demande)

- Pas de tests automatisés : la vérification se fait par la collection Thunder Client.
- Le token est stocké dans le `localStorage` du navigateur : pratique, mais exposé en cas de faille XSS (un cookie `HttpOnly` serait plus sûr).
- Un token ne peut pas être révoqué avant son expiration (24 h).
- Le secret JWT et le compte administrateur ont une valeur par défaut, uniquement pour que le projet démarre tel quel : en conditions réelles, ils se définissent dans `.env`.
- La liste personnelle de séries n'est pas paginée côté serveur (le tri et les filtres se font dans le navigateur).
