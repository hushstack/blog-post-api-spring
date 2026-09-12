# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All commands use the Maven wrapper from the project root. To run the whole stack —
app, Postgres, Redis, RabbitMQ — without a local Java or database install, use Docker
instead: `docker compose up -d`. See `README-DOCKER.md`.

```bash
./mvnw spring-boot:run              # run the app (DevTools restart is active)
./mvnw clean package                # build the executable jar into target/
./mvnw test                         # run the full test suite
./mvnw test -Dtest=ClassName        # run one test class
./mvnw test -Dtest=ClassName#method # run one test method
./mvnw spring-boot:build-image      # build an OCI image
```

There is no separate lint step; compilation (`./mvnw compile`) is the gate.

## State of the project

A working blog/social API: auth (register, OTP verify, login, refresh, logout, password reset), profiles, posts with images, feed, comments, reactions, friendships, and notifications. Seven modules — `auth`, `user`, `post`, `comment`, `reaction`, `friendship`, `notification` — plus `common`, `config`, `security`, `integration/{storage,email}`, `messaging`, and `scheduler`.

Runtime dependencies: PostgreSQL, Redis, RabbitMQ. The app will not start without a datasource; Redis is required for OTP, token revocation, reaction counts and rate limiting; RabbitMQ carries OTP delivery, image processing, feed fan-out and notifications.

**Flyway owns the schema in every profile** and `ddl-auto` is `validate` everywhere — see *Database migrations* below. Every queue consumer is now implemented; none is a stub.

## Toolchain and version constraints

