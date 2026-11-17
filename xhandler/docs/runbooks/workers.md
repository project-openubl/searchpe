# Workers behind (job backlog) or API errors

## Look

```bash
xhandler-admin status
kubectl get pods -l app.kubernetes.io/name=xhandler-service -o wide
kubectl top pods -l app.kubernetes.io/component=worker
```

## Actions

- **`ops.snapshot` stopped** (gauges NaN, `DatabaseGaugesMissing`): the snapshot is a scheduled job;
  check the workers first.
- **Workers crash-looping**: `kubectl logs --previous`. A lost lock connection makes a worker exit
  on purpose (§10.4) and Kubernetes restarts it; repeated exits point at the database (see
  [database-failover.md](database-failover.md)).
- **Saturated**: scale the worker deployment (`kubectl scale deploy/xhandler-worker --replicas=N`).
  Job slots per pod are `xhandler.jobs.slots`; SUNAT concurrency is capped per credential, so more
  workers don't hammer SUNAT.
- **One job type piling up**: `GET /api/admin/v1/jobs/{id}` on a sample; cancel a runaway job with
  `xhandler-admin cancel-job <id>`.
- **API 5xx**: check the engine sidecar (`kubectl logs <pod> -c engine`), the HSM (signing
  errors: [hsm.md](hsm.md)) and the database.
