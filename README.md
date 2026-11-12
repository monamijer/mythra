# Series TV

**Series TV** is a web-based television series tracking application built with Spring Boot and Java 21. It provides a RESTful API backed by MySQL and a native HTML, CSS, and JavaScript frontend served directly by the Spring Boot application.

The project was developed as part of an advanced Java programming course, with an emphasis on backend architecture, REST API design, authentication, authorization, and database management.

Each user maintains an independent series library, tracks episode-by-episode viewing progress, and can import additional information from the TMDB API. New accounts require administrator approval before access is granted.

## Getting Started

### Requirements

The following software is required:

- Java 21
- MySQL or MariaDB (XAMPP is supported)
- TMDB API v3 key (required for metadata integration)

### Installation and Execution

1. Start the MySQL server (if using XAMPP, start the MySQL module).
2. Copy `.env.example` to `.env` and configure the required environment variables, including database credentials and the TMDB API key.

   If no `.env` file is present, the application uses the default XAMPP configuration (`root` user with an empty password) and a development JWT secret.

   **Note:** For production deployments, generate a dedicated Base64-encoded JWT secret:

   ```bash
   openssl rand -base64 48
   ```

3. Start the application:

   ```bash
   ./mvnw spring-boot:run
   ```

4. Open the application in your browser:

   <http://localhost:8080>

### Database Initialization

The `series_tv_db` database and its tables are initialized automatically. Hibernate generates the database schema from the JPA entity definitions.

### Initial Administrator Account

On the first application startup, an administrator account is automatically created if no administrator exists.

Default credentials:

| Parameter | Value |
|---|---|
| Email | `admin@seriestv.local` |
| Password | `Admin1234!` |

These values can be customized through the following environment variables in `.env`:

- `ADMIN_EMAIL`
- `ADMIN_PSEUDO`
- `ADMIN_PASSWORD`

**Security note:** Change the default administrator credentials before deploying the application in a production environment.

## User Roles and Account Management

New registrations are assigned the `EN_ATTENTE` (pending) status. Authentication is denied until an administrator approves the account.

The application defines two user roles:

### USER

A standard user can:

- Manage their own television series library.
- Track viewing progress and episode history.
- Access personal statistics.
- Update series statuses, ratings, and reviews.

Users are restricted to their own resources.

### ADMIN

An administrator has access to all application resources and can:

- Approve or reject pending registrations.
- Promote users to administrator status or demote administrators.
- Change user passwords.
- Delete user accounts and their associated data.
- Access all users and application resources.

## Data Model

The application is based on the following JPA entities:

| Entity | Description |
|---|---|
| `Utilisateur` | User account containing a username, email address, hashed password, role, account status, registration date, and last login date. |
| `Serie` | A television series belonging to a user, including viewing status, personal rating, and review. |
| `Saison` | A season associated with a television series. |
| `Episode` | An episode belonging to a season. |
| `Visionnage` | A record linking a user to a watched episode, including the viewing date. |

### Entity Relationships

The database uses the following relationships:

- `Utilisateur 1—N Serie`
- `Serie 1—N Saison`
- `Saison 1—N Episode`
- `Utilisateur 1—N Visionnage`
- `Episode 1—N Visionnage`

The `Visionnage` entity associates a user with an episode. A uniqueness constraint on the user-episode pair prevents duplicate viewing records.

## Architecture

The backend follows a layered architecture, with responsibilities separated into dedicated packages.

```text
com.monprojet.series
├── controller   REST endpoints and request validation (@Valid)
├── service      Business logic and application services
├── repository   Data access layer (Spring Data JPA)
├── entity       JPA entity definitions
├── dto          Request/response objects and mappers
├── security     JWT authentication, security filters, and access control
├── exception    Business exceptions and centralized error handling
└── config       Application configuration, security, TMDB, and initial administrator
```

### Security

The application implements stateless authentication and authorization using Spring Security.

The security mechanisms include:

- **JWT authentication:** Stateless authentication using JSON Web Tokens.
- **Password hashing:** Passwords are hashed using BCrypt.
- **Role-based access control:** Authorization is enforced through `@PreAuthorize`.
- **Resource ownership:** Standard users can only access their own resources, while administrators have unrestricted access.
- **Account status validation:** The account status is checked on every request to prevent access by unapproved or otherwise unauthorized accounts.

## REST API

The application exposes the following primary API endpoints.

### Authentication

| Method | Endpoint |
|---|---|
| `POST` | `/api/auth/inscription` |
| `POST` | `/api/auth/connexion` |

### Series Management

| Method | Endpoint |
|---|---|
| `GET`, `POST` | `/api/utilisateurs/{userId}/series` |
| `GET`, `PUT`, `DELETE` | `/api/utilisateurs/{userId}/series/{id}` |
| `PATCH` | `/api/utilisateurs/{userId}/series/{id}/statut` |
| `PATCH` | `/api/utilisateurs/{userId}/series/{id}/avis` |

