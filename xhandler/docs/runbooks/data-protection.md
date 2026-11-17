# Data protection workflows

## Data-subject requests (access, rectification, cancellation, opposition)

The tenant is the controller. A request that reaches the platform (email, support) is registered
and forwarded to the tenant at once (our duty: within 3 days):

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  $URL/api/admin/v1/data-protection/tenants/$TENANT/data-subject-requests \
  -d '{"subject_doc_type":"1","subject_doc_number":"12345678","kind":"access","details":"recibido por correo"}'
```

The tenant sees it (`GET /api/v1/tenants/{t}/data-subject-requests`), gets what the platform
holds about the person (`…/{id}/personal-data`) and closes it with its answer (`…/{id}/close`).
Terms: 20 days for access, 10 for the others; the tenant is reminded 2 days before, and overdue
requests are logged for the platform. Electronic documents can't be erased while tax law requires
keeping them; cancellation is answered on that basis by the tenant.

## Tenant exit

1. The tenant exports everything: `POST /api/v1/tenants/{t}/exports` → parts (one ZIP per issue
   month with XML, CDR, PDF and JSON, plus an index).
2. It leaves: `POST /api/v1/tenants/{t}/exit {confirm_name}` (OWNER; needs an export from the last
   30 days). The tenant is suspended; deletion is scheduled 2 years later.
3. The daily `tenants.exit-deletion` job deletes files and rows and records the evidence in
   `tenant_deletion` (counts per table, files deleted).
4. Exceptions: `PUT /api/admin/v1/data-protection/tenants/{t}/archive-plan {"archive_plan": true}` keeps it;
   `PUT …/audit-hold {"audit_hold": true}` blocks any deletion during a tax audit;
   `POST …/exit/cancel` brings it back before the deletion.

## Retention

The daily `retention.purge` job deletes documents older than the tenant's `retention_years`
(full years after the issue year), never under an audit hold. Archive tiers for old files are an
object-storage lifecycle rule (infrastructure).
