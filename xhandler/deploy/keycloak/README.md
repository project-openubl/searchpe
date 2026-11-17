`xhandler-realm-dev.json`: the Keycloak realm for development and tests (Dev Services and
the repository root's `hack/compose.yaml`). Users `alice`, `bob`, `dave` (email not verified) and `carol`
(platform-support), with their name as password; machine client `acme-erp` / `acme-secret`.
Never import it into a real environment.

Every Keycloak realm that calls xhandler-service (dev or real) needs, on each client:

- an **audience mapper** adding `xhandler-service` to access tokens (the API checks `aud`);
- for machine clients (service accounts), the **`client_id` user-session-note mapper**, as in
  Keycloak's built-in `service_account` scope. The API treats a token as a machine client only
  when `client_id` = `azp` and the username is `service-account-<azp>`.
