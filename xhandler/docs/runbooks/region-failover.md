# Region failover

A deliberate operator decision (DESIGN.md §25.1): the remote region's replica is asynchronous,
so the last seconds before the disaster may be missing there.

1. Declare the incident; stop writes to the old region if it's partly alive (scale api and worker
   deployments to 0 there).
2. Promote the cross-region Postgres replica; point the remote overlay's database secret at it.
3. Switch object storage to the replicated bucket (content-addressed: replication is idempotent).
4. Confirm the HSM cluster in the remote region holds the platform keys and the master key.
5. Scale up api and workers in the remote region; move DNS / the gateway.
6. Reconcile: documents emitted in the lost window may exist at SUNAT but not here. Tenants retry
   with their `Idempotency-Key`; SUNAT answers 1033 for numbers it already has, and the
   service's 1033 check (digest comparison) adopts them.
7. OSE: forwards resume from `ose_submission`; report the window to SUNAT's OSE contact.

Drill it twice a year (staging), recording timings (ISO 27001 and OSE availability evidence).
