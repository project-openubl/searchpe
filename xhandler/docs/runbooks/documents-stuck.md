# Documents stuck (past deadline, exception, unknown)

Each document has a SUNAT deadline (DESIGN.md §3.1). `xhandler_documents_past_deadline` counts
documents without a final answer past it; `exception` documents need the tenant (credentials,
data) or the platform; `unknown` ones wait for SUNAT's status.

## Look

```bash
xhandler-admin status                 # workers, queue depth, health
xhandler-admin endpoint-status        # breakers, caps, holds per SUNAT endpoint
```

## Actions

- **An endpoint's breaker is open or held** (`endpoint-status`): SUNAT is failing there; see
  [sunat-outage.md](sunat-outage.md). `xhandler-admin release endpoint <id>` after a manual hold.
- **Exceptions from one tenant's credentials** (codes 0101–0106, 0111, 0154): the tenant was
  notified; nothing to do unless it asks for support. Never resend with other credentials.
- **Documents unknown for long**: the reconciler asks SUNAT (status/CDR) with backoff. If the
  consult service itself is down, they wait; `xhandler-admin retry document <id>` forces an
  attempt after it's back (an ambiguous document is checked, never blindly resent).
- **Stuck in progress after a crash**: `POST /api/admin/v1/repair/requeue-stuck` (idempotent;
  fencing prevents duplicates).
- **Deadline passed anyway**: the tenant was alerted (deadline alerts). Boletas past D+5 go to a
  resumen diario automatically.
