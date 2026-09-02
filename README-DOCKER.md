# Running the API with Docker

The whole stack — the app plus Postgres, Redis and RabbitMQ — comes up with one
command. Nothing needs to be installed except Docker: no Java, no Maven, no
database. Flyway builds the schema on first start.

## Run it

```bash
docker compose up -d          # first run also builds the image
docker compose logs -f app    # watch it start
```

Then open **http://localhost:8080/swagger-ui.html**.

| URL | What it is |
|---|---|
| http://localhost:8080/swagger-ui.html | API docs, and the place to test endpoints |
| http://localhost:8080/actuator/health | `{"status":"UP"}` once everything is ready |
| http://localhost:15672 | RabbitMQ management UI (`guest` / `guest`) |

Stop it with `docker compose down`. Add `-v` to also delete the database,
uploads and queues — without `-v` your data survives a restart.

## Getting a token

Most endpoints need one. In a fresh copy no mail server is configured, so the
verification code is printed to the log instead of being emailed:

```bash
# 1. register (or use POST /auth/register in Swagger UI)
curl -s localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","username":"me","password":"Str0ng!Passw0rd","fullName":"Me"}'

# 2. read the six-digit code out of the log
docker compose logs app | grep "Local-only REGISTER OTP"

# 3. exchange it for tokens
curl -s localhost:8080/api/v1/auth/verify-otp -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","code":"123456"}'
```

Paste the `accessToken` into the green **Authorize** button in Swagger UI.

## Sending it to someone else

They need Docker and two files — `docker-compose.yml` and (only if they build it
themselves) the repository. They do **not** need your `.env`; it holds your R2
keys and mail password and must never be shared.

**Option A — they build from the source.** Simplest if they already have the repo:

```bash
git clone <your-repo-url> && cd blog-post-api-spring
docker compose up -d
```

**Option B — they pull a prebuilt image.** Nothing to compile on their side.
Published to GHCR, which needs no account beyond the GitHub one that already
owns this repository:

```bash
# you, once. The default gh token has no package scope, hence the refresh.
gh auth refresh -h github.com -s write:packages
gh auth token | docker login ghcr.io -u YOURNAME --password-stdin

docker compose build
docker tag blog-post-api:latest ghcr.io/YOURNAME/blog-post-api:latest
docker push ghcr.io/YOURNAME/blog-post-api:latest
```

GHCR packages are **private by default**, and a private one fails their pull with
`denied`. Make it public once, under your GitHub profile → Packages →
`blog-post-api` → Package settings → Change visibility.

Send them `docker-compose.yml` and this command:

```bash
APP_IMAGE=ghcr.io/YOURNAME/blog-post-api:latest docker compose up -d
```

Either way they get their own database, their own uploads and their own data.
Their copy stores uploads on disk inside a Docker volume and serves them back at
`/files`, so no Cloudflare R2 credentials are involved.

## Using it from a Next.js frontend

CORS is already configured for `http://localhost:3000`, the Next.js default, so
`fetch` from the browser works with no change. Only `Authorization` and
`Content-Type` are accepted as request headers, and credentials are allowed.

```ts
const res = await fetch("http://localhost:8080/api/v1/users/me", {
  headers: { Authorization: `Bearer ${accessToken}` },
});
const { success, data } = await res.json();
```

Every response is enveloped: `{success, data, timestamp}` on success, and
`{success: false, code, message, fieldErrors, path, timestamp}` on failure, where
`code` is a stable machine-readable name. Error responses carry the CORS headers
too, so the body is readable rather than opaque.

Four things that bite:

- **Next.js on a port other than 3000** — set the origin explicitly, or the
  browser blocks every call: `CORS_ALLOWED_ORIGINS=http://localhost:3001 docker
  compose up -d`. The value is a comma-separated list, so several may be listed.
