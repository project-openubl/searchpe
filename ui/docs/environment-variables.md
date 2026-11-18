# Environment variables

Defined in `common/src/environment.ts` (`OpenublEnvType`). The Express server and the Vite dev
server read them from the process (`npm run start:dev` / `npm run start` load `ui/.env` with
dotenv, see `.env.example`); the browser gets base64-encoded in `window._env` only the keys
allowlisted in `CLIENT_ENV_KEYS`; anything else stays on the server.

## Runtime (container)

| Variable                  | Required | Default                                 | Meaning                                                                                                                  |
| ------------------------- | -------- | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `XHANDLER_API_URL`        | yes      | `http://localhost:8080` (dev)           | xhandler-service; the `/api/xhandler` proxy target (server only)                                                         |
| `SEARCHPE_API_URL`        | no       | unset                                   | searchpe; the `/api/searchpe` proxy target. Unset hides searchpe (server only)                                           |
| `OIDC_SERVER_URL`         | yes      | `http://localhost:8180/realms/xhandler` | The Keycloak realm (issuer URL)                                                                                          |
| `OIDC_CLIENT_ID`          | no       | `xhandler-ui`                           | The UI's public client (authorization code + PKCE)                                                                       |
| `OIDC_SCOPE`              | no       | `openid`                                | Scopes requested at login                                                                                                |
| `PORT`                    | no       | `8080`                                  | The Express server's port (server only)                                                                                  |
| `VERSION`                 | no       | `99.0.0`                                | Shown in the About dialog; set by the image build                                                                        |
| `DEBUG`                   | no       | unset                                   | `1` logs the env and every proxied request                                                                               |
| `NODE_EXTRA_CA_CERTS`     | no       | unset                                   | Extra CAs for the backends (a mounted file, only read); see below                                                        |
| `LIBRO_RECLAMACIONES_URL` | no       | unset                                   | The platform's virtual Libro de Reclamaciones (https, Ley 32495), linked from every shell; unset (or not https) hides it |

Derived: `SEARCHPE_ENABLED` (`"true"` when `SEARCHPE_API_URL` is set, so when the server
mounts searchpe's proxy) is what the browser sees; it never sees a backend URL.

`entrypoint.sh` builds `/tmp/ca-bundle.pem` (`$TMPDIR` if set) from the system bundle,
OpenShift's service-serving CA (if mounted) and the file at `NODE_EXTRA_CA_CERTS` (if set and
readable), then points `NODE_EXTRA_CA_CERTS` at it. It never writes to the mounted file; with a
read-only root filesystem, mount an `emptyDir` at `/tmp`.

The server answers `GET /healthz` with `200 {"status":"UP"}` for HTTP probes; it doesn't check
the backends.

## Build time

| Variable   | Default      | Meaning                                                            |
| ---------- | ------------ | ------------------------------------------------------------------ |
| `BRANDING` | `./branding` | Folder with `strings.json`, `manifest.json`, `favicon.ico`, images |
| `BASE_URL` | `/`          | Vite `base` when the UI is served under a path                     |

## Tests

| Variable              | Meaning                                                                  |
| --------------------- | ------------------------------------------------------------------------ |
| `CI`                  | Vitest uses Playwright's Chromium instead of the installed Google Chrome |
| `CHOKIDAR_USEPOLLING` | `1` for `vitest --watch` on machines out of inotify instances            |
| `E2E_*`               | See [e2e/README.md](../e2e/README.md)                                    |
