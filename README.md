# Eduplay Backend

REST API for **Eduplay**, an offline-first Unity Android mathematics game for young children, aligned with the
Zimbabwe ZIMSEC curriculum. It replaces Firebase Authentication, Realtime Database and Storage for all personal
data: parent accounts, child profiles, learning progress, game results, reports and subscriptions are stored in
PostgreSQL, either a local container or Supabase-managed.

---

## Contents

1. [Project overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Technology stack](#3-technology-stack)
4. [Prerequisites](#4-prerequisites)
5. [Local development setup](#5-local-development-setup)
6. [Environment variables](#6-environment-variables)
7. [Supabase PostgreSQL configuration](#7-supabase-postgresql-configuration)
8. [Local PostgreSQL Docker configuration](#8-local-postgresql-docker-configuration)
9. [Docker image build](#9-docker-image-build)
10. [Docker Compose deployment](#10-docker-compose-deployment)
11. [API documentation and Swagger](#11-api-documentation-and-swagger)
12. [Database migrations](#12-database-migrations)
13. [Testing](#13-testing)
14. [Production deployment](#14-production-deployment)
15. [Backup and restore](#15-backup-and-restore)
16. [JWT secret rotation](#16-jwt-secret-rotation)
17. [Security considerations](#17-security-considerations)
18. [Operations: monitoring and logging](#18-operations-monitoring-and-logging)
19. [CI/CD: GitHub Actions → ECR → EC2](#19-cicd-github-actions--ecr--ec2)

---

## 1. Project overview

| Capability | Summary |
|---|---|
| Parent accounts | Email + password registration, login, email verification, password reset. Only parents (and future teachers or admins) have credentials. **Children never log in.** |
| Child profiles | Up to 10 per parent (configurable). Minimal data: a display name, an optional birth year and an avatar id. |
| Offline sync | The game queues lesson progress and game results on the device and uploads them with `POST /children/{id}/sync` when it is back online. Uploads are idempotent and safe to retry. |
| Reports | Parent dashboard per child: totals, time spent, averages, per-topic performance, strong topics, topics needing improvement and recent activity. |
| Subscriptions | 14-day trial on registration, with `TRIAL / ACTIVE / EXPIRED / CANCELLED` states and an admin endpoint for payment-provider integration. |
| Privacy | Parental consent is recorded with the policy version. Accounts can export all their data or delete it; deleted data is hard-deleted after 30 days. |

## 2. Architecture

```
 Unity Android app ──HTTPS/JSON──▶ Reverse proxy (TLS) ──▶ Spring Boot API :8080 ──JDBC──▶ PostgreSQL 16
                                                            │  (stateless, JWT)              (local container
                                                            └─ Actuator :8081 (internal)      or Supabase)
```

Layered packages under `com.eduplay.backend`:

| Package | Responsibility |
|---|---|
| `config` | Typed `eduplay.*` properties, OpenAPI, clock |
| `security` | Spring Security filter chains, JWT issue/validate, rate limiting, JSON 401/403 |
| `auth` | Register, login, refresh-token rotation, logout, password reset, email verification |
| `user` | `/me` profile, consent, data export, account deletion |
| `child` | Child profiles and **`ChildAccessService`**, the single ownership gate for all child data |
| `progress` | Progress reads, game-result history and the offline **sync** (SQL upserts) |
| `report` | Dashboard aggregates computed in PostgreSQL |
| `subscription` | Trial creation, status, expiry job, admin updates |
| `email` | Async SMTP delivery |
| `common` | Error model (`ApiError`), exceptions, pagination envelope, utilities |
| `infrastructure` | Request-ID/access-log filter, gzip request bodies, `DATABASE_URL` handling, retention job |

Each feature package has `controller`, `service`, `dto`, `repository` and `domain` (JPA entities) sub-packages.
Entities never leave the service layer; controllers only exchange DTOs.

### Key design decisions

* **Stateless JWT access tokens (15 min) and opaque rotating refresh tokens (30 days).** Refresh tokens are
  256-bit random strings. Only their SHA-256 hash is stored. Each refresh revokes the presented token and issues a new
  one. Presenting an already-revoked token is treated as theft: **all sessions of the user are revoked.**
* **Ownership by construction.** Every child-scoped query goes through
  `ChildAccessService.requireOwnedChild(parentId, childId)`, which matches the child id *and* the parent id from
  the verified JWT. Another family's child returns `404 CHILD_NOT_FOUND`, never `403`, so ids cannot be probed.
* **Sync merges in SQL.** `INSERT … ON CONFLICT` makes merges atomic and race-free across devices (see
  [Offline synchronisation](#offline-synchronisation)).
* **Append-only game results**, enforced by a database trigger that rejects `UPDATE`.
* **Errors**: every error, including 401 and 403 from the security layer, uses one JSON shape with a stable `code`
  the client can switch on.

## 3. Technology stack

Java 21 · Spring Boot 3.5 (Web, Security, Data JPA/Hibernate, Validation, Actuator, Mail) · PostgreSQL 16 ·
Flyway 11 · JJWT 0.13 (HS256) · BCrypt (cost 12) · springdoc-openapi 2.8 · Micrometer/Prometheus · Lombok ·
Maven (wrapper included) · Docker multi-stage build on `eclipse-temurin:21-jre-alpine` · JUnit 5, Mockito,
Testcontainers.

## 4. Prerequisites

| Purpose | Requirement |
|---|---|
| Run with Docker only | Docker Engine 24+ with Compose v2.20+ |
| Develop or run tests on the host | JDK 21 and Docker (for Testcontainers). Maven is not required: use `./mvnw`. |
| Supabase option | A Supabase project and its database password |

No JDK on your machine? Build and test inside a container:

```bash
docker run --rm -v "$PWD":/workspace -v eduplay-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /workspace maven:3.9-eclipse-temurin-21 mvn -B verify
```

## 5. Local development setup

```bash
cp .env.example .env
# Edit .env: set JWT_SECRET (openssl rand -base64 48) and DB_PASSWORD.

# 1. Start only PostgreSQL, published on localhost:
docker compose --profile local up -d postgres
#    (uncomment the ports line under the postgres service first to expose 5432 to the host)

# 2. Optional: Mailpit catches all email at http://localhost:8025
docker compose --profile mail up -d mailpit

# 3. Run the API on the host with the dev profile:
set -a; source .env; set +a
export SPRING_PROFILES_ACTIVE=dev DB_HOST=localhost SMTP_HOST=localhost SMTP_PORT=1025
./mvnw spring-boot:run
```

* API: <http://localhost:8080/api/v1>
* Swagger UI (dev or staging): <http://localhost:8080/swagger-ui.html>
* Health: <http://localhost:8081/actuator/health>

The `dev` profile turns on Swagger, detailed health output, DEBUG logging for `com.eduplay` and Mailpit SMTP
defaults.

## 6. Environment variables

All configuration is read from environment variables. `.env.example` lists every variable with a placeholder
value. Never commit `.env`.

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` in Docker | `dev`, `staging` or `prod` |
| `DATABASE_URL` | – | Optional full connection string. Accepts `jdbc:postgresql://…` or `postgresql://user:pass@host:port/db?…` (Supabase format). Overrides the `DB_*` parts. |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `eduplay` | Database location |
| `DB_USERNAME` / `DB_PASSWORD` | `eduplay` / – | Credentials. When set, they take precedence over credentials inside `DATABASE_URL`. |
| `DB_SSL_MODE` | `prefer` | `disable`, `require`, `verify-ca` or `verify-full`. Use `require` or stricter for Supabase. |
| `DB_POOL_SIZE` | `10` (prod `15`) | Hikari maximum pool size |
| `FLYWAY_ENABLED` | `true` | Run migrations on startup |
| `JWT_SECRET` | **required** | Base64, ≥ 32 bytes. The app refuses to start without it. |
| `JWT_PREVIOUS_SECRETS` | – | Comma-separated retired secrets that are still accepted (rotation) |
| `JWT_ISSUER` | `eduplay-backend` | `iss` claim |
| `JWT_ACCESS_TOKEN_TTL` / `REFRESH_TOKEN_TTL` | `15m` / `30d` | Token lifetimes |
| `PASSWORD_RESET_TTL` / `EMAIL_VERIFICATION_TTL` | `30m` / `24h` | Email token lifetimes |
| `MAIL_ENABLED` | `true` | `false` skips sending (logged without addresses) |
| `MAIL_FROM` | – | Sender address |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | – / `587` | SMTP server |
| `SMTP_AUTH` / `SMTP_STARTTLS` | `true` / `true` | SMTP options |
| `APP_PASSWORD_RESET_URL` / `APP_EMAIL_VERIFICATION_URL` | – | Links in emails; `?token=…` is appended |
| `PRIVACY_POLICY_VERSION` | `2026-01` | Version clients must accept on registration |
| `DELETED_DATA_RETENTION` | `30d` | Delay before soft-deleted accounts and children are hard-deleted |
| `RETENTION_PURGE_CRON` | `0 30 2 * * *` | Purge schedule (UTC) |
| `SUBSCRIPTION_TRIAL_PERIOD` | `14d` | Trial length |
| `MAX_CHILDREN_PER_PARENT` | `10` | Child-profile limit |
| `REPORT_STRONG_SCORE_THRESHOLD` / `REPORT_WEAK_SCORE_THRESHOLD` / `REPORT_MIN_GAMES_FOR_RATING` | `8.0` / `5.0` / `3` | Topic rating rules |
| `RATE_LIMIT_ENABLED` | `true` | In-memory login, registration and reset limits |
| `CORS_ALLOWED_ORIGINS` | empty | Comma-separated browser origins. The Unity app does not need CORS. |
| `SWAGGER_ENABLED` | `false` (prod) | Expose `/swagger-ui.html` and `/v3/api-docs` |
| `FORWARD_HEADERS_STRATEGY` | `native` in staging/prod | Trust `X-Forwarded-*` from the reverse proxy |
| `SERVER_PORT` / `MANAGEMENT_PORT` | `8080` / `8081` | API and Actuator ports |
| `JAVA_OPTS` | see Dockerfile | JVM flags |
| `APP_BIND_ADDRESS` / `APP_PORT` / `APP_MEMORY_LIMIT` | `127.0.0.1` / `8080` / `1g` | Compose publishing and limits |

## 7. Supabase PostgreSQL configuration

The backend uses standard JDBC with no Supabase SDK. Supabase is only the PostgreSQL host.

1. In the Supabase dashboard, open **Project Settings → Database → Connection string**.
2. Use the **Session pooler** (port `5432`) or the **Direct connection**. Avoid the *Transaction pooler* (port
   `6543`): it does not support the prepared statements that JDBC and Hibernate rely on. If you must use it, append
   `&prepareThreshold=0`.
3. Configure `.env` in **one** of these two ways:

   ```dotenv
   # a) a single connection string (copied from Supabase; the password is URL-encoded)
   DATABASE_URL=postgresql://postgres.<project-ref>:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require

   # b) or the individual parts
   DB_HOST=aws-0-<region>.pooler.supabase.com
   DB_PORT=5432
   DB_NAME=postgres
   DB_USERNAME=postgres.<project-ref>
   DB_PASSWORD=<password>
   DB_SSL_MODE=require
   ```

4. Optional: for full certificate verification, download the Supabase CA certificate, mount it into the container,
   and use `DATABASE_URL=jdbc:postgresql://…/postgres?sslmode=verify-full&sslrootcert=/certs/supabase.crt`.
5. Start the API only, with no local database container:

   ```bash
   docker compose --profile supabase up -d --build
   ```

Flyway creates all tables in the `public` schema on first start. Supabase's free tier allows a limited number of
connections, so keep `DB_POOL_SIZE` small (5–10).

## 8. Local PostgreSQL Docker configuration

The `local` profile starts `postgres:16-alpine` on an internal Docker network. Data lives in the named volume
`eduplay_postgres-data`, so it survives restarts and image upgrades. The database port is **not** published to the
host by default; uncomment `ports` under the `postgres` service if you need host access. In `.env`, keep
`DB_HOST=postgres` and `DB_SSL_MODE=prefer` (or `disable`) for this option.

## 9. Docker image build

The multi-stage `Dockerfile` builds with Maven on JDK 21, extracts Spring Boot layers for efficient caching, and
runs on `eclipse-temurin:21-jre-alpine` as the non-root user `10001`.

```bash
docker build -t eduplay-backend:1.0.0 .
# or
docker compose --profile local build
```

Runtime properties of the image:

* `SPRING_PROFILES_ACTIVE=prod`, `TZ=UTC`
* JVM sized from the container memory limit (`-XX:MaxRAMPercentage=75`), G1 GC, exits on OOM so Docker restarts it
* The JVM runs as PID 1 (`exec`), so `docker stop` triggers graceful shutdown: in-flight requests have up to 30 s
  to finish
* `HEALTHCHECK` uses the liveness probe on the internal management port

## 10. Docker Compose deployment

| Task | Local database | Supabase |
|---|---|---|
| Build image | `docker compose --profile local build` | `docker compose --profile supabase build` |
| Start | `docker compose --profile local up -d --build` | `docker compose --profile supabase up -d --build` |
| Stop | `docker compose --profile local down` | `docker compose --profile supabase down` |
| View logs | `docker compose --profile local logs -f app` | `docker compose --profile supabase logs -f app-supabase` |
| Restart app | `docker compose --profile local restart app` | `docker compose --profile supabase restart app-supabase` |
| Status / health | `docker compose --profile local ps` | `docker compose --profile supabase ps` |
| Run migrations only | `docker compose --profile migrate run --rm flyway migrate` | same, with Supabase values in `.env` |
| Run tests | `./mvnw verify` | – |

`make up`, `make logs`, `make down`, `make migrate`, `make test` and `make backup` wrap these commands. Pass
`PROFILE=supabase` to target Supabase.

`docker compose down` keeps the database volume. `docker compose down -v` **deletes all data**.

Container hardening in Compose: read-only root filesystem (tmpfs `/tmp`), all Linux capabilities dropped,
`no-new-privileges`, a memory limit, json-file log rotation, and the API bound to `127.0.0.1` by default, ready for
a reverse proxy.

## 11. API documentation and Swagger

* **Static spec: [`docs/openapi.yaml`](docs/openapi.yaml)** (OpenAPI 3.1). Use it to generate a client, for example
  a C# client for Unity with `openapi-generator-cli generate -i docs/openapi.yaml -g csharp`, or import it into
  Postman or Insomnia. Every operation has a stable `operationId`, and every error is described by the `ApiError`
  schema as the `default` response.
* The file is generated from the code. `OpenApiSpecTest` fails the build if it falls out of date. After changing
  the API, regenerate it:

  ```bash
  OPENAPI_WRITE=true ./mvnw test -Dtest=OpenApiSpecTest     # or: make openapi
  ```

* Swagger UI: `/swagger-ui.html`; live spec: `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`. Enabled in `dev` and
  `staging`; in `prod` only with `SWAGGER_ENABLED=true`.
* Click **Authorize** and paste an access token to call protected endpoints.

### Endpoints (all under `/api/v1`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | – | Create a parent account (requires `policyVersion` and `parentalConsent: true`) → tokens |
| POST | `/auth/login` | – | Sign in → tokens |
| POST | `/auth/refresh` | – | Rotate a refresh token → new access and refresh tokens |
| POST | `/auth/logout` | – | Revoke one refresh token (this device) |
| POST | `/auth/logout-all` | Bearer | Revoke all sessions |
| POST | `/auth/password-reset/request` | – | Always `202` with a generic message |
| POST | `/auth/password-reset/confirm` | – | `{token, newPassword}`; revokes all sessions |
| POST | `/auth/verify-email` | – | `{token}` from the verification email |
| POST | `/auth/verify-email/resend` | Bearer | Re-send the verification email |
| GET / PATCH / DELETE | `/me` | Bearer | Profile; partial update; delete (body `{password}`) |
| GET | `/me/export` | Bearer | Full data export (JSON) |
| POST | `/me/consent` | Bearer | Accept a new privacy-policy version |
| GET / POST | `/children` | Bearer | List (paginated) or create child profiles |
| GET / PATCH / DELETE | `/children/{childId}` | Bearer | Read, update or delete a child profile |
| GET | `/children/{childId}/progress` | Bearer | Merged lesson progress and `serverTime` |
| POST | `/children/{childId}/sync` | Bearer | Offline upload (see below) |
| GET | `/children/{childId}/game-results` | Bearer | Paginated game history, newest first |
| GET | `/children/{childId}/report?days=N` | Bearer | Parent dashboard |
| GET | `/subscription` | Bearer | Current subscription status |
| PUT | `/admin/users/{userId}/subscription` | Bearer (ADMIN) | Set plan, status and period |

**Conventions**

* JSON in and out; timestamps are ISO-8601 UTC (`2026-10-27T08:15:00Z`).
* Null fields are omitted. Collections are always wrapped in an object (`{"items": [...]}`), never a top-level
  array, because Unity's `JsonUtility` cannot parse top-level arrays.
* Paginated responses: `{items, page, size, totalItems, totalPages}`; query `?page=0&size=20`.
* Every response carries `X-Request-Id`. Send your own to correlate client and server logs.
* Error format:

  ```json
  {"timestamp":"2026-10-31T10:00:00Z","status":401,"error":"Unauthorized","code":"TOKEN_EXPIRED",
   "message":"Access token has expired","path":"/api/v1/children/…/sync","requestId":"…",
   "fieldErrors":[{"field":"gameResults[3].score","message":"must be greater than or equal to 0"}]}
  ```

  Useful codes: `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_CREDENTIALS`, `TOKEN_EXPIRED`,
  `INVALID_TOKEN`, `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_REUSED`, `EMAIL_ALREADY_REGISTERED`,
  `POLICY_VERSION_MISMATCH`, `CHILD_NOT_FOUND`, `CHILD_LIMIT_REACHED`, `RATE_LIMITED` (with `Retry-After`),
  `INVALID_OR_EXPIRED_TOKEN`.

### Offline synchronisation

`POST /api/v1/children/{childId}/sync`

```json
{
  "lessonProgress": [
    {"sectionId": "number", "topicId": "counting", "lessonId": "level1",
     "currentGameIndex": 3, "completed": false, "updatedAt": "2026-10-27T08:15:00Z"}
  ],
  "gameResults": [
    {"id": "0f8e2c4a-0000-0000-0000-000000000001", "sectionId": "number", "topicId": "counting",
     "lessonId": "level1", "gameIndex": 2, "gameType": "MatchingGame", "score": 8, "attempts": 1,
     "mistakes": 2, "durationSeconds": 74, "completed": true, "playedAt": "2026-10-27T08:14:10Z"}
  ]
}
```

Response:

```json
{"childId":"…","serverTime":"2026-10-27T08:20:01Z","acceptedGameResults":1,"duplicateGameResults":0,
 "lessonProgress":[{"sectionId":"number","topicId":"counting","lessonId":"level1","currentGameIndex":3,
                    "completed":false,"updatedAt":"2026-10-27T08:15:00Z"}]}
```

Server rules:

1. Game results are append-only and keyed by the client UUID. A result whose id is already stored is skipped and
   counted in `duplicateGameResults`, including a repeated id within the same batch.
2. At most 500 game results and 500 lesson entries per request. Larger queues go in several requests.
3. Lesson merge: **completed always beats incomplete.** If both are incomplete, the **higher `currentGameIndex`**
   wins. If both are complete, the original `completedAt` is kept. `updatedAt` only moves forward.
4. Timestamps in the future (device clock drift) are clamped to the server time. Use `serverTime` to detect drift.
5. One database transaction per request. An invalid item rejects the whole batch with `400` and nothing is
   written, so the client can fix or drop the bad item and resend.
6. Requests can be gzip-compressed (`Content-Encoding: gzip`, up to 10 MB decompressed). Responses over 1 KB are
   gzip-compressed when the client sends `Accept-Encoding: gzip`.

**Recommended Unity client loop**

1. Store results and progress locally with a client-generated UUID per result.
2. When online, send up to 500 queued results per request.
3. On `200`: delete the sent results from the queue and replace local lesson progress with `lessonProgress`.
4. On `401 TOKEN_EXPIRED`: call `/auth/refresh`, **persist the new refresh token immediately**, then resend the
   *same* batch. This is safe because sync is idempotent.
5. On network errors or `5xx`: keep the queue and retry later with exponential backoff.
6. On `401 REFRESH_TOKEN_REUSED` or `INVALID_REFRESH_TOKEN`: keep the queue and ask the parent to sign in again.

## 12. Database migrations

Migrations live in `src/main/resources/db/migration` (`V<n>__description.sql`) and are applied by Flyway.

* **Automatically** on application startup (`FLYWAY_ENABLED=true`, the default).
* **Manually**, without starting the API:

  ```bash
  docker compose --profile migrate run --rm flyway info
  docker compose --profile migrate run --rm flyway migrate
  ```

  The Flyway container uses `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` and `DB_SSL_MODE` from
  `.env`, so it works for both the local database and Supabase. When running against the local container, start
  `postgres` first.
* Hibernate runs with `ddl-auto=validate`: the app refuses to start if entities and schema disagree.
* Never edit an applied migration. Add a new `V2__…sql` instead.

Schema overview: `users`, `refresh_tokens`, `password_reset_tokens`, `email_verification_tokens`,
`consent_records`, `children`, `lesson_progress` (unique on child, section, topic and lesson), `game_results`
(append-only trigger) and `subscriptions`. All tables use UUID primary keys and `TIMESTAMPTZ` columns. Foreign keys
cascade on hard delete.

## 13. Testing

```bash
./mvnw verify            # unit + integration tests (Docker must be running)
./mvnw test -Dtest=SyncIntegrationTest
```

Integration tests start a real **PostgreSQL 16** with Testcontainers, apply the Flyway migrations, and exercise the
full HTTP and security stack through MockMvc. Email sending is mocked. A controllable clock covers expiry
scenarios.

| Area | Test class |
|---|---|
| Registration, login, JWT validation, headers, errors | `AuthIntegrationTest`, `JwtServiceTest` |
| Refresh rotation, reuse detection, multiple devices | `RefreshTokenIntegrationTest` |
| Password reset and email verification | `PasswordResetIntegrationTest`, `EmailVerificationIntegrationTest` |
| Rate limiting | `RateLimitIntegrationTest`, `RateLimitServiceTest` |
| Child CRUD, pagination, ownership isolation | `ChildIntegrationTest` |
| Offline sync, duplicates, merge rules, gzip, 500-item limit, append-only | `SyncIntegrationTest` |
| Reports | `ReportIntegrationTest`, `ReportServiceTest` |
| Subscriptions | `SubscriptionIntegrationTest` |
| Profile, export, deletion and 30-day purge | `AccountIntegrationTest` |
| Schema, constraints and indexes | `DatabaseMigrationIntegrationTest` |
| `DATABASE_URL` / Supabase parsing | `DatabaseUrlEnvironmentPostProcessorTest` |
| `docs/openapi.yaml` in sync with code | `OpenApiSpecTest` |

## 14. Production deployment

Target: a Linux server in Zimbabwe running Docker.

1. **Server preparation**: install Docker Engine and the Compose plugin, enable the firewall (allow 22, 80 and 443
   only), enable automatic security updates, and set the clock with NTP.
2. **Get the code** (or a pre-built image from your registry): `git clone … && cd eduplay`.
3. **Configure**: `cp .env.example .env && chmod 600 .env`, then set strong `DB_PASSWORD`, `JWT_SECRET`, SMTP
   credentials, `MAIL_FROM`, app link URLs, and keep `SPRING_PROFILES_ACTIVE=prod` and
   `FORWARD_HEADERS_STRATEGY=native`.
4. **Start**: `docker compose --profile local up -d --build` (or `--profile supabase`).
5. **TLS reverse proxy** in front of `127.0.0.1:8080`. Example with Caddy (automatic Let's Encrypt certificates):

   ```caddyfile
   api.eduplay.example {
       encode gzip
       request_body { max_size 12MB }
       reverse_proxy 127.0.0.1:8080
   }
   ```

   With nginx, set `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`,
   `proxy_set_header X-Forwarded-Proto $scheme;` and `client_max_body_size 12m;`.
6. **Verify**: `docker compose --profile local ps` shows `healthy`;
   `curl https://api.eduplay.example/livez` returns `{"status":"UP"}`.
7. **Upgrades**: `git pull && docker compose --profile local up -d --build`. Flyway applies new migrations on start.
   Take a backup first.
8. **Scaling**: the API is stateless apart from the in-memory rate limiter. With several replicas, also rate limit
   at the proxy, or move the limiter to Redis.

## 15. Backup and restore

### Local PostgreSQL container

```bash
# Backup (compressed custom format)
mkdir -p backups
docker compose --profile local exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backups/eduplay-$(date -u +%Y%m%dT%H%M%SZ).dump
# or simply: make backup

# Restore into the running database (stop the app first)
docker compose --profile local stop app
docker compose --profile local exec -T postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < backups/eduplay-XXXX.dump
docker compose --profile local start app
```

Recommendations: schedule the backup daily with cron, for example
`0 1 * * * cd /opt/eduplay && make backup && find backups -mtime +14 -delete`. Copy backups off the server
(encrypted, for example with `age` or `gpg`). Test a restore every month on a staging machine. Backups contain
children's data, so store them as securely as the database itself.

### Supabase

Supabase takes daily backups (and point-in-time recovery on paid plans), available under **Database → Backups**.
For an independent copy, run `pg_dump` against the session-pooler connection:

```bash
docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:16-alpine \
  pg_dump -h "$DB_HOST" -p 5432 -U "$DB_USERNAME" -d postgres -n public -Fc > backups/supabase-$(date -u +%F).dump
```

Account deletions are hard-deleted after 30 days, but older backups still contain those records. Keep backup
retention at or below what your privacy policy promises.

## 16. JWT secret rotation

Access tokens are signed with `JWT_SECRET`. Each token's `kid` header identifies the key that signed it, so you
can rotate without signing anyone out:

1. Generate a new secret: `openssl rand -base64 48`.
2. In `.env`, move the current value to `JWT_PREVIOUS_SECRETS` and put the new value in `JWT_SECRET`:

   ```dotenv
   JWT_SECRET=<new>
   JWT_PREVIOUS_SECRETS=<old>
   ```

3. `docker compose --profile local up -d` (restarts the app). New tokens use the new key, and tokens signed with the
   old key keep working until they expire.
4. After at least 15 minutes (the access-token lifetime), remove `JWT_PREVIOUS_SECRETS` and restart again.

Refresh tokens are opaque database records, not JWTs, so they are unaffected. **After a suspected leak**, skip the
grace period: set only the new `JWT_SECRET` and restart. To also force every user to sign in again, revoke all
refresh tokens:
`UPDATE refresh_tokens SET revoked_at = now() WHERE revoked_at IS NULL;`

## 17. Security considerations

* **Passwords**: BCrypt with cost 12, 8–128 characters, never logged or returned. Login uses the same generic
  error and equalised timing for unknown emails and wrong passwords.
* **Tokens**: 15-minute HS256 access tokens. The issuer is enforced and the `alg` is fixed (unsigned tokens are
  rejected). Refresh tokens are random 256-bit values stored only as SHA-256 hashes, rotated on every use, with
  reuse detection that revokes all sessions. Row locks prevent two concurrent refreshes from both succeeding.
  Password reset and account deletion revoke every session.
* **Password reset**: always returns the same `202` response (no email enumeration). Tokens are single-use, valid
  for 30 minutes, and invalidated by newer requests.
* **Rate limiting**: token buckets per IP and per account for login, registration, password reset and verification
  emails, returning `429` with `Retry-After`. Limits are generous per IP because mobile carriers in Zimbabwe often
  place many users behind one carrier-grade NAT address, and stricter per account.
* **Authorisation**: a deny-by-default filter chain, an `ADMIN` role for `/api/v1/admin/**`, and child ownership
  checked on every child-data operation (404 for other families' data).
* **Input**: Bean Validation on every DTO, bounded sizes for strings, lists (500), request bodies (2 MB) and
  decompressed bodies (10 MB). Unknown JSON fields are ignored.
* **HTTP headers**: HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, a restrictive CSP,
  `Referrer-Policy: no-referrer` and `Permissions-Policy`. CORS is off unless origins are configured.
* **Logs**: no request bodies, tokens, passwords, emails or query strings, only method, path, status, duration,
  request id and internal UUIDs. Stack traces are never returned to clients.
* **Actuator** listens on the separate management port 8081, which is never published outside the Docker network.
  Only `health`, `info` and `prometheus` are exposed, and health details are hidden.
* **Privacy and children**: children have no credentials and only minimal data. Parental consent is stored with
  the policy version, timestamp and IP. Parents can export and delete their data, which is hard-deleted after 30
  days. No personal data is sent to Firebase or any third party except the configured SMTP provider. Keep Firebase
  in the Unity app only for non-personal features, such as crash reporting without user identifiers. Review
  obligations under Zimbabwe's Cyber and Data Protection Act [Chapter 12:07].
* **Container**: non-root user, read-only filesystem, dropped capabilities, `no-new-privileges`, secrets only via
  environment.

## 18. Operations: monitoring and logging

| Endpoint | Port | Purpose |
|---|---|---|
| `/livez` | 8080 | Liveness: the process is up (safe to expose to the proxy or load balancer) |
| `/readyz` | 8080 | Readiness: includes the database check |
| `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | 8081 | Same, on the internal port |
| `/actuator/prometheus` | 8081 | Micrometer metrics: HTTP latency, JVM, Hikari pool, Tomcat |
| `/actuator/info` | 8081 | Build information |

* **Graceful shutdown**: on `SIGTERM`, readiness switches to `OUT_OF_SERVICE`, new requests are refused and
  in-flight requests get up to `SHUTDOWN_TIMEOUT` (30 s). Compose allows 45 s before `SIGKILL`.
* **Structured logs**: `staging` and `prod` write one JSON object per line in ECS format to stdout, including
  `requestId`, which is ready for Loki, ELK or `docker logs | jq`. `dev` writes plain text.
* **Log rotation**: Compose uses the json-file driver with `max-size: 20m` and `max-file: 10` for the app. To set the
  same defaults for every container, add `{"log-driver":"json-file","log-opts":{"max-size":"20m","max-file":"10"}}`
  to `/etc/docker/daemon.json`. To also write log files, set `LOGGING_FILE_NAME=/tmp/eduplay.log` (or mount a volume),
  with `LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE=20MB` and `LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY=14`.
* **Scheduled jobs** (UTC): subscription expiry every 15 minutes, and the retention purge (accounts and children
  deleted more than 30 days ago, plus expired tokens) daily at 02:30.
* Suggested alerts: readiness failing, the 5xx rate, p95 latency on `/sync`, Hikari pending connections, and disk
  usage of the PostgreSQL volume.

## 19. CI/CD: GitHub Actions → ECR → EC2

`.github/workflows/ci-cd.yml` runs on every pull request and push to `main`:

| Job | Runs on | What it does |
|---|---|---|
| **Build and test** | PRs and `main` | `./mvnw verify` with Testcontainers PostgreSQL |
| **Build and push image** | `main` only | Builds the `linux/amd64` image and pushes it to ECR as `eduplay-backend:<commit-sha>`. Tags are immutable. |
| **Deploy to EC2** | `main` only | Opens SSH to the instance with EC2 Instance Connect, uploads `deploy/`, runs `deploy.sh <image>`, then smoke-tests `/livez` |

**AWS resources:** region `us-east-1`; ECR repository
`383975042508.dkr.ecr.us-east-1.amazonaws.com/eduplay-backend` (scan on push, keeps the last 30 images);
instance `i-089ab8d959ae52044` (Ubuntu, user `ubuntu`).

**GitHub configuration:** secrets `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`, and the variable
`EC2_HOST_KEY` (the server's `ssh-ed25519 …` host key, used for strict host verification). If the instance is
replaced, update the variable with `ssh-keyscan -t ed25519 <ip>`.

**On the server** (`/opt/eduplay`), the stack runs as Caddy (ports 80/443) → app → PostgreSQL 16, with data in
Docker volumes. The first deployment generates `/opt/eduplay/.env` with new random database and JWT secrets.
Those secrets never leave the server, and later deployments keep them. Edit that file to configure SMTP,
Swagger, CORS or a domain, then re-run the latest deployment from the Actions tab or restart the stack:

```bash
sudo docker compose -f /opt/eduplay/docker-compose.prod.yml --project-directory /opt/eduplay up -d
```

**HTTPS:** point a DNS name at the instance (ideally through an Elastic IP, because the default public IP
changes when the instance is stopped), set `SITE_ADDRESS=api.your-domain` in `/opt/eduplay/.env` and restart.
Caddy obtains and renews the Let's Encrypt certificate automatically. Until then the API is served over
**plain HTTP**, so do not use real parent accounts on it.

**Rollback:** re-run an earlier successful workflow run from the Actions tab, or on the server:
`sudo /opt/eduplay/deploy.sh 383975042508.dkr.ecr.us-east-1.amazonaws.com/eduplay-backend:<older-sha>`
(log in to ECR first, as the workflow does).
