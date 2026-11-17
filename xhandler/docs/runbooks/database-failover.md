# Database failover, switchover and restore tests

CloudNativePG with synchronous replication (DESIGN.md §25.1, `deploy/examples/cnpg-cluster.yaml`):
a commit is on two zones before the app answers, so a failover loses nothing.

## Unplanned failover

Automatic. The app retries connections; workers whose lock connection broke exit and restart
(§10.4); recovery and the reconciler resume interrupted attempts with fencing, so nothing is lost
or sent twice. Check afterwards:

```bash
kubectl cnpg status xhandler-db
xhandler-admin status
xhandler-admin migration-status
```

If `DatabaseGaugesMissing` persists, the app can't reach the new primary: check the `-rw`
service, NetworkPolicies and credentials.

## Planned switchover

`kubectl cnpg promote xhandler-db <standby>` during low traffic; same checks.

## Restore test (weekly, evidence for ISO 27001)

1. Restore the latest backup to a new cluster (`bootstrap.recovery` from the object store) at a
   point in time.
2. Point a throwaway deployment of the service (or `psql`) at it: check `flyway_schema_history` matches
   production's, and count documents per day for the last week against production's numbers.
3. Record the result and the duration (RTO evidence), then delete the restored cluster.
