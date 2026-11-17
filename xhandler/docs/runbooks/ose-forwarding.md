# OSE forwarding at risk (1-hour limit)

RS 117-2017 art. 7.5: each CDR-OSE and its document reach SUNAT within 1 hour of issuing the CDR.
SUNAT flags late ones (4196) and the fine is per document (10 UIT). Alerts fire at 45 minutes.

## Look

```bash
curl -s -H "Authorization: Bearer $TOKEN" $URL/api/admin/v1/ose/forwarding | jq
# pending, oldest_pending_seconds, rejected (+ items), late, forwarded
kubectl logs -l app.kubernetes.io/component=worker --since=30m | grep -E "ose.forward|OSE forward"
```

## Causes and actions

1. **No SUNAT credentials** (log: `OSE forwarding has no SUNAT credentials`): set them with
   `POST /api/admin/v1/ose/sunat-credentials {username, password}`. Forwarding resumes on the next run (30 s).
2. **SUNAT's OSE service down or slow** (forwards stay `pending`, `last_error` shows timeouts):
   check the engine's egress (`kubectl logs deploy/egress-proxy`), SUNAT's status, and the static
   egress IPs registered with SUNAT. Nothing is lost: retries continue every ≤ 5 minutes. Tell
   SUNAT's OSE contact if the outage is theirs (keep evidence: the gauge and logs).
3. **Workers down or saturated** (`JobBacklogOld` too): see [workers.md](workers.md). Forwarding runs
   at priority 1, ahead of everything else.
4. **SUNAT rejected forwards** (`rejected` > 0): SUNAT refused a document the OSE accepted. This
   needs a person: read `rejected_items[].error`; fix the cause (our CDR, a validation gap) and
   `POST /api/admin/v1/ose/submissions/{id}/reforward`. Record the incident (ISO 27001).

## After

Late forwards (`late` > 0) are reported to the compliance owner with the list and the cause.