- **`next/image` refuses unconfigured hosts.** Avatar and post image URLs come
  back absolute, pointing at wherever the storage driver put them, and the
  component rejects any host not declared. In `next.config.ts`:

  ```ts
  images: {
    remotePatterns: [
      // local driver — the default in this compose stack
      { protocol: "http", hostname: "localhost", port: "8080", pathname: "/files/**" },
      // r2 driver — the bucket's public host
      { protocol: "https", hostname: "*.r2.dev", pathname: "/**" },
    ],
  }
  ```

  A plain `<img>` needs none of this; it is `next/image` that is strict.
- **Server Components and route handlers never hit CORS**, because the request
  comes from Node rather than a browser — but the URL still has to resolve from
  wherever that process runs. If the frontend is itself in a container,
  `localhost:8080` is that container, not the API: use `host.docker.internal:8080`,
  or put both on one compose network and use the service name `app:8080`.
- **Multipart uploads: do not set `Content-Type` yourself.** Pass the `FormData`
  as the body and let the browser write the header — setting it by hand omits the
  multipart boundary and the request fails.

  ```ts
  const body = new FormData();
  body.append("file", file);
  await fetch("http://localhost:8080/api/v1/users/me/avatar", {
    method: "PUT",
    headers: { Authorization: `Bearer ${accessToken}` }, // no Content-Type
    body,
  });
  ```

The full endpoint list, request shapes and response schemas are in Swagger UI at
**http://localhost:8080/swagger-ui.html**, and the raw OpenAPI document at
`/v3/api-docs` — which most TypeScript client generators will take directly.

## Which address does it answer on?

`8080` is the **port**; what changes is the address in front of it.

- **Their own copy (this setup):** `http://localhost:8080` on their machine.
  Nothing to configure — this is why running their own copy is the easy path.
- **Reaching *your* running copy from another laptop on the same Wi-Fi:** they
  use `http://<your-LAN-IP>:8080`. Find yours with `ip addr | grep 'inet 192'`
  — something like `192.168.1.42`. Your firewall has to allow port 8080, and
  the address changes when you move networks. Your machine must stay on.
- **Reaching your copy over the internet:** your home router hides you behind
  NAT, so there is no address to hand out. It takes a tunnel:
  `cloudflared tunnel --url http://localhost:8080` prints an `https://…` URL
  that works from anywhere.

Before doing either of the last two, change `JWT_SECRET` in `.env` to a real
value — `openssl rand -base64 48`. The dev fallback is published in this
repository, and anyone who knows it can mint a valid token for any account.

## Configuration

Every value has a working default, so `docker compose up` needs no `.env` at
all. Where a `.env` exists, Compose reads it and those values win — that is how
a Cloudflare R2 bucket and a real SMTP host get picked up without ever being
built into the image. `.env.example` is the template; `.env` is git-ignored and
excluded from the Docker build context.

| Variable | Default | Notes |
|---|---|---|
| `APP_PORT` | `8080` | Host port. Change if 8080 is taken. |
| `APP_IMAGE` | `blog-post-api:latest` | Set to `you/blog-post-api:tag` to push. |
| `FILESYSTEM_DISK` | `local` | `r2` switches uploads to Cloudflare R2. |
| `JWT_SECRET` | dev fallback | Must be 32+ bytes. Change before exposing. |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` requires real `MAIL_*` and R2 values. |

## Troubleshooting

- **`port is already allocated`** — something else already holds that port, most
  often a natively installed Postgres, Redis or RabbitMQ. Only two ports are
  published, and both can be moved:
  `APP_PORT=8081 RABBITMQ_UI_PORT=15673 docker compose up -d`.
  (Postgres and Redis are deliberately *not* published — the app reaches them
  over the compose network, so they cannot clash with a local install.)
- **App restarts in a loop** — read `docker compose logs app`. A missing R2
  value while `FILESYSTEM_DISK=r2` stops startup deliberately, naming the
  property it wanted.
- **Schema errors on start** — the database volume predates a migration. Wipe
  it with `docker compose down -v` and start again.
- **Rebuild after changing code** — `docker compose up -d --build`.
