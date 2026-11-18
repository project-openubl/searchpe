# E2E tests (Playwright)

Specs mirror `client/src/app/pages/` down to the surface folder
(`tests/searchpe/versions/version-list/version-list.spec.ts`); `auth.setup.ts` logs in once
through Keycloak, `tests/public/**` runs with no session (project `public`). Everything runs
against a real stack: nothing is mocked.

## 1. The stack

From the repository root (one level above `ui/`):

```bash
# PostgreSQL (one database per service) and Keycloak with the dev realm, on 5432 and 8180.
podman compose -f hack/compose.yaml up -d postgres keycloak   # Grafana (lgtm) is on 3001 if you want it
```

The shared dev realm (`xhandler/deploy/keycloak/xhandler-realm-dev.json`) includes the UI's public
client, `xhandler-ui` (PKCE, with audience mappers for `xhandler-service` and `searchpe`).

## 2. The backends

xhandler-service on 8080 (see `xhandler/README.md`; the engine sidecar is only needed to emit):

```bash
(cd xhandler && ./mvnw -q -pl app package -DskipTests)
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/xhandler \
QUARKUS_DATASOURCE_USERNAME=xhandler_login QUARKUS_DATASOURCE_PASSWORD=xhandler_login \
QUARKUS_FLYWAY_USERNAME=xhandler_owner QUARKUS_FLYWAY_PASSWORD=xhandler_owner \
QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
  (cd xhandler && ./mvnw quarkus:dev -pl app)
```

searchpe on 8081, with OIDC and authorization on (its defaults turn both off):

```bash
QUARKUS_HTTP_PORT=8081 \
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/searchpe \
QUARKUS_DATASOURCE_USERNAME=searchpe QUARKUS_DATASOURCE_PASSWORD=searchpe \
QUARKUS_OIDC_TENANT_ENABLED=true SEARCHPE_DISABLE_AUTHORIZATION=false \
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
| `E2E_PUBLIC_DOCUMENT_TOKEN` | (none)                  | A buyer link's token (`access_url`) for the buyer-portal spec |

`auth.setup.ts` signs the e2e user up for an organization (`POST /api/v1/tenants`) when they
belong to none, so the organization specs run on a fresh stack.