- **Java 25**, **Spring Boot 4.1.1**, Maven 3.9.16 via wrapper. Boot 4 APIs differ from Boot 3 in places — verify against the 4.1.x reference docs rather than assuming Boot 3 idioms.
- The web starter is `spring-boot-starter-webmvc` (Boot 4 name), not `spring-boot-starter-web`.
- **Test dependencies are per-starter in Boot 4.** There is no `spring-boot-starter-test`; instead each starter has a sibling `-test` artifact (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`, …). When adding a new starter, add its matching `-test` artifact too, or the corresponding test slice annotations and assertions will not resolve.
- **Jackson 3, not Jackson 2.** Boot 4 ships `tools.jackson.core:jackson-databind:3.x`; the autoconfigured `ObjectMapper` bean is `tools.jackson.databind.ObjectMapper`. Jackson 2 (`com.fasterxml.jackson.*`) is still on the classpath transitively via jjwt and springdoc, so code importing it **compiles fine and then fails at runtime with "No qualifying bean of type ObjectMapper"**. Always import from `tools.jackson`. The same split runs through the ecosystem: use `GenericJacksonJsonRedisSerializer` (not `...Jackson2...`) and `JacksonJsonMessageConverter` (not `Jackson2JsonMessageConverter`, and it takes a `JsonMapper`, not an `ObjectMapper`).
- **`spring-boot-starter-json` must be declared explicitly** — the webmvc starter does not pull JSON support in on its own.
- **Boot 4 splits autoconfiguration into per-technology modules.** A third-party library on the classpath is no longer enough to get it configured. Flyway needs `org.springframework.boot:spring-boot-flyway` for the autoconfiguration *in addition to* `flyway-core`; with only `flyway-core`, Flyway is silently never invoked and the app dies later on schema validation, which reads like a migration bug rather than a missing dependency. Expect the same shape for other integrations.
- **`flyway-database-postgresql` is a separate artifact** from Flyway 10 onward. Without it, Flyway cannot resolve the Postgres dialect and fails with "Unsupported Database: PostgreSQL".
- **Lombok** is wired through explicit `annotationProcessorPaths` on `maven-compiler-plugin` for both `default-compile` and `default-testCompile`. Any future annotation processor (e.g. MapStruct) must be added to *both* executions, and MapStruct specifically must be ordered after Lombok.

## Runtime configuration

Config is split three ways: `application.properties` holds shared settings and reads secrets from the environment; `application-dev.properties` supplies localhost defaults; `application-prod.properties` supplies nothing and requires every value from the environment. Profile files override the base, which is why `app.jwt.secret` can have no default in the base file and still boot in dev.

Environment variables in play: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`, `RABBITMQ_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `FILESYSTEM_DISK`, `STORAGE_PATH`, `STORAGE_PUBLIC_URL`, `R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY`/`R2_BUCKET`/`R2_REGION`/`R2_ENDPOINT`/`R2_URL`/`R2_USE_PATH_STYLE_ENDPOINT`, `SHARE_BASE_URL`, `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_FROM`. `JWT_SECRET` must be at least 32 bytes — `JwtProvider` refuses to start otherwise, deliberately. The `MAIL_*` set is required in prod; in dev it is read only under the opt-in `mail` profile, and without that profile nothing maps onto `spring.mail.*` at all, which is what selects the no-delivery mail fallback locally. The `mail` profile also sets SMTP socket timeouts (JavaMail's default is infinite, and the `otp.send` consumer is one thread) and turns `management.health.mail` off, because the container healthcheck polls every 10 s and would otherwise open an SMTP session each time.

## Database migrations

Flyway owns the schema. `ddl-auto` is `validate` in **every** profile, set once in `application.properties`; the dev profile deliberately no longer sets `update`.

- Migrations live in `src/main/resources/db/migration` as `V<n>__<snake_case>.sql`. `V1__baseline_schema.sql` is the whole schema as of the first release; `V2__notifications.sql` adds the notifications table.
- **A schema change starts with a new migration**, never with an entity edit alone. Hibernate validates against what Flyway built and refuses to start on a mismatch, which is the point — `update` and Flyway both writing DDL is how a database drifts away from its migrations unnoticed.
- The column types in a migration must match what Hibernate expects, not merely something compatible. The reliable way to write one for a new entity is to let Hibernate emit its own DDL first and copy from it:

  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.arguments="--spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none \
      --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
      --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=/tmp/schema.sql"
  ```

  `Instant` maps to `timestamp(6) with time zone`, and `@GeneratedValue(strategy = UUID)` means ids are generated in the application — no database default and no uuid extension.
- **A database built by the old `ddl-auto=update` cannot be migrated in place.** `V1` creates every table from scratch and will fail against one that already has them. Drop and recreate the dev database rather than reaching for `spring.flyway.baseline-on-migrate`, which would mark an unknown schema as already at V1 and skip the real definition.
- The entities carry no JPA relationships (plain `UUID` columns), so foreign keys exist only in the migrations. They cascade on delete where ownership is unambiguous; `posts.original_post_id` is `on delete set null` so a repost outlives the post it quoted. `reactions.target_id` and `notifications.target_id` are polymorphic and therefore carry no foreign key at all.

`SecurityConfig` defines the chain: stateless, CSRF off (no cookies or sessions to protect), JWT filter ahead of `UsernamePasswordAuthenticationFilter`. **A new endpoint is authenticated unless the chain names it**, which is the right default — add a matcher only when anonymous access is genuinely intended, and read the ordering note under *Implementation notes* first.

Rate limiting is opt-in per handler via `@RateLimited` on a controller method, enforced by `RateLimitInterceptor` (registered for all paths in `WebConfig`) against a fixed-window counter in Redis. Authenticated callers are bucketed by user id, anonymous ones by `getRemoteAddr()` — deliberately not `X-Forwarded-For`, which a client can set freely; behind a proxy set `server.forward-headers-strategy` so the container resolves the real address first. It fails **open** when Redis is unreachable, on the grounds that a Redis outage should not lock everyone out of login — by catching `DataAccessException`, because Lettuce throws on an outage and never returns null; the earlier null check alone was unreachable. `spring.data.redis.timeout` is 2 s for the same reason: the blacklist check sits on every authenticated request, and the 60 s Lettuce default would turn a stalled Redis into 60 s requests. The auth endpoints carry limits; a handler without the annotation is unlimited.

Actuator exposes `/actuator/health` only, with `show-details=never`. Widening `management.endpoints.web.exposure.include` puts the widened set behind the security chain, so authorize it deliberately.

## Swagger UI

On at `/swagger-ui.html` in dev, and switched off entirely in prod — `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are both false there, so none of the UI settings in the base file reach a deployed environment. `SecurityConfig` permits `/swagger-ui/**`, `/swagger-ui.html` and `/v3/api-docs/**`.

- **The error envelope is attached in `OpenApiConfig`, not by annotating handlers.** An `OperationCustomizer` sets `ApiErrorResponse` as the OpenAPI `default` response on every operation, and an `OpenApiCustomizer` registers the schema, which no handler signature mentions. `default` means "any status not listed", which is exactly what `GlobalExceptionHandler` guarantees — enumerating 400/401/404 per handler would be a per-endpoint guess, and a guess goes stale.
- **`springdoc.swagger-ui.persist-authorization=true`** keeps the bearer token across page reloads, in browser localStorage. A real place for a token to sit, and the reason it is scoped to the profiles where the UI exists at all.
- **`info.description` carries the token walkthrough** — where the OTP surfaces (emailed under the `mail` profile, printed at DEBUG without it) and what to paste where. Keep it in step with `EmailConfig`, and **keep it short**: Swagger UI renders the description above the Servers row, so a long one pushes the **Authorize** button — the only place a token can be entered — down the page, where testers do not find it.
- **`POST /posts` and the multipart `PUT /posts/{id}` document `content` and `visibility` as query parameters**, because they are `@RequestParam` on a multipart handler. Swagger UI therefore puts them in the query string. It binds correctly — Spring reads `@RequestParam` from multipart form fields too — but post content ends up in access logs, so switch them to `@RequestPart` if that matters.

## File storage

`FILESYSTEM_DISK` selects the backend — `local` (filesystem) or `r2` (Cloudflare R2 over its
S3-compatible API) — and `StorageConfig` builds the matching `StorageService` in one bean method,
the same shape as `EmailConfig`. Every upload path in the app goes through that one seam:
`UserServiceImpl.updateAvatar`, `PostServiceImpl.storeImages`/`delete`, and the `image.process`
consumer's read-and-replace. None of them knows which backend it is talking to.

- **The R2 config names two hosts and they are not interchangeable.** `R2_ENDPOINT` is the private
  S3 API host the SDK signs against and carries **no** bucket name; `R2_URL` is the public r2.dev or
  custom domain that stored URLs are built from, and the only one that ever reaches a client. Swap
  them and uploads succeed while every image 404s.
- **Selecting `r2` with an incomplete configuration fails the context start**, naming the missing
  property. The alternative — falling back to local disk — writes user uploads to a container
  filesystem that the next deploy erases, and nothing would report it.
- **The AWS SDK is pinned through its imported BOM** (`aws-sdk.version` in `pom.xml`); Boot does not
  manage it. Mixed SDK artifact versions are the usual source of `NoSuchMethodError` here.
- **Checksums are set to `WHEN_REQUIRED`.** Recent SDK versions attach a CRC32 trailer to every
  request by default, which R2, S3-compatible but not S3, rejects on some paths.
- **Uploads carry `Cache-Control: public, max-age=3600`, deliberately not `immutable`.** The
  `image.process` stage rewrites the object at the same key, so a long-lived cache entry would pin
  the full-size original in front of the downscaled one.
- **Validation is in `AbstractStorageService`, not in either backend** (Template Method): reject
  empty, cap the size, sniff the content type from the magic bytes and check it against the
  allowlist, generate the stored name, and prove the bytes decode as an image. The part's declared
  `Content-Type` is never consulted — Postman sends `application/octet-stream` when its MIME lookup
  fails, and an attacker sends whatever passes — so the bytes decide (OWASP A08). That is the security-critical half, and two copies of it would
  eventually stop agreeing. `keyFor` maps a stored URL back to an object key and is the single place
  a foreign or traversing URL is rejected, so no backend can be steered outside its own prefix.

## Docker

`docker compose up -d` brings up the app plus Postgres, Redis and RabbitMQ; `README-DOCKER.md` is
the user-facing guide, including how to hand a copy to someone else.

- **Every compose value has a working default**, so a fresh clone with no `.env` still runs — on the
  `dev` profile, with `FILESYSTEM_DISK=local` and no credentials of any kind. That is what makes the
  stack shareable: a second person needs the compose file, never the secrets.
- **Compose reads `.env` for its `${...}` defaults**, so a real R2 bucket or SMTP host is picked up
  automatically without ever entering the image. `.env` is in `.dockerignore` as well as
  `.gitignore` — the build context is uploaded to the daemon and anything in it can land in a layer.
- **The service names are the hostnames.** `DB_URL` points at `postgres`, not `localhost`; inside
  the compose network `localhost` is the container itself. This is the usual first failure when
  copying settings out of `application-dev.properties`.
- **The app waits on health checks**, not on `depends_on` alone: Flyway cannot migrate a Postgres
  that is still starting, and the container would exit before the retry.
- **Every service carries a memory limit, and the JVM flags assume it.** Without a cgroup limit each runtime plans for the whole host — `MaxRAMPercentage` is a share of the *container* limit only when one exists, RabbitMQ's watermark is 40 % of host RAM — and four of them on a 2 GB VPS overcommit and swap, which shows up as 10–20 s on every request including `/actuator/health`. Defaults (`APP_MEM` 768M, `RABBITMQ_MEM` 384M, `POSTGRES_MEM` 320M, `REDIS_MEM` 160M) sum to ~1.6 GB. The Dockerfile pairs that with `-XX:+UseSerialGC` (1–2 vCPUs; one collector thread instead of G1's background ones) and `-XX:+ExitOnOutOfMemoryError` (so `restart: unless-stopped` gets a dead JVM, not a limping one). Logs are rotated per service through the `x-logging` anchor, and `SPRING_JPA_SHOW_SQL` defaults to `false` in the container so the dev profile's statement log stays local.
- **Tests are skipped in the image build** (`-DskipTests`). They need Postgres, Redis and RabbitMQ,
  none of which exist in a build container; run them against the running stack instead.
- **An optional secret is listed in compose with no value at all** (`JWT_SECRET:`), never as
  `${JWT_SECRET:-}`. The pass-through form omits the variable when it is unset; the `:-` form sets
  it to an **empty string**, and an empty value *overrides* the `${JWT_SECRET:dev-fallback}` default
  in `application-dev.properties` rather than falling back to it — the app then dies on
  "app.jwt.secret must not be blank". Same shape as the `spring.mail.host` trap that the `mail` profile now closes: present-but-
  empty is not the same as absent. The dev fallback is committed, so anyone who reads the repository
  can mint a token; replace it before the port is reachable by anyone untrusted (OWASP A07).
- **The Postgres volume mounts at `/var/lib/postgresql`, not `/var/lib/postgresql/data`.** Postgres
  18 moved the cluster into a version-named subdirectory so `pg_upgrade --link` does not cross a
  mount boundary. The old path makes the container log a wall of explanation and never become
  healthy, which reads like a healthcheck bug.
- **`/data/uploads` is created and chowned in the Dockerfile, before the `USER` switch.** Docker
  seeds a fresh named volume from whatever the image holds at the mount point, ownership included,
  so without that step the volume arrives owned by root and the unprivileged process cannot write to
  it — surfacing as `INTERNAL_ERROR "Could not store file"` on the first upload, not at startup.
- **Only 8080 and 15672 are published.** Postgres and Redis stay on the compose network so they
  cannot collide with a native install; RabbitMQ's management UI can, and `RABBITMQ_UI_PORT` moves
  it.

## Package naming

The root package is `com.cachewraith.blog_post_api_spring` — with an underscore, because the artifact id `blog-post-api-spring` is not a legal package name. New packages nest under this exact root; do not "fix" the underscore.

## Implementation notes and deviations from the spec

Where the build departs from the written spec, it is deliberate and recorded here.

- **`POST /auth/verify-otp` takes `{email, code}` in the body**, not the code in the path. Redis keys OTP by user, so a bare code cannot be looked up without scanning every `otp:*` key — racy, collision-prone, and it makes the per-user attempt cap unenforceable. A code in a URL also lands in access logs and `Referer` headers.
- **Every OTP is verified at `POST /auth/verify-otp` with `{email, code}`, and the account's state picks the purpose — there is no `purpose` field anywhere.** `AuthServiceImpl.purposeFor` is the single rule: `PENDING_VERIFICATION` → `REGISTER`, `ACTIVE` → `RESET_PASSWORD`, `SUSPENDED` → none. Every issuer (`register`, `forgot-password`, `resend-otp`) and the verifier go through it, so the code that was sent is always the code that is checked. A client-supplied purpose was tried and dropped: an active user who resent a code and then verified without naming `RESET_PASSWORD` got `OTP_INVALID` for a correct code, because codes are keyed by purpose in Redis. Verifying `REGISTER` activates the account and returns a token pair; verifying `RESET_PASSWORD` returns a single-use `resetToken` (15 min) and deliberately no session, since being able to read a mailbox must not by itself log anyone in. The reset flow is `POST /auth/forgot-password` (emails the code to an `ACTIVE` account and nothing else) → `POST /auth/verify-otp` → `POST /auth/reset-password` (spends the token). The response is one flat `VerifyOtpResponse` carrying `purpose` with unused fields omitted, so the register shape is what `/auth/login` returns plus `purpose`. `forgot-password` used to mint the reset token itself and hand it to nobody, which left the flow with no completable path at all.
- **`POST /auth/resend-otp` takes `{email}` only and always returns 200.** The purpose is derived from account state, never from the caller: `PENDING_VERIFICATION` → `REGISTER`, `ACTIVE` → `RESET_PASSWORD`, `SUSPENDED` → nothing — a `REGISTER` code for an active account would mint a session with no password. `OtpService.issue` carries a per-account, per-purpose 60 s cooldown (`otp:cooldown:*`, SET NX EX) and returns `false` when it drops a send; the controller's `@RateLimited` is per client and cannot stop many clients flooding one inbox. The drop is silent to the caller, deliberately — a 429 would confirm the account exists.
- **The OTP expiry travels on `OtpSendEvent`.** The mail states what the issuer actually set; the `app.email.otp-ttl-minutes` property it replaced was documented as cosmetic and had drifted to 10 against a real TTL of 5, so every code looked valid for twice as long as it was.
- **Refresh tokens live in a per-user Redis hash** at `refresh:{userId}` (field = `jti`), not as separate `refresh:{userId}:{jti}` keys. Separate keys force a `KEYS refresh:{userId}:*` scan to end all sessions at once, which blocks the whole Redis instance. Revoke-one is `HDEL`, revoke-all is `DEL`.
- **The feed is every PUBLIC post plus the viewer's own plus friends' FRIENDS posts**, filtered inside the query (OWASP A01), so a viewer with no friends gets a full feed of public posts rather than an empty page. It used to be friends-only. `V3` adds `ix_posts_created_at` for it: the page is now a top-N over the whole table on `created_at`, which `(author_id, created_at)` cannot serve.
- **`feed.fanout` invalidates each friend's cached feed rather than pushing post ids into it.** A pushed id can outlive the viewer's right to see it (unfriending, a visibility change); invalidation re-runs the visibility-filtered query on the next read. Note that `KEY_FEED` is deleted here and written nowhere — there is no feed cache today, so the public-post widening opens no invalidation gap.
- **The feed query is split into `findFeedFirstPage` / `findFeedAfter`.** Postgres cannot infer the type of a parameter compared only against NULL, so a single `:cursor is null or ...` query fails with `could not determine data type`.
- **`notification.dispatch` persists a `Notification` row** and the module exposes `/notifications`. Recording happens in the consumer rather than inline in the feature services, so a notification failure cannot roll back the comment, repost or friend request that raised it. A type this build does not recognise is dropped with a warning rather than retried — redelivery would fail identically forever and only fill the dead-letter queue.
- **Self-notification is suppressed once, in `NotificationServiceImpl.record`**, not at each publisher. Commenting on your own post is not news, and the alternative is the same guard copied into every call site.
- **Reactions have one mutation, `POST /reactions/{targetType}/{targetId}` with `{type}`, and it toggles.** No reaction → added, same type again → removed, different type → changed; the response's `viewerReaction` says where the caller ended up. The former `PUT` (upsert) and `DELETE` were dropped: a one-button client had to track its own state to pick between them, and a stale view could add on top of an existing reaction.
- **`GET /posts/{id}/comments` pages top-level comments and nests each one's replies in `replies`.** Replies are one level deep (enforced on create), so a page is the complete thread for the comments on it; they are fetched for the whole page in one `findByParentCommentIdIn` query. There was no replies route before this — the repository method existed and nothing called it, so a reply could never be read back.
- **`GET /reactions/{targetType}/{targetId}` lists who reacted** — `{user, type, reactedAt}`, newest first, paged, `?type=` narrowing to one reaction for tab-style UIs. Each row carries `friendStatus` (`SELF`, `FRIENDS`, `REQUEST_SENT`, `REQUEST_RECEIVED`, `NONE`; null when anonymous) from `FriendshipService.statusesFor`, one query per page — `DECLINED` and `BLOCKED` both read as `NONE`, since a block must not announce itself to the blocked party. It and `/summary` are public reads in `SecurityConfig`, the same as the post and its comments; `assertTargetReachable` still enforces post visibility, so a reaction is visible to exactly the people the post is (OWASP A01).
- **There is no `REACTION` notification type.** Raising one would require `ReactionService` to resolve a target's owner through `PostService`, reintroducing exactly the cycle that `ReactionSyncConsumer` exists to avoid.
- **`image.process` downscales the stored original in place** rather than writing a second derivative file. Rewriting the object at its existing URL means no schema column and no row update — every `PostImage` that referenced the image still does. Only JPEG and PNG are re-encoded: GIF may be animated and WebP has no ImageIO writer in the JDK, so re-encoding either would flatten or corrupt it, and both are left exactly as uploaded.
- **`otp.send` delivers through `EmailService`.** `EmailConfig` selects `SmtpEmailService` when a `JavaMailSender` exists and `LoggingEmailService` otherwise, resolved through an `ObjectProvider<JavaMailSender>` in one bean method rather than a pair of `@ConditionalOnMissingBean` beans, whose ordering in user configuration is easy to get subtly wrong.
- **Outbound mail is an opt-in profile, `application-mail.properties`, activated as `SPRING_PROFILES_ACTIVE=dev,mail`.** It is the only place outside prod that maps `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD` onto `spring.mail.*`, and it sets `app.email.log-codes=false` so a real send stops printing codes (OWASP A09). The mapping cannot live in `application-dev.properties`: unset, `${MAIL_HOST}` is an unresolvable placeholder, and `${MAIL_HOST:}` is worse — Boot builds a `JavaMailSender` whenever `spring.mail.host` is present **at all**, empty included, selecting SMTP with nowhere to connect. Boot has no "activate on property", so a profile carries the condition instead and is either absent or complete. The dev profile alone keeps `app.email.log-codes=true`, which is what makes registration completable with no mail server.
- **Never hand a container a dotted `spring.mail.*` environment variable.** They work in `.env` only because `spring.config.import` parses it as a properties file. As an environment variable, `spring.mail.host` satisfies `Environment.getProperty`, so `@ConditionalOnProperty` switches `MailSenderAutoConfiguration` on — but `MailProperties` binds through the `Binder`, which reads a `SystemEnvironmentPropertySource` as `SPRING_MAIL_HOST` and finds nothing. The result is a sender with a null host that dead-letters every OTP, which is strictly worse than no mail at all. `docker-compose.yml` therefore forwards the `MAIL_*` names, in the pass-through form.
- **Locally stored files are served by a resource handler in `WebConfig`, registered only for the local driver.** Without it the URLs `LocalStorageService` returns resolve to nothing — the driver stored the bytes but no route ever exposed them. The path comes from `StorageProperties.localFilesPattern()`, derived from `app.storage.public-base-url`, and both `WebConfig` and the permit rule in `SecurityConfig` call it: two hand-written copies of `/files/**` would eventually disagree. A public base URL with no path segment fails at startup rather than mounting the handler at `/**`.
- **`PUT /posts/{id}` has two handlers on one path, split by `consumes`.** JSON takes `content`, `visibility`, `removeImageIds`; multipart takes the same as form fields plus `images` to append. Both call one `PostService.update`, so the image cap, the "text or an image" rule and the owner check exist once. `PostImageResponse` carries `id` for exactly this — `removeImageIds` names images by it, and an id not on this post is rejected as `VALIDATION_FAILED` whether it belongs to another post or to none, so the response cannot say which. Surviving images are renumbered contiguously before new ones are appended.
- **`GET /users/{id}/posts` filters visibility inside the query**, via `PostVisibilityService.visibleVisibilitiesFor`. Paging first and discarding invisible rows afterwards would report page totals that count posts the viewer never receives.
- **Someone else's notification is reported as 404, not 403.** A 403 would confirm that the id exists, turning the endpoint into an enumeration oracle.
- **Order matters in `SecurityConfig`.** `authorizeHttpRequests` matches in declaration order and a single `*` matches one whole path segment, so `/users/me` is listed *before* the public `/users/*` rule. Reversing them silently exposes the own-profile endpoint anonymously.

<!-- spring-structure:start -->
## Project structure (Spring Boot)

Base package `com.cachewraith.blog_post_api_spring`, entry point `BlogPostApiSpringApplication`.
Every new file goes at its address below, under the name below; do not invent sibling packages.

| Package | Classes |
|---|---|
| `config` | `SecurityConfig`, `SwaggerConfig`, `WebConfig`, `RedisConfig`, `AsyncConfig`, `ModelMapperConfig` |
| `common.response` | `ApiResponse`, `ApiErrorResponse`, `PageResponse` |
| `common.exception` | `GlobalExceptionHandler`, `BusinessException`, `ResourceNotFoundException`, `ValidationException`, `ErrorCode` |
| `common.base` | `BaseEntity`, `BaseRepository`, `BaseService`, `BaseController` |
| `common.util` | `DateUtil`, `StringUtil`, `FileUtil` |
| `common.constant` | `AppConstants`, `ApiVersions` |
| `common.annotation` | `CurrentUser`, `RateLimited` |
| `security.jwt` | `JwtProvider`, `JwtFilter`, `JwtProperties` |
| `security.userdetails` | `CustomUserDetailsService` |
| `security.handler` | `AccessDeniedHandlerImpl`, `AuthEntryPointImpl` |
| `security.ratelimit` | `RateLimitService`, `RateLimitInterceptor` |
| `integration.<provider>` | third-party clients — `payment`, `email`, `storage` |
| `scheduler` | one class per job — `CleanupScheduler` |

Inside `modules.<feature>`, for feature `User`:

| Package | Classes |
|---|---|
| `controller.v<N>` | `UserController` — same simple name in every version; the package disambiguates |
| `service` | `UserService` (interface) |
| `service.impl` | `UserServiceImpl` |
| `repository` | `UserRepository` |
| `entity` | `User` |
| `dto.v<N>.request` | `UserCreateRequest`, `UserUpdateRequest` |
| `dto.v<N>.response` | `UserResponse` |
| `mapper.v<N>` | `UserMapper` |
| `validator` | `UserValidator` |

Modules scaffolded here: `post`, `user`, `comment`, `reaction`, `friendship`, `notification`
(full layout) and `auth` (controller + service + dto + entity + repository — it has no
entity of its own beyond the OTP audit trail and reaches user data through `UserService`).
All at `v1`.

Two modules carry more than one controller, which is how a route that hangs off another
resource's path is handled: the module that owns the resource being returned keeps the
controller, and the path decides the class. `post` has `PostController` (`/posts`),
`FeedController` (`/feed`) and `UserPostsController` (`/users/{id}/posts`).

Rules
- Controllers, DTOs and mappers are versioned (`v1`, `v2`); services, repositories and
  entities are not — one service backs every version.
- A module carries only the packages it needs: `auth` is controller + service + dto, no
  entity or repository of its own.
- Controllers accept and return DTOs only. Entities never cross the controller boundary.
- Endpoints return `ApiResponse` / `PageResponse`; failures throw a `BusinessException`
  subclass carrying an `ErrorCode` and surface through `GlobalExceptionHandler`.
- Cross-feature access is service → service, never into another feature's repository.
- New feature → new `modules/<name>` with the full layout. New breaking API shape → new
  `v<N>` of controller + dto + mapper only.

The manifest names the classes the layout expects, not classes that exist today — the tree
is currently empty. Create each one when the work needs it; do not generate stubs ahead of use.
<!-- spring-structure:end -->

<!-- design-patterns:start -->
## Design patterns: choose deliberately

Before writing any non-trivial unit — a new class, module, or a branch point that will
grow — state in one or two lines: the shape of the problem, the candidate patterns, the
one chosen, and why. Not an essay, and not silence either.

Match the problem shape, not the pattern name:

| The problem | Candidates |
|---|---|
| Construction is conditional, or the concrete type varies | Factory Method, Abstract Factory |
| An object needs many optional parts, or must be built step by step | Builder |
| One instance must be shared | container-scoped singleton — never a static global |
| Two incompatible interfaces must meet | Adapter, Bridge |
| Behavior must be added without touching the original | Decorator, Proxy |
| A subsystem needs one simple entry point | Facade |
| Part and whole must be treated alike | Composite |
| One algorithm, several interchangeable variants | Strategy |
| The steps are fixed, the details vary | Template Method |
| Something must react to change elsewhere | Observer, Mediator |
| An action must be queued, logged, or undone | Command, Memento |
| Behavior depends on which state the object is in | State |
| An input passes through ordered, optional handlers | Chain of Responsibility |
| Persistence must be swappable or testable | Repository, Unit of Work |
| A failure path must be explicit, not thrown | Result / Either, Null Object |
| A remote dependency can fail or stall | Circuit Breaker, Retry with backoff |
| Reads and writes have diverging models | CQRS |

Rules
- **The simplest construct that works, wins.** A function, a plain class, a language
  feature, or a `match` beats a pattern. A pattern earns its place when there are already
  two real variants, or a known axis of change — never on one hypothetical future one.
- **Write the language's idiom, not the 1994 diagram.** A first-class function is Strategy
  in most languages; a decorator, a context manager, an enum with behavior, or a
  discriminated union may be the local spelling. Do not build an interface hierarchy the
  language does not need.
- **Match the vocabulary already in this codebase** over introducing a new one. Consistency
  beats a marginally better fit.
- **Name it where it lands** — class or module name, or one line of doc — so the next
  reader sees the pattern without inferring it.
- **Do not retrofit** patterns into working code that nobody asked you to change.
- Say when a pattern was considered and rejected, and why. That is a design decision worth
  one line in the commit body or the PR.
<!-- design-patterns:end -->
