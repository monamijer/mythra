# Series TV

Application web de suivi de séries télévisées : une API REST Spring Boot (Java 21, MySQL) et une interface en HTML, CSS et JavaScript natif, servie par l'application elle-même. Projet de Java avancé avec Spring Boot.

Chaque utilisateur gère sa propre liste de séries, suit sa progression épisode par épisode et peut l'enrichir avec l'API TMDB. Les comptes sont soumis à l'approbation d'un administrateur.

## Démarrage

**Prérequis** : Java 21, MySQL ou MariaDB (XAMPP convient), et une clé d'API TMDB v3 pour les fonctions d'enrichissement.

1. Démarrer MySQL (XAMPP : module MySQL).
2. Copier `.env.example` en `.env` et renseigner les valeurs (mot de passe de la base si elle en a un, clé TMDB). Sans `.env`, l'application démarre avec la configuration XAMPP par défaut (utilisateur `root`, mot de passe vide) et un secret JWT de développement. Pour un vrai usage, générer son propre secret (Base64) avec `openssl rand -base64 48`.
3. Lancer l'application :

   ```bash
   ./mvnw spring-boot:run
   ```

4. Ouvrir <http://localhost:8080>.

La base `series_tv_db` et ses tables sont créées automatiquement : Hibernate génère le schéma à partir des entités.

**Compte administrateur** : au premier démarrage, un administrateur est créé s'il n'en existe aucun. Par défaut : `admin@seriestv.local` / `Admin1234!` (modifiables avec `ADMIN_EMAIL`, `ADMIN_PSEUDO` et `ADMIN_PASSWORD` dans `.env`).

## Rôles et comptes

- Une inscription crée un compte au statut `EN_ATTENTE` : la connexion est refusée, avec un message explicite, tant qu'un administrateur ne l'a pas approuvé.
- `USER` : gère uniquement ses propres séries, visionnages et statistiques.
- `ADMIN` : approuve ou refuse les comptes, promeut ou rétrograde, change un mot de passe, supprime un utilisateur (avec ses données), et accède à toutes les ressources.

## Entités

| Entité | Rôle |
|---|---|
| `Utilisateur` | Compte (pseudo, email, mot de passe haché, rôle, statut, dates d'inscription et de dernière connexion) |
| `Serie` | Série de la liste d'un utilisateur (statut de visionnage, note personnelle, critique) |
| `Saison` | Saison d'une série |
| `Episode` | Épisode d'une saison |
| `Visionnage` | Épisode vu par un utilisateur, avec la date |

Relations : `Utilisateur 1—N Serie`, `Serie 1—N Saison`, `Saison 1—N Episode`, `Utilisateur 1—N Visionnage`, `Episode 1—N Visionnage` (`Visionnage` relie un utilisateur à un épisode, avec une contrainte d'unicité sur le couple).

## Architecture

```
com.monprojet.series
├── controller   routes REST, validation des corps de requête (@Valid)
├── service      logique métier
├── repository   accès aux données (Spring Data JPA)
├── entity       entités JPA
├── dto          objets d'échange (request / response) et mappers
├── security     JWT, filtre d'authentification, contrôle des accès
├── exception    exceptions métier et gestion centralisée des erreurs
└── config       configuration (sécurité, TMDB, administrateur initial)
```

Sécurité : Spring Security avec jetons JWT sans session, mots de passe hachés avec BCrypt, contrôle des accès par `@PreAuthorize` (un utilisateur n'accède qu'à ses ressources, un administrateur à toutes), et vérification du statut du compte à chaque requête.

## Routes principales

| Domaine | Routes |
|---|---|
| Authentification | `POST /api/auth/inscription`, `POST /api/auth/connexion` |
| Séries | `GET, POST /api/utilisateurs/{userId}/series`, `GET, PUT, DELETE .../series/{id}`, `PATCH .../series/{id}/statut`, `PATCH .../series/{id}/avis` |
| Saisons et épisodes | `GET, POST /api/utilisateurs/{userId}/series/{serieId}/saisons`, `GET, POST /api/saisons/{saisonId}/episodes` |
| Visionnage | `POST, DELETE /api/utilisateurs/{userId}/visionnages/{episodeId}`, `GET .../progression/{serieId}`, `GET .../series-en-cours`, `GET .../series-terminees`, `GET .../stats` |
| TMDB | `GET /api/tmdb/recherche`, `populaires`, `tendances`, `mieux-notees`, `diffusees-bientot`, `decouvrir`, `genres`, `serie/{id}` (et `similaires`, `recommandations`, `credits`, `fournisseurs`, `videos`), `recherche-acteur`, `acteur/{id}`, `POST /api/utilisateurs/{userId}/tmdb/importer/{tmdbId}` |
| Administration | `GET /api/admin/utilisateurs`, `GET .../statistiques`, `PATCH .../{id}/approuver`, `refuser`, `promouvoir`, `retrograder`, `mot-de-passe`, `DELETE .../{id}` |

Codes de réponse : 200, 201 et 204 en succès ; 400 (données invalides), 401 (identifiants incorrects), 403 (accès refusé ou compte non approuvé), 404, 409 (règle métier ou doublon), 503 (TMDB indisponible) et 500 en dernier recours. Toutes les erreurs ont la même forme JSON.

## Démonstration avec Thunder Client

Le dossier `docs/thunder-client` contient une collection de 39 requêtes, dans l'ordre de la démonstration (authentification, administration, CRUD, progression, TMDB, contrôle des accès, suppressions), et son environnement. Dans Thunder Client, importer les deux fichiers, puis suivre les dossiers dans l'ordre. Après chaque connexion, copier le `token` dans `tokenAdmin` ou `tokenAlice` ; après chaque création, copier l'`id` dans la variable correspondante (`aliceId`, `serieId`, `saisonId`, `episodeId`).
