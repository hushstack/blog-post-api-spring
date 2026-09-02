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

**Option B — they pull a prebuilt image.** Nothing to compile on their side:

```bash
# you, once — replace YOURNAME with your Docker Hub account
docker login
APP_IMAGE=YOURNAME/blog-post-api:latest docker compose build
docker push YOURNAME/blog-post-api:latest
```

Send them `docker-compose.yml` and this command:

```bash
APP_IMAGE=YOURNAME/blog-post-api:latest docker compose up -d
```

Either way they get their own database, their own uploads and their own data.
Their copy stores uploads on disk inside a Docker volume and serves them back at
`/files`, so no Cloudflare R2 credentials are involved.

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
