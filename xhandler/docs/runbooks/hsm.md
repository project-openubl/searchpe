# HSM restart, signing failures, key rotation

Tenant keys are wrapped in Postgres and unwrapped only inside the HSM; platform keys (PSE, OSE
CDR) are HSM objects (DESIGN.md §6).

- **HSM restart / cluster member lost**: signing calls fail fast and emits answer 503 `busy`;
  documents already accepted keep flowing (sends don't sign). Sessions are re-opened on the next
  call. Confirm with a test emit in the staging tenant.
- **PIN or module misconfigured** after a deploy: the pods log PKCS#11 errors at start; fix the
  `xhandler-hsm` secret / module volume in the overlay.
- **Master key rotation**: `xhandler-admin rotate-keys` re-wraps every secret, tenant key,
  platform credential and platform key with the current master key. Run it after adding the new
  master key, before retiring the old one.
- **Platform certificate expiring** (alert 30 days before): import the new PSE or OSE CDR
  certificate (`POST /api/admin/v1/pse/certificate`, `POST /api/admin/v1/ose/cdr-certificate`) and
  register it in SOL first.