### Seasons and Episodes

| Method | Endpoint |
|---|---|
| `GET`, `POST` | `/api/utilisateurs/{userId}/series/{serieId}/saisons` |
| `GET`, `POST` | `/api/saisons/{saisonId}/episodes` |

### Viewing History and Progress Tracking

| Method | Endpoint |
|---|---|
| `POST`, `DELETE` | `/api/utilisateurs/{userId}/visionnages/{episodeId}` |
| `GET` | `/api/utilisateurs/{userId}/progression/{serieId}` |
| `GET` | `/api/utilisateurs/{userId}/series-en-cours` |
| `GET` | `/api/utilisateurs/{userId}/series-terminees` |
| `GET` | `/api/utilisateurs/{userId}/stats` |

### TMDB Integration

The application integrates with the TMDB API to provide series metadata, discovery, and additional information.

Available operations include:

- Search, popular, trending, and top-rated series.
- Upcoming broadcasts and series discovery.
- Genre retrieval.
- Detailed series information.
- Similar series and recommendations.
- Cast and crew information.
- Streaming provider information.
- Video retrieval.
- Actor search and actor details.
- Importing a TMDB series into a user's library.

Primary endpoints:

| Method | Endpoint |
|---|---|
| `GET` | `/api/tmdb/recherche` |
| `GET` | `/api/tmdb/populaires` |
| `GET` | `/api/tmdb/tendances` |
| `GET` | `/api/tmdb/mieux-notees` |
| `GET` | `/api/tmdb/diffusees-bientot` |
| `GET` | `/api/tmdb/decouvrir` |
| `GET` | `/api/tmdb/genres` |
| `GET` | `/api/tmdb/serie/{id}` |
| `GET` | `/api/tmdb/serie/{id}/similaires` |
| `GET` | `/api/tmdb/serie/{id}/recommandations` |
| `GET` | `/api/tmdb/serie/{id}/credits` |
| `GET` | `/api/tmdb/serie/{id}/fournisseurs` |
| `GET` | `/api/tmdb/serie/{id}/videos` |
| `GET` | `/api/tmdb/recherche-acteur` |
| `GET` | `/api/tmdb/acteur/{id}` |
| `POST` | `/api/utilisateurs/{userId}/tmdb/importer/{tmdbId}` |

### Administration

| Method | Endpoint |
|---|---|
| `GET` | `/api/admin/utilisateurs` |
| `GET` | `/api/admin/utilisateurs/statistiques` |
| `PATCH` | `/api/admin/utilisateurs/{id}/approuver` |
| `PATCH` | `/api/admin/utilisateurs/{id}/refuser` |
| `PATCH` | `/api/admin/utilisateurs/{id}/promouvoir` |
| `PATCH` | `/api/admin/utilisateurs/{id}/retrograder` |
| `PATCH` | `/api/admin/utilisateurs/{id}/mot-de-passe` |
| `DELETE` | `/api/admin/utilisateurs/{id}` |

## HTTP Response Codes

The API uses standard HTTP response codes to indicate the outcome of each request.

| Status Code | Description |
|---|---|
| `200 OK` | Request completed successfully. |
| `201 Created` | Resource successfully created. |
| `204 No Content` | Request completed successfully without a response body. |
| `400 Bad Request` | Invalid request data or validation failure. |
| `401 Unauthorized` | Invalid authentication credentials. |
| `403 Forbidden` | Access denied or account not approved. |
| `404 Not Found` | Requested resource does not exist. |
| `409 Conflict` | Business rule violation or duplicate resource. |
| `503 Service Unavailable` | TMDB service unavailable. |
| `500 Internal Server Error` | Unexpected server-side error. |

All API errors follow a consistent JSON response structure.

## API Testing and Demonstration

The `docs/thunder-client` directory contains a Thunder Client collection consisting of **39 requests**, together with the corresponding environment configuration.

The collection is organized according to the intended demonstration sequence:

1. Authentication
2. Administrator operations
3. CRUD operations
4. Viewing progress management
5. TMDB integration
6. Access control verification
7. Resource deletion

### Running the Demonstration

1. Open Visual Studio Code with the Thunder Client extension installed.
2. Import both files from `docs/thunder-client`.
3. Select the imported environment.
4. Execute the requests in the order provided by the collection.

After each authentication request, copy the returned `token` into the appropriate environment variable:

- `tokenAdmin`
- `tokenAlice`

After creating a resource, copy its returned `id` into the corresponding variable:

- `aliceId`
- `serieId`
- `saisonId`
- `episodeId`

This ensures that subsequent requests use the identifiers and authentication tokens generated during the demonstration.

---

**Project:** Series TV  
**Backend:** Java 21, Spring Boot, Spring Security, Spring Data JPA  
**Database:** MySQL / MariaDB  
**Frontend:** HTML, CSS, Vanilla JavaScript  
**External API:** TMDB  
**API Testing:** Thunder Client
