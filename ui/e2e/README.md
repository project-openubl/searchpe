# E2E tests (Playwright)

Specs mirror `client/src/app/pages/` down to the surface folder
(`tests/searchpe/versions/version-list/version-list.spec.ts`); `auth.setup.ts` logs in once
through Keycloak, `tests/public/**` runs with no session (project `public`). Everything runs
against a real stack: nothing is mocked.

## 1. The stack

From the repository root (one level above `ui/`), `hack/ui-e2e-stack.sh` runs everything the
suite needs from built artifacts, the same way CI does (`.github/workflows/ui-e2e.yml`):

```bash
hack/ui-e2e-stack.sh build          # xhandler app and searchpe jars, engine and SUNAT fake binaries
hack/ui-e2e-stack.sh up             # PostgreSQL + Keycloak (compose), then the processes below
hack/ui-e2e-stack.sh down           # stops the processes; `down --compose` also removes the
                                    # compose stack (and with it the database)
```

| Service          | Port | Notes                                                                                        |
| ---------------- | ---- | -------------------------------------------------------------------------------------------- |
| PostgreSQL       | 5432 | `hack/compose.yaml`: one database per service                                                |
| Keycloak         | 8180 | the shared dev realm, with the UI's public client `xhandler-ui`                              |
| SUNAT fake       | 8099 | `sunat-fake`: every company starts on it (environment `fake`)                                |
| engine           | 8090 | `xhandler-engine`, signs and sends                                                           |
| xhandler-service | 8080 | production mode with dev conveniences (software vault, CAPTCHA test mode, `enterprise` plan) |
| searchpe         | 8081 | OIDC and authorization on, advanced search on                                                |

Logs go to `/tmp/xhandler-ui-e2e/*.log` (`STACK_DIR` to change). `COMPOSE=docker compose` if you
use Docker. The database outlives `down`: the suite passes against it run after run.

## 2. The backends by hand

To work on a backend, run it with `quarkus:dev` instead; set what the script sets
(`hack/ui-e2e-stack.sh` is the reference). xhandler-service on 8080 needs the engine and the SUNAT
fake to issue (`(cd xhandler/engine && cargo run)` and `cargo run --bin sunat-fake`), and the
`enterprise` default plan for webhooks:

```bash
podman compose -f hack/compose.yaml up -d postgres keycloak
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/xhandler \
QUARKUS_DATASOURCE_USERNAME=xhandler_login QUARKUS_DATASOURCE_PASSWORD=xhandler_login \
QUARKUS_FLYWAY_USERNAME=xhandler_owner QUARKUS_FLYWAY_PASSWORD=xhandler_owner \
QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
XHANDLER_BILLING_DEFAULT_PLAN=enterprise \
  (cd xhandler && ./mvnw quarkus:dev -pl app)
```

searchpe on 8081, with OIDC and authorization on (its defaults turn both off):

```bash
QUARKUS_HTTP_PORT=8081 \
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/searchpe \
QUARKUS_DATASOURCE_USERNAME=searchpe QUARKUS_DATASOURCE_PASSWORD=searchpe \
QUARKUS_OIDC_TENANT_ENABLED=true SEARCHPE_DISABLE_AUTHORIZATION=false \
SEARCHPE_ALLOW_ADVANCEDSEARCH=true \
QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
QUARKUS_OIDC_CLIENT_ID=searchpe QUARKUS_OIDC_CREDENTIALS_SECRET=secret \
  ./mvnw quarkus:dev -pl searchpe
```

Without searchpe, run with `E2E_SEARCHPE=false`: its specs skip and the UI hides its section.

## 3. Run

```bash
cd ui
cp .env.example .env            # XHANDLER_API_URL, SEARCHPE_API_URL, OIDC_* for the dev server
npx playwright install chromium # once, unless E2E_BROWSER_CHANNEL=chrome uses Google Chrome
npm run test:e2e                # starts `npm run start:dev` on :3000 unless one is running
npm run test:ui -w e2e          # Playwright UI mode
```

| Variable                    | Default                 | Meaning                                                       |
| --------------------------- | ----------------------- | ------------------------------------------------------------- |
| `E2E_BASE_URL`              | `http://localhost:3000` | A UI already running (then no dev server is started)          |
| `E2E_USERNAME`              | `olga`                  | Dev-realm user; olga holds every searchpe permission          |
| `E2E_PASSWORD`              | `olga`                  |                                                               |
| `E2E_SEARCHPE`              | `true`                  | `false` when the stack runs without searchpe                  |
| `E2E_SEARCHPE_IMPORT`       | `false`                 | `true` to create a padrón version (downloads SUNAT's padrón)  |
| `E2E_BROWSER_CHANNEL`       | (Playwright's Chromium) | `chrome` to use the installed Google Chrome                   |
| `E2E_PUBLIC_DOCUMENT_TOKEN` | (the setup's document)  | A buyer link's token (`access_url`) for the buyer-portal spec |

`auth.setup.ts` logs in once and prepares, through the xhandler API, what the organization specs
read, so they run instead of skipping on a fresh stack (`ensureFixtures` in
`support/helpers/tenants.ts`): an organization (it signs the e2e user up), a company on the SUNAT
fake, a factura the company issues to itself and receives back (the documents and receptions
lists, and the buyer link for `tests/public/`), a webhook and a partner (the dev realm's olga is a
platform admin). It writes their ids to `.auth/fixtures.json`; the `public` project depends on
the setup for the buyer link, while its browser still has no session.

On this stack the whole suite runs; the only skip is the padrón import (`E2E_SEARCHPE_IMPORT`).
