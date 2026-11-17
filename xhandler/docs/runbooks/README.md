# Runbooks

Every alert in `deploy/monitoring/prometheusrule.yaml` links to one of these (DESIGN.md §18, §26.4).
They assume `xhandler-admin` (the admin CLI, `admin-cli/`) configured for the environment, and
`kubectl` access to the namespace. Platform operators never read tenant data outside an approved
support session (§26.1); the admin API shows identifiers and states, not content.

| Runbook | When |
|---|---|
| [ose-forwarding.md](ose-forwarding.md) | `OseForwardingSlaAtRisk`, `OseForwardingLate`, `OseIntakeErrors` |
| [documents-stuck.md](documents-stuck.md) | `DocumentsPastDeadline`, `DocumentsNeedAttention` |
| [sunat-outage.md](sunat-outage.md) | `SunatBacklogGrowing`, SUNAT announces maintenance |
| [workers.md](workers.md) | `JobBacklogOld`, `ApiErrorRate` |
| [database-failover.md](database-failover.md) | `DatabaseGaugesMissing`, planned switchover, restore tests |
| [hsm.md](hsm.md) | HSM restart, signing failures, key rotation |
| [region-failover.md](region-failover.md) | Losing the primary region |
| [pse-rejected-rate.md](pse-rejected-rate.md) | `PseRejectedRateHigh` |
| [breach.md](breach.md) | A suspected personal-data breach |
| [data-protection.md](data-protection.md) | Data-subject requests, tenant exit, retention, tax-audit holds |
| [load-tests.md](load-tests.md) | Before GA and after capacity changes |
