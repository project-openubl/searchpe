# SUNAT outage or throttling

The service keeps accepting documents (emit is local) and drains the backlog, earliest deadline
first, when SUNAT is back (DESIGN.md §9, §24, §25: ~3 hours for a 4-hour outage at peak).

## Look

```bash
xhandler-admin endpoint-status
kubectl logs -l app.kubernetes.io/component=worker --since=15m | grep -i breaker
```

## Actions

- Breakers open and close by themselves (half-open probes). Don't release them by hand while
  SUNAT is still failing.
- **Planned maintenance** announced by SUNAT: hold the affected endpoints beforehand
  (`xhandler-admin hold endpoint <id>`) and release after; tenants see "pendiente".
- **Throttling** (HTTP 429/503 bursts, IP blocks): lower the endpoint's cap
  (`xhandler-admin endpoint-status --cap <id> <n>`), then raise it back step by step.
- **During the drain**: watch `xhandler_documents_past_deadline`; boletas past D+5 go by resumen.
- Post-incident: record the outage window (evidence for tenants' late documents).
