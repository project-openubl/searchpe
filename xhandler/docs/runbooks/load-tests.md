# Load tests (DESIGN.md §25)

Run in staging against the SUNAT fake (the repository root's `hack/compose.yaml` profile `fake`, or its
deployment), never against SUNAT beta or production. Scripts are in `hack/load/` (k6).

## 1. Sustained emit

```bash
k6 run -e URL=$URL -e TOKEN=$TOKEN -e TENANT=$T -e COMPANY=$C -e RATE=85 -e DURATION=1h hack/load/emit.js
```

Pass: p99 emit < 1.5 s, > 99.9% 201s, `xhandler_documents_pending` flat (SUNAT keeps up),
`xhandler_jobs_oldest_waiting_seconds` < 60.

## 2. Burst

Bursts of 5,000 documents/minute: `RATE=84 DURATION=10m`, then `RATE=300 DURATION=2m`, then
`RATE=84` again. Pass: no 5xx other than 503 `busy` (the designed back-pressure, retried by the
client), and the backlog drains after the burst.

## 3. Outage and drain

1. Start `emit.js` at `RATE=83` for 6 hours.
2. After 30 minutes, put the fake in outage: `curl -X POST $FAKE/fake/mode -d '{"mode":"outage"}' -H 'Content-Type: application/json'`.
3. After 4 hours, back to normal: `-d '{"mode":"normal"}'`.
4. Pass: emits never fail during the outage; breakers open and probe; after recovery the backlog
   (~1.2M at peak figures) drains earliest deadline first in about 3 hours; no document is sent
   twice (fake's `GET /fake/received` count = documents accepted) and none is lost.

## 4. OSE intake and forwarding

```bash
./hack/load/make-ose-docs.sh $ISSUER_RUC 20000 /tmp/ose-docs
k6 run -e URL=$URL -e OSE_USER=$U -e OSE_PASSWORD=$P -e DOCS=/tmp/ose-docs -e COUNT=20000 -e VUS=50 hack/load/ose-intake.js
```

Pass: ≥ 99.96% CDR-OSE answers, p99 < 2 s, `xhandler_ose_forward_oldest_seconds` < 600 during the
run and back to 0 after, `late` = 0 in `GET /api/admin/v1/ose/forwarding`.

## 5. Failover under load (chaos, §25.1)

While test 1 runs: delete the Postgres primary pod, a worker pod, and an api pod; restart the HSM
(staging). Pass: no lost or duplicated documents (compare the fake's received count with accepted
documents), recovery within a minute.

Record results and dates: they are ISO 27001 and OSE availability evidence.
