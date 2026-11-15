# xhandler-service — Design

Multi-tenant SaaS for SUNAT (Peru) electronic documents: create, sign, validate, send,
track, deliver and report them. Client systems (ERP, POS, xhandler-ui, later
xhandler-mobile) use a REST API. The fiscal core is
[xhandler-rust](https://github.com/project-openubl/xhandler-rust); everything else is a
Quarkus application.

License: Apache-2.0, developed in a **private** GitHub repository (project-openubl/xhandler-service). Status: design reviewed twice; no code yet (see §21).

Inputs to this design:

- the requirement rounds held on 2026-11-13/14;
- [xhandler-mobile's design](https://github.com/project-openubl/xhandler-mobile), for the fiscal domain;
- [pulpcore](https://github.com/pulp/pulpcore), for platform patterns;
- [pulp-ui-2](https://github.com/pulp/pulp-ui-2), for the UI;
- vendor research (Nubefact, Bizlinks, APIsPERU);
- the regulatory research in [research/](research/README.md).

---

## 1. Scope

### 1.1 Roles

The service operates in three roles from v1:

| Role | Who signs | Who submits | Legal basis |
|---|---|---|---|
| **Software** (SEE-Del contribuyente) | Tenant's own certificate | Tenant's SOL user, to SUNAT or to the tenant's OSE | RS 097-2012 |
| **PSE** (Proveedor de Servicios Electrónicos) | Platform's PSE certificate | Platform's own SOL user; the tenant authorizes the platform in SOL | RS 199-2015, RS 108-2022 |
| **OSE** (Operador de Servicios Electrónicos) | Issuer signs the document; the platform signs the CDR | Platform forwards document + CDR to SUNAT within 1 hour | RS 117-2017 and amendments |

### 1.2 Customers and accounts

- Customers:
  - SMEs through their ERP/POS;
  - large enterprises;
  - developers and startups (free tier);
  - software vendors reselling the service.
- Account hierarchy: **Partner → Tenant → Company (RUC)**.
  - A partner manages its tenants through the API.
  - A tenant owns one or more companies.

### 1.3 In v1

- Documents: everything xbuilder supports.
  - factura, boleta;
  - nota de crédito, nota de débito;
  - guía de remisión (GRE);
  - percepción, retención;
  - resumen diario (RC), comunicación de baja (RA), resumen de reversiones (RR).
- Inputs:
  - native xbuilder JSON;
  - adapters for UBL XML upload, SFS-style TXT and Nubefact-compatible JSON;
  - OSE inbound over SOAP, REST and SFTP.
- Supplier invoice reception, uploaded through the API.
- PDF and email delivery; webhooks.
- Optional catalogs: customers (with RUC/DNI lookup), products, vehicles, drivers, addresses.
- SIRE automation; factura negociable conformity tracking; CSV/Excel reports.
- Billing through Lago, in a separate pod.
- UI in xhandler-ui, a separate container (§18).

### 1.4 Not in v1

- Contingency (paper) documents.
- WhatsApp delivery.
- Selling RUC/DNI or exchange-rate data APIs as products.
- CAVALI integration.
- Mobile sync (the schema is ready for it, §7.2).
- Redis in xhandler-service (§10.9). Lago, a separate product, runs its own Redis and Postgres (§14).

---

## 2. Decisions

| Topic | Decision |
|---|---|
| Application stack | Java 25, Maven, Quarkus: Hibernate/Panache, Flyway, OIDC, Micrometer, OpenTelemetry, SmallRye OpenAPI, Scheduler, Qute, Mailer, Dev Services |
| Fiscal core | xhandler-rust, run as a stateless Rust sidecar ("engine") reached over localhost. The fallback is Panama FFM behind the same Java interface. |
| Rejected options | Go: viable, but it means rewriting xsender. A Rust web stack: too much plumbing. Chicory/wasm: no networking, so xsender would be duplicated. |
| Tenancy | One shared schema; `tenant_id` on every table, enforced by Hibernate's discriminator plus Postgres row-level security |
| Identity | Keycloak OIDC for people (roles, 2FA) and machine clients (OAuth2 client credentials); public self-service signup |
| Keys | PKCS#11 HSM. The platform PSE and OSE keys are HSM objects. Tenant keys are wrapped under an HSM master key and unwrapped only inside the HSM to sign. |
| Other secrets | Envelope encryption in Postgres; several master keys active at once; a rotation command |
| Async work | A Postgres job system modeled on pulpcore's tasking (exclusive resources, FIFO per resource, heartbeats on the lock connection, LISTEN/NOTIFY). Retry state lives on the document; a singleton reconciler with leases is the only creator of SUNAT attempts (§24). No Kafka, no Redis. |
| Files | S3-compatible storage, content-addressed by sha256 and immutable |
| Hosting | Cloud-agnostic Kubernetes/OpenShift. The same image runs as `api` and `worker` deployments, each with the engine sidecar. |
| Availability | Lose nothing (RPO = 0) and no noticeable downtime (seconds of automatic failover), delivered by the infrastructure layer (Kubernetes + cloud, a separate project). The application is built to be failover-safe (§25.1). 99.96% for the OSE role. ISO 27001 controls from day one. |
| License and repository | Apache-2.0; the code lives in a private GitHub repository |
| PDF | Qute HTML templates rendered with OpenHTMLtoPDF |
| Email | Platform default sender, or the tenant's own SMTP |
| Numbering | Assigned by the service at emit |
| Emit | Build, number, validate and sign inside the request, reply `201 pending_send`, send asynchronously |
| Boletas | Sent individually up to D+5. A boleta still unsent after D+5 moves automatically into a resumen diario (RC, estado 1) before D+7. RC is also used for voiding. |
| Billing | Lago (separate pod). The service emits usage events and enforces entitlements. |
| UI | xhandler-ui (separate container) built on the pulp-ui-2 blueprint |
| Retention | 10 years by default (configurable). Full export when a tenant leaves; deleted 2 years later unless the tenant buys an archive plan. |
| Operating company | A Peruvian legal entity in the general regime (required for PSE/OSE registration). It invoices its own subscriptions through xhandler-service. |

---

## 3. Legal constraints that shape the system

Sources: [research/](research/README.md). Values are the rules in force on 2026-11-13.

### 3.1 Deadlines

D is the issue date; days are calendar days.

| Document | Send deadline | Voiding | Source |
|---|---|---|---|
| Factura, and notes referring to it | D+3. Sent later, it is not a factura. | RA within 7 days. Before 2027 this counts from receipt of the CDR (needs `cdr_received_at`); from 2027-01-01, from when the RA is generated. | RS 000003-2023; RS 000048-2026; RS 000143-2026 |
| Boleta, and its notes (sent individually) | D+5. After that, only by resumen diario, which the service builds automatically (§9.2). | RC line with estado 3 within 7 days | RS 097-2012 art. 12 |
| Resumen diario | D+7 | — | RS 114-2019 |
| GRE (remitente/transportista) | Before transport starts. Always sent direct to SUNAT, never through an OSE. | Only in SOL: before transport starts, or during transport if the destinatario changes | RS 000123-2022 |
| Percepción / retención | D+7 (OSE text; the SEE-Del text in RS 274-2015 is unverified) | RR within 7 days of telling the provider/client about the reversal (needs a recorded `reversal_communicated_at`) | RS 117-2017 arts. 36, 38 |
| OSE forwarding to SUNAT | Within 1 hour of issuing the CDR | — | RS 117-2017 art. 7.5 |
| Factura negociable conformity | Buyer has 8 days | — | DU 013-2020; RS 000165-2021 |

Other rules:

- A document whose issue date is more than 2 days after the send date is rejected (code 2329).
- A boleta buyer must be identified above S/ 700 (validator code 2514).
- Only documents not yet delivered to the buyer can be voided. Otherwise a nota de crédito is required.
- From 2027-01-01 a credit or debit note may refer to exactly one document.
- No code ships before 2027-01-01, so the service implements only the 2027 rules. It has no dated feature flags.
- **Time zone.** Every legal date — D, deadlines, the 2329 future-date check, the `daily_counter` day — is computed in `America/Lima`. Timestamps are stored in UTC. The document stores D, the first send time and the CDR receipt time separately.
- **Currency.** For documents in foreign currency, the S/ 700 boleta check uses the PEN equivalent at the document's `tipo_cambio`. If the document carries none, the SBS rate for D is used.

### 3.2 Roles

- **Submission route.** A company using an OSE cannot also send directly to SUNAT; only SEE-SOL stays available (RS 117-2017 art. 12.2). Principal taxpayers with 300 UIT or more of income must use an OSE or SEE-SOL. So each company's route is a **regulatory status the service derives and enforces**, not a free toggle. GRE is always direct.
- **PSE (RS 199-2015 art. 3, 5, 7).**
  - The PSE signs with its own certificate and its own SOL user; the issuer stays liable.
  - Registration needs ISO 27001, capital of 150 UIT or more, and at least 5 employees.
  - Rejected CDRs must stay at or under 10% in the first year, then 5%, measured every six months.
- **OSE (RS 117-2017 arts. 5–8, annexes A and C).**
  - Registration needs 300 UIT of capital, a 28 UIT bank guarantee, an audit by an ISO lead auditor, and SUNAT's 25-business-day test process. ISO 27001 is required from the second year.
  - Validation is immediate. CDRs follow a fixed structure and are signed with a key used only for the OSE role.
  - Availability must be 99.96%.
  - Keys must be in an HSM: FIPS 140-2 Level 2 if dedicated, Level 3 if shared.
  - Development follows OWASP, with penetration tests.
  - The OSE runs a public page to check document validity.
  - Issuers on SUNAT's blocked lists (DL 1532, RS 000302-2024) must be refused.
  - The validation service is invoiced as its own line.
  - Fines are per document: 25 UIT for not validating, 10 UIT for a late forward.
- **PSE and OSE in the same company.** No rule forbids it, and none explicitly allows it. We build both strictly separated:
  - separate keys;
  - no validation shortcut for our own PSE documents;
  - a separate billing line;
  - two SOL authorizations per tenant.

  SUNAT's written confirmation is obtained before the combined offer is sold.

### 3.3 Buyers, retention, records

- **Buyer portal.** Issuers must give buyers authenticated web access to their documents for 1 year (RS 097-2012 art. 25; RS 117-2017 art. 29). The cited text explicitly covers boletas (and their notes) reported by resumen diario, and the CRE/CPE cases in art. 29.2 and 33.4. The service offers the portal for every document type anyway, since vendors do the same. It is served by xhandler-ui through capability links (§12).
- **Retention.** Issuers keep documents at least 5 years, up to the prescription period of 4, 6 or 10 years (Código Tributario arts. 87.7, 87.8, 43).
- **SIRE** has replaced PLE for current periods (RS 112-2021, RS 040-2022, RS 000075-2026, RS 000125-2026).
  - SIRE's API needs the tenant's client_id/secret plus a SOL user and password.
  - PLE remains only for adjusting old periods.

### 3.4 Non-tax law

- **Personal data (Ley 29733, DS 016-2024-JUS).**
  - For invoice data, the service is the processor (encargado); for its own customers' data, it is the controller.
  - Sub-processors need the tenant's authorization (in the contract).
  - Audit logs of every personal-data access, change and export, kept at least 2 years.
  - Access reviews every six months; weekly backups with restore tests.
  - Breaches are reported to the tenant immediately. The controller notifies the ANPD within 48 hours for serious incidents.
  - Data-subject requests are forwarded to the tenant within 3 days.
  - After a contract ends, data is kept at most 2 years, or returned to the tenant.
  - Hosting outside Peru must be communicated to the ANPD.
- **Signatures (Ley 27269, DS 052-2008-PCM).**
  - Holding a tenant's key and signing for them is not an accredited "firma remota".
  - It is valid by contract, with an explicit authorization clause, HSM custody, and a log of every signature.
- **Consumer protection.** A virtual Libro de Reclamaciones with a permanently visible link (Ley 32495). Complaints answered within 15 working days.
- **Taxes on the SaaS.** A Peruvian operator charges IGV and issues a real comprobante for every subscription. Lago's invoices are not comprobantes.
- **Data residency.** No rule found requires XML, CDR or servers to be in Peru.

---

## 4. Architecture

```
 xhandler-ui (separate container)               Lago (separate pod)
 buyer portal · validity page · tenant and       plans · quotas · invoicing
 partner consoles · manual issuing               ▲ usage events (CloudEvents)
            │ REST (generated client)            │  entitlements
 ┌──────────▼────────── Kubernetes / OpenShift ──┴─────────────────┐
 │ api pods     Quarkus: REST · SOAP (OSE) · SFTP intake  + engine │
 │ worker pods  Quarkus: jobs                             + engine │
 │ engine = Rust sidecar on localhost (xhandler-rust)               │
 └──────┬───────────────┬───────────────┬──────────────┬───────────┘
   PostgreSQL HA    S3-compatible    PKCS#11 HSM     Keycloak
   (PITR)           storage          (all keys)      (OIDC)
                                         │
     SUNAT: SOAP billService · GRE REST · SIRE · validity · conformity
     Third-party OSEs (SOAP, per company)
```

- **api**: serves REST and SOAP and the SFTP intake. It runs the emit transaction (number, build, validate, sign) synchronously.
- **worker**: runs jobs (§10).
- **One artifact**: the same image for api and worker, selected by configuration (`xhandler.role=api|worker`). Both scale horizontally.
- **engine**: a stateless sidecar; it holds no data, no keys and no HSM session. Signing takes two calls (§4.1): the engine prepares SignedInfo, Java signs it in the HSM through SunPKCS11, and the engine assembles the signature.
- Observability: OpenTelemetry traces cross Quarkus → engine → SUNAT, and Micrometer metrics are exported to Prometheus (§15).

### 4.1 Engine API (HTTP on localhost, versioned `/v1`)

Mirrors xhandler-mobile's Rust bridge so the two stay aligned (a shared facade crate is optional, §19).

| Endpoint | Purpose |
|---|---|
| `POST /v1/build` | Document JSON (`{"kind": "Invoice", "spec": {...}}`) + defaults → `{xml, enriched_json}`. `enriched_json` (totals, legends) feeds PDFs and reports, so Java never computes taxes. |
| `POST /v1/validate` | XML → SUNAT validation result (codes, observations): XSD (R5, libxml2, schemas precompiled) plus xvalidator rules (R3), in one call |
| `POST /v1/sign` | **P0 only**: single-step signing with SUNAT's beta demo key or a PKCS#12 in the request. Replaced by the two steps below in R1/P1. |
| `POST /v1/sign/prepare` | XML + certificate → `{signed_info, digest_to_sign, state}` |
| `POST /v1/sign/finalize` | `state` + signature bytes from the HSM → `{xml, digest_value}` |
| `POST /v1/certificates/inspect` | Certificate → subject, issuer, RUC, validity dates |
| `POST /v1/send`, `/v1/verify-ticket`, `/v1/status`, `/v1/status-cdr` | SUNAT/OSE SOAP and GRE REST operations |
| `POST /v1/sire/*`, `/v1/validity`, `/v1/conformity/*` | New SUNAT clients (§19) |
| `POST /v1/cdr` | Build an OSE CDR (OSE role) |

- Send results use the mobile `SunatStatus`: `Accepted | Rejected | Exception | InProgress | Unknown | NotDelivered`, plus `code`, `description`, `notes`, `cdr_zip` and `ticket`.
- xsender's `Status` maps from SUNAT codes: 0 → Accepted, 98 → InProgress, 99–1999 → Exception ("not processed, can be sent again"), 2000–3999 → Rejected, ≥ 4000 → Accepted. Its `Unknown` means "any other code". §9.2 decides what each outcome does. The service's `unknown` status ("we don't know whether SUNAT has it") is a separate concept: it is set by crash recovery and by xsender `Unknown` codes.
- Errors carry `kind` (`WrongPassword | InvalidCertificate | InvalidDocument | Other`) and `message`.
- The sidecar API is versioned in its path. Quarkus and the engine ship in the same image release, so only one version is live at a time.

---

## 5. Inputs

| Input | Path |
|---|---|
| Native JSON (xbuilder, snake_case) | `POST /documents`. This is the internal format; every other input converts to it. |
| UBL XML upload | Validated. Signed if unsigned (software/PSE). Stored and sent. |
| SFS-style TXT | An adapter that converts to native JSON |
| Nubefact-compatible JSON | An adapter that converts to native JSON, so their customers can switch by changing the URL and token |
| OSE inbound | SOAP compatible with SUNAT's `billService` (`sendBill`, `sendSummary`, `sendPack`, `getStatus`, `getStatusCdr`); REST for signed XML; SFTP batch |
| Supplier invoices (reception) | `POST /receptions`: XML plus optional PDF/CDR. Checked with SUNAT's validity API and stored. |

---

## 6. Tenancy, identity, secrets

- **Tenants.**
  - `partner`, `tenant` and `company` are first-class rows.
  - Every business table has `tenant_id`, and it is part of every tenant-scoped uniqueness rule.
- **One RUC, one company.** SUNAT numbering is unique per RUC, so a RUC exists only once on the platform per environment: a global partial unique index on `company(ruc, sunat_env) WHERE active`.
  - Before production, the tenant proves it owns the RUC: the certificate's RUC must match and the SOL credentials must log in.
  - Moving a company to another tenant (for example, from a partner to the client) is an explicit transfer, never a second registration.
- **Row-level security mechanics.**
  - The application connects as a non-owner role. Every tenant table has `ENABLE` and `FORCE ROW LEVEL SECURITY`.
  - The tenant is set with `SET LOCAL app.tenant_id` inside each transaction, never per session, so it can't leak across pooled connections.
  - Policies compare against `current_setting('app.tenant_id', true)`. A missing setting matches no rows.
  - Cross-tenant work uses a separate, narrowly granted role or `SECURITY DEFINER` functions, and is audited: the job unblocker and claimer, schedules (purge, retention, blocked lists) and the public capability-link lookups.
  - Hibernate's tenant discriminator sits on top as a second layer.
  - Tests deliberately try to cross tenants, including with the setting missing.
- **Protected deletion.** A tenant or company that has documents cannot be deleted (legal retention); it can only be deactivated.
- **Identity.**
  - Keycloak realm(s) with roles per partner and tenant (admin, operator, viewer, billing) and optional per-company scoping.
  - Machine clients use OAuth2 client credentials.
  - 2FA for users; stronger authentication for platform operators.
  - Self-service signup creates the tenant on first login, with the free tier and SUNAT beta.
- **Keys.**
  - The HSM is reached through PKCS#11 (SunPKCS11 in Java).
  - The platform's PSE key and the OSE CDR key are separate, permanent HSM objects.
  - **Tenant keys are wrapped**, so thousands of tenants don't hit HSM object limits and costs:
    - at import, the private key is wrapped under an HSM master key and stored as ciphertext;
    - at signing time, it is unwrapped into a session-only object inside the HSM.

    The key is only ever in clear text inside the HSM.
  - At import, the .pfx and its password pass through application memory once. They are never logged or stored, and the buffers are cleared.
  - Development and CI use SoftHSM.
  - Every signature is logged: who, document, key, time.
- **Secrets** (SOL, GRE and SIRE credentials, SMTP passwords, lookup tokens).
  - Each secret is encrypted with its own data key, which is wrapped by a master key. Master keys live in the HSM.
  - Decryption tries every active master key, and `xhandler-admin rotate-keys` re-wraps data keys without downtime.
- **Contract.** The tenant contract includes the data processing agreement, the sub-processor list, and the clause authorizing the platform to sign on the tenant's behalf (§3.4).

---

## 7. Data model (PostgreSQL)

Every table has:

- `id` (UUIDv7);
- `tenant_id` (except platform tables);
- `created_at` and `updated_at`.

Editable configuration and catalog rows also have `deleted_at` (soft delete). Issued documents, their events, files and deliveries are never deleted: no `deleted_at`, and there are no partial unique indexes on deletion.

```
partner           id, name, branding (json), status
tenant            id, partner_id?, name, plan_ref (Lago), status, retention_years, audit_hold
company           id, tenant_id, ruc, razon_social, nombre_comercial, direccion(...), email,
                  logo_ref, firmante_*, sunat_env (beta|production), emission_mode
                  (software|pse), submission_route (sunat|ose:<id>), ose_obligated,
                  cuenta_detracciones, es_agente_percepcion, es_agente_retencion,
                  pdf_template_*, pdf_options (json), email_* settings, lookup_provider,
                  delivered_on_emit_default, ruc_verified_at, switching (bool), sending_paused, active
                  UNIQUE(ruc, sunat_env) WHERE active          -- global, across tenants (§6)
ose_endpoint      id, company_id, name, urls (bill, summary, consult), credential_id
credential        id, owner_type (company|platform), owner_id?, kind (sol|gre|sire|validity|
                  smtp|lookup|ose_client), secret (envelope-encrypted), pool (platform SOL pool)
signing_key       id, owner (company|platform_pse|platform_ose), hsm_label | wrapped_key,
                  subject, ruc, valid_from, valid_until, status
pse_authorization id, company_id, authorized_at, revoked_at, evidence_ref
series            id, company_id, sunat_env, tipo_comprobante, serie, next_number, is_default,
                  active, device_id?, UNIQUE(company_id, sunat_env, tipo_comprobante, serie)
daily_counter     company_id, sunat_env, kind (RC|RA|RR), day (America/Lima), next_number
customer, product, vehicle, driver, address        (optional catalogs, as in mobile)
document          id, company_id, sunat_env, origin (api|adapter|ose_inbound|reception),
                  tipo_comprobante, serie, numero, fecha_emision, deadline_at,
                  customer snapshot, moneda, tipo_cambio, total, status, status_before_voiding,
                  sunat_code, sunat_description, sunat_notes (json), ticket, digest_value,
                  input_ref, enriched_ref (S3), affected_document_id, voided_by_document_id, summary_document_id,
                  endpoint, hold_reason (null|needs_attention|operator),
                  delivered_at, delivered_source (service|client), attempts, next_attempt_at,
                  send_started_at, first_sent_at, cdr_received_at, reversal_communicated_at,
                  labels (json), device_id?,  -- partitioned by issue year (§25)
                  UNIQUE(company_id, sunat_env, tipo_comprobante, serie, numero)
                    WHERE origin <> 'ose_inbound'                 -- §7.1
document_file     document_id, kind (xml_signed|cdr_zip|pdf|input), storage_key, sha256, size
document_event    document_id, at, status, code, message, actor, correlation_id   -- timeline/audit
delivery          document_id, channel, recipient, at, result
access_token      document_id, token_hash, created_at, expires_at   -- buyer capability links (§12)
idempotency_key   tenant_id, key, request_hash, response (json), created_at,
                  UNIQUE(tenant_id, key)                          -- §11.1
ose_client        id, issuer_ruc, username, password_hash, failed_attempts, locked_until,
                  sunat_association_checked_at, status            -- OSE inbound auth (§11.4)
ose_submission    id, ose_client_id, issuer_ruc, tipo, serie, numero, received_at, result
                  (cdr|inconsistency), cdr_issued_at, forwarded_at, sunat_ack, document_id?,
                  next_attempt_at, attempts, purge_after          -- 1-month retention duty
conformity        document_id, state (pending|agreed|objected|silent_agreed), due_at, details
reception         id, company_id, supplier_ruc, tipo, serie, numero, validity_state, files
sire_period       company_id, register (rvie|rce), period, state, proposal_ref, ticket, submitted_at
sunat_endpoint    id, url, kind (bill|summary|consult|gre|ose_forward), breaker_state, breaker_until,
                  concurrency_cap, paused         -- written only by the reconciler (§24.2)
job, job_schedule, worker (§10)    webhook_subscription, webhook_delivery    usage_event_outbox
audit_log         at, actor, tenant_id, action, object, details     -- personal-data access log
```

### 7.1 Rules

- Issued documents never change. Only their status columns change, and corrections are new documents.
- Every status change writes a `document_event` carrying the request's `correlation_id`.
- OSE inbound documents are not covered by the document uniqueness rule. An issuer may resubmit a number after an inconsistency notice. For those documents, uniqueness of `(issuer_ruc, tipo, serie, numero)` holds among accepted submissions only, through a partial unique index on `ose_submission WHERE result = 'cdr'`.
- Credentials can belong to the platform (`owner_type = platform`): the PSE SOL users (a pool), the OSE's SUNAT credentials, and the validity API.

### 7.2 Ready for mobile sync

This follows xhandler-mobile's design §3.1:

- UUIDv7 ids and timestamps;
- soft deletes on editable rows;
- `device_id` on rows that are created once;
- files with sha256;
- series that can belong to a device.

Sync itself is not in v1.

---

## 8. Numbering

- Default series are created with each company:
  - `F001` factura, `B001` boleta;
  - `FC01`/`BC01` nota de crédito, `FD01`/`BD01` nota de débito;
  - `T001` GRE remitente, `V001` GRE transportista;
  - `P001` percepción, `R001` retención.

  Clients may choose another active series. POS integrations should use **one series per terminal**: this is common practice, and it removes contention on a shared series (see §11.1).
- A number is reserved inside the emit transaction (§11.1):
  `UPDATE series SET next_number = next_number + 1 WHERE … RETURNING next_number - 1`.
  The unique constraint on `document` is the backstop.
- RC/RA/RR are numbered `RC-YYYYMMDD-n` through `daily_counter`, with the same atomic update. The day is computed in America/Lima.
- **A number is consumed when its document commits.** From then on it stays used, even if SUNAT rejects the document.
  - If the transaction fails, the number is reissued.
  - The XML signed in that transaction was never sent; the signing log records it as discarded.
- Switching beta → production goes through the company's `switching` gate:
  1. reject new emits;
  2. let active jobs drain;
  3. archive beta documents (read-only);
  4. reset the series;
  5. open the gate.

---

## 9. Document lifecycle

### 9.1 Status

| Status | Meaning |
|---|---|
| `draft` | Being edited, no number (manual issuing in xhandler-ui) |
| `pending_send` | Signed and stored; no definitive answer yet |
| `in_progress` | Ticket issued or answer 98; verification scheduled |
| `accepted` | CDR with code 0 and no notes |
| `accepted_with_observations` | Accepted with code ≥ 4000 or notes |
| `rejected` | Codes 2000–3999 |
| `exception` | A permanent error: a permanent SUNAT code (§9.2) or a local error |
| `unknown` | We don't know whether SUNAT has the document: crash during a send (§10.4) or a code xsender can't classify. SUNAT is consulted before anything else. |

Independently of status, `hold_reason` marks documents that need a person: `needs_attention` (repeated identical failures, still retried every 6 hours) or `operator` (held, not retried until released).
| `voiding` | RA/RC/RR sent, waiting for its result. The previous status is kept in `status_before_voiding`. |
| `voided` | Voiding accepted |

```
draft ─emit─▶ pending_send ─send─▶ accepted | accepted_with_observations | rejected | exception
                 ▲   │  ▲               ▲
   transient ────┘   │  └─ consult ── unknown (crash mid-send / unclassified code)
                     └─▶ in_progress ─┘ (verify ticket)
accepted* ─void─▶ voiding ─▶ voided
                     └── voiding rejected ─▶ back to status_before_voiding
```

### 9.2 Retries and definitive answers

- A document **may be sent to SUNAT more than once**; retries are required, and SUNAT is expected to fail (§24).
- Every SUNAT interaction for a document is serialized through the job system's exclusive `document:{id}` resource (§10.2).
- Each attempt is **fenced on the document** (§10.4): it re-reads the document after taking the resource, does nothing once a definitive answer exists, and writes its outcome only if `document.attempts` still matches.
- A retry always sends the **same signed XML bytes** from storage. The only exception is a summary whose ticket is lost and that SUNAT doesn't have; it gets a new RC number (§9.2.2).

#### 9.2.1 Outcomes

Codes are matched in this order:

| Outcome | Definitive? | Action |
|---|---|---|
| Accepted / accepted with observations | Yes | None; later attempts do nothing |
| Rejected (2000–3999) | Yes | None; the number stays used; "duplicate as new" is offered |
| 1033 "registrado previamente con otros datos" | Depends | Recover SUNAT's record (§9.2.2) and **compare it with our `digest_value`**. If they match: accepted. If they differ: `exception` "number collision" (e.g. the number was already used by a previous provider; onboarding sets each series' starting number, §8). Matched before the generic 99–1999 branch. |
| Voided | Yes | None |
| Transient SUNAT error (table below) | No | Retry with backoff |
| Permanent SUNAT error (table below) | Stops automatic retries | `exception`; the user fixes the cause; manual resend (with the same number) |
| **Failure after the request may have reached SUNAT** (timeout, connection reset mid-request, HTTP 5xx after sending, unexpected body, crash after `send_started_at`) | No | `unknown`: resolve the ambiguity first (§9.2.2), never resend blindly. The resolving attempt waits until `send_started_at` + SUNAT read timeout + margin, so a stale call can't still be in flight. |
| Failure before anything was sent (DNS, connection refused, TLS handshake) | No | Retry with backoff |
| Ticket answer 98 (in progress) | No | Check the ticket again later |
| Ticket answer 99 | — | The outcome is the code inside the returned CDR; classify that code, not "99" |

#### 9.2.2 Resolving ambiguity, per document type

SUNAT's consult service (`billConsultService`: `getStatus`/`getStatusCdr`) exists **only in production**. It answers with its own codes ("0001" accepted, "0002" rejected, "0011" doesn't exist, …), which xsender maps explicitly (R2) and never through the 99–1999 ranges.

| Document | Production | Beta |
|---|---|---|
| Factura and its notes | `getStatusCdr` → accepted (store the CDR) / rejected / "doesn't exist" → resend the same bytes | Resend the same bytes; 1033 → digest check via the validity API (consulta integrada) |
| Boleta and its notes (sent individually) | Resend the same bytes; 1033 → validity API (`estadoCp`) + digest check, then accepted **without a CDR** (boletas get no `getStatusCdr` coverage) | Same |
| Summaries (RC/RA/RR) with a known ticket | Check the ticket | Same |
| Summaries with a lost ticket | Check each line through the validity API. Lines SUNAT already has are done; the rest go into a **new** RC/RA/RR with a new number, signed again | Same |
| GRE (REST) | Query by document through the GRE API; resend the same bytes if absent | (no GRE beta) |

#### 9.2.3 SUNAT codes 99–1999 ("not processed")

- **Source.** SUNAT publishes these codes in the *CódigosRetorno* sheet of its CPE validation rules workbook (`AjustesValidacionesCPE vYYYYMMDD.xlsx`, cpe.sunat.gob.pe → Guías y manuales). Changes are logged in its *Control de Cambios* sheet. The current copy is v2026-02-12 (1,987 codes).
- **Where the classification lives.** In xhandler-rust (xsender), as data generated from that sheet, so the service, the CLI and mobile all classify the same way (§19, R2).
- **Unknown codes.** A code that isn't in the table: resolve the ambiguity first (§9.2.2). If the document is still not definitive, set `hold_reason = needs_attention` and alert, so the table gets updated.

| Class | Codes (v2026-02-12) | Meaning | Action |
|---|---|---|---|
| **Transient: SUNAT system** | 0100, 0109, 0130–0138, 0200, 0201, 0253, 0254 | "El sistema no puede responder", authentication service down, storage, queue, database or batch failures on SUNAT's side | Retry with backoff; counts toward the endpoint's breaker |
| **Transient: constancia** | 0125, 0307 | SUNAT could not produce or return the receipt | Resolve via §9.2.2, then retry |
| **Transient: in process** | 0140 | "Existe un Documento igual en Proceso" | Wait, then resolve via §9.2.2; never resend in parallel |
| **Already registered** | 1033 | Registered before "con otros datos" | Digest comparison (above) |
| **Already final** | 1032 | Already reported, voided or rejected | Resolve via §9.2.2; definitive |
| **Route change** | 1079 | Boleta can only go in a resumen diario | Move it into the RC fallback (below) |
| **Ticket lost** | 0126, 0127 | Ticket not ours or doesn't exist | Lost-ticket path (§9.2.2) |
| **Permanent: credentials and authorization** | 0101–0106, 0110–0113, 0154, 1078, 1080, 1085, 1086 | Bad SOL user/password, inactive user, missing CPE profile, user must be secondary, PSE not authorized for the RUC, issuer not authorized for SEE-Del, wrong system (GRE), issuer without operating capacity (SSCO) | `exception`. Alert the tenant: fix credentials, authorization or route. The issuer is blocked from sending until it's fixed. |
| **Permanent: package or file** | 0151–0161, 0202–0204, 0250–0252, 0300–0306 | ZIP or XML name, empty, corrupt or missing file, too many files, unzip failure, wrong method, schema directory or file not found (e.g. a wrong UBLVersionID), unparseable or unprocessable XML | `exception`. Most likely our bug; alert the platform. These fail every time with the same bytes, so retrying would loop. |
| **Permanent: document content** | 1001–1077, 1083 | Format and required-field errors in the XML | `exception`. Fix and resend with the same number. |
| **Permanent: references** | 0403, 0404, 1039, 1040 | Affected document missing or rejected; invalid note target | `exception` |
| **Not applicable** | 0400–0402, 1081, 1082, 1084 | Homologation test cases; utility-services channel; contingency | `exception` + alert |

The GRE REST API and SIRE have their own error codes, published in their manuals. They are classified the same way when R2 implements those clients.

#### 9.2.4 Retry rules

- Backoff steps are 1m, 5m, 15m, 1h, then hourly, with ±20% jitter. Retries are scheduled on the document (`next_attempt_at`), and the reconciler is the only component that turns due documents into attempts (§24). Jobs never schedule follow-up jobs.
- **No hot loops.** After 10 identical transient outcomes, or 24 hours without progress, the document gets `hold_reason = needs_attention` and an alert. **It keeps being retried, every 6 hours,** until it is definitive or someone acts on it. This keeps "always retryable" without hammering SUNAT with a request that keeps failing the same way.
- During an outage, the endpoint's circuit breaker stops attempts for all its documents (§24). Deadline alerts fire before the legal limit (§3.1).
- **Boletas past D+5.** A boleta still not accepted when D+5 (Lima) passes stops being sent individually.
  - A scheduled job groups such boletas by issue day into a resumen diario (RC, estado 1) and sends it before D+7.
  - Each boleta gets `summary_document_id` set and `next_attempt_at` cleared; from then on it follows the RC's outcome.

### 9.3 Guards

- **Delivery.** A document counts as delivered when:
  - the service delivered it (email, portal download);
  - the client attests it, with `delivered: true` on emit (the default comes from `company.delivered_on_emit_default`, usually true for POS), or `POST /documents/{id}/delivered`.

  `delivered_source` records which of the two.
- **Voiding** is allowed only when all of these hold:
  - the document is `accepted`/`accepted_with_observations`;
  - it is within its deadline (§3.1, using `cdr_received_at` or `reversal_communicated_at` as the rule requires);
  - it is not delivered.

  The void request must also attest "not delivered". Otherwise the API returns 409 and suggests a nota de crédito.
- Status changes are conditional: `UPDATE document SET status = :new WHERE id = :id AND status IN (:allowed)`. If zero rows change, the API returns 409.
- A document already delivered that later ends up `rejected` raises a warning (webhook + email) listing who received it.

---

## 10. Job system

Modeled on pulpcore's tasking (`pulpcore/tasking/worker.py`, `tasks.py`, `models/task.py`), kept entirely in Postgres.

### 10.1 Tables

```
job            id, seq bigserial (ordering), tenant_id, name,
               state (waiting|running|completed|failed|canceling|canceled),
               priority_class, run_at, unblocked_at?,
               reserved_resources text[] (exclusive), credential_id?,
               attempt, args (json), result (json), error (json), worker, started_at,
               finished_at, correlation_id, created_resources (json)
job_schedule   name, job_name, interval, next_run_at, last_job_id
worker         name, role, last_heartbeat
```

Indexes:

- partial GIN on `reserved_resources WHERE state IN ('waiting','running')`;
- partial btree on `(priority_class, seq) WHERE state = 'waiting'`;
- partial btree on `(unblocked_at) WHERE state = 'waiting' AND unblocked_at IS NOT NULL`.

The job API is shaped like Pulp's tasks (state, timestamps, error, created resources, links), so pulp-ui-2's task-reporting patterns apply to xhandler-ui.

### 10.2 Resources

- **Exclusive `document:{id}`.** Taken by every attempt on that document: send, ticket check, consult or voiding.
  - A voiding job also takes `document:{id}` of every original it voids.
  - Attempts run one at a time, in `seq` order.
- **Credential slots (`credential_id`).** At most N running jobs per SUNAT credential.
  - Beta: N = 1.
  - Production: configurable. The value is an **assumption to measure**: SUNAT publishes no per-user limit.
  - PSE mode sends with a **pool** of platform SOL users, so the shared credential is not one global bottleneck.
- Endpoint health (breaker, concurrency cap) is enforced by the reconciler (§24.2), not here.

### 10.3 Unblocking and claiming

This is pulpcore's two-step design: a single "unblocker" decides what may run, and workers just claim.

**Unblocker.** At most one worker runs it at a time, under a global `pg_try_advisory_xact_lock(UNBLOCK)`. It runs on `NOTIFY xhandler_job_wakeup` and periodically. In one pass over incomplete jobs in `seq` order, it sets `unblocked_at` on a waiting job only when all of these hold:

- `run_at <= now()`. Jobs scheduled for later reserve nothing, so they never block.
- None of its exclusive resources is taken by a running job or an earlier due job (FIFO per resource).
- Its credential has a free slot, counting running plus already-unblocked jobs.

The `job` table stays small (the reconciler creates attempts only up to capacity, §24.2), so this pass is cheap. Priority is by `priority_class`, then `seq`.

**Claim.** The worker first takes the job's session advisory lock with `pg_try_advisory_lock(job.id)`, on the pod's lock connection (§10.8), **before** the waiting → running compare-and-set, as pulpcore does:

```sql
SELECT id FROM job WHERE state = 'waiting' AND unblocked_at IS NOT NULL
ORDER BY priority_class, unblocked_at FOR UPDATE SKIP LOCKED LIMIT 1;
-- then pg_try_advisory_lock(id); if false, skip it
-- then UPDATE job SET state = 'running', worker = :me WHERE id = :id AND state = 'waiting'
```

### 10.4 Running, fencing and recovery

- **Document fencing.** At the start of an attempt, transaction 1 increments `document.attempts` (to n), records `send_started_at` and commits. The outcome is written in transaction 2 with `UPDATE document … WHERE id = :id AND attempts = :n`. A stale worker (paused or partitioned) that wakes up after a newer attempt has started finds `attempts ≠ n` and writes nothing. `job.attempt` fences only the job row.
- **Ownership.** While a job runs, its worker holds the job's session advisory lock on the pod's lock connection (§10.8).
- **Heartbeat on the lock connection.** As in pulpcore, the worker's heartbeat (`UPDATE worker SET last_heartbeat = now()`) runs **on the same connection that holds the job locks**, so a missing heartbeat always means the locks are gone too. If the lock connection breaks, the worker:
  1. stops claiming;
  2. lets in-flight SUNAT calls finish;
  3. writes their outcomes (still fenced on the document);
  4. exits the process, so Kubernetes restarts it.
- **Recovery.** A singleton under `WORKER_CLEANUP` finds workers whose heartbeat is older than the TTL and whose job locks can be taken (`pg_try_advisory_lock` succeeds), and fails their running jobs. Their documents are never lost:
  - each stays non-definitive;
  - one that had `send_started_at` set moves to `unknown`;
  - the reconciler starts the next attempt when its `next_attempt_at` lease expires (§24.2). Recovery creates no jobs itself.
- Workers name themselves and sleep until `NOTIFY` (wake-up or `xhandler_job_cancel`).
- The state machine has no loops: `waiting → running → completed | failed`, plus `canceling → canceled` from waiting or running.
- Finished jobs are purged after a protection period. The permanent audit trail is `document_event`.

### 10.5 Priority classes

1. OSE forwarding (1-hour legal limit).
2. Document attempts (send, ticket check, ambiguity resolution, voiding). Within this class, the reconciler already orders by legal deadline (§24.2).
3. Webhooks and email.
4. Reports, SIRE, exports, purges.

### 10.6 Schedules

- The **reconciler** (§24.2): every few seconds, enqueue attempts for due non-definitive documents, up to capacity.
- Deadline alerts and the boleta D+5 → RC fallback.
- Certificate expiry (30 days ahead).
- Import of SUNAT's blocked-issuer lists, and checks of the OSE association list.
- Conformity polling.
- SIRE calendar.
- PSE rejected-rate report.
- Usage sync to Lago.
- Circuit-breaker probes (driven by the reconciler, §24.2).
- Job purge; OSE 1-month purge; retention and exit deletions.

### 10.7 Guards summary

1. Exclusive `document:{id}` with FIFO, plus the definitive-answer check at the start of each job.
2. Credential slots (pooled for PSE).
3. Atomic counters for numbering (§8).
4. Conditional status updates (§9.3).
5. Document fencing on `attempts`, plus the reconciler lease; recovery goes through `unknown` (§10.4, §24.2).

The beta → production switch uses the company's `switching` gate (§8), not a job resource.

### 10.8 Concurrency and thread-safety rules

These rules apply pulpcore's tasking discipline to a multi-threaded JVM. Every PR touching jobs, documents or numbering is reviewed against them.

**Invariants (database)**

1. **Postgres is the only source of truth.**
   - No mutable state is shared across requests or jobs in memory.
   - The only in-memory state is immutable configuration and read-through caches (Caffeine) of data that may be stale without harm: entitlements, catalogs, compiled schemas.
2. **Every state change is a compare-and-set** inside a transaction: `UPDATE … WHERE id = :id AND status IN (:allowed) [AND attempts = :n]`, which must change exactly one row. Nothing reads, decides in Java, and then writes without a row lock or this conditional.
3. **No remote call while holding row locks**, with one documented exception: the emit transaction (engine, HSM, S3; §11.1). SUNAT calls always follow three steps:
   1. transaction 1 records `send_started_at` and commits;
   2. call SUNAT, with no transaction open;
   3. transaction 2 writes the result with a compare-and-set.
4. **Lock ordering**, to prevent deadlocks: company → series/daily_counter → documents (sorted by id) → job rows. Advisory locks use the two-integer form `(namespace, key)`, with one namespace per purpose. They are never taken inside a row-lock section in any other order.
5. **Singleton activities** run under `pg_try_advisory_xact_lock(namespace, constant)`. If the lock isn't acquired, the activity is skipped, because another worker is doing it. This is the pulpcore model (`TASK_UNBLOCKING_LOCK`, `TASK_SCHEDULING_LOCK`, `TASK_METRICS_LOCK`, `WORKER_CLEANUP_LOCK`). Ours:
   - `UNBLOCK`;
   - `RECONCILE`;
   - `SCHEDULE_DISPATCH`;
   - `WORKER_CLEANUP`;
   - `METRICS`;
   - `BLOCKED_LIST_IMPORT`;
   - `RETENTION_PURGE`.
6. **Job ownership.**
   - Only the worker holding a job's session advisory lock may move it from `running` to a final state.
   - `canceling` may be set by anyone, without the lock (as in pulpcore), and is signalled with `NOTIFY xhandler_job_cancel`.
   - The running worker checks for cancellation at safe points. **It never abandons a SUNAT call after `send_started_at`**: it finishes, records the result, then honors the cancellation.
7. **Idempotent handlers.** Every job handler can safely run again from the start: it re-reads state, resends the same bytes, and writes results with a compare-and-set. Fencing with `attempt` makes a stale run harmless (§10.4).

**Connections**

- Each worker pod has one **lock connection**. It holds the session advisory locks of all jobs currently running in that pod, and it is never returned to the pool. Calls on it are serialized by a single mutex-guarded component.
- Only **non-blocking** `pg_try_advisory_*` calls are allowed on the lock connection. A blocking call would stall every job behind the mutex, and Postgres's deadlock detector can't see waits that span the pool and the lock connection.
- The worker's **heartbeat runs on the lock connection** (§10.4). So a live heartbeat proves the locks are held, and a lost connection stops the heartbeat and makes recovery possible.
- If the lock connection breaks, the locks are gone. The worker stops claiming, lets in-flight calls finish, writes their outcomes (fenced on `document.attempts`), and exits (§10.4).
- The lock and listener connections are direct to Postgres. They never go through a transaction-pooling proxy such as PgBouncer.
- A separate dedicated **listener connection** handles `LISTEN`/`NOTIFY`.
- The pool size covers concurrent jobs, concurrent requests, the lock connection, the listener connection and headroom. It's checked at startup.

**Threads (Quarkus / Java)**

- HTTP requests run on virtual threads. **JNI calls pin virtual threads** (SunPKCS11/HSM), so HSM calls run on a bounded platform-thread executor sized to the HSM session pool. Emits take a permit from a bounded semaphore, and connection-pool acquisition has a timeout, so a slow HSM degrades emits only, not reads.
- Jobs run on a bounded executor with N slots per worker pod. N is configured, and the unblocker never hands out more work than slots exist.
- CDI beans are stateless (`@ApplicationScoped` with no mutable fields). Data passed between components is immutable (Java records).
- Hibernate sessions and entities never cross threads or transactions; IDs are passed instead.
- Objects that are not thread-safe are never shared:
  - XML: `javax.xml.validation.Validator`, `DocumentBuilder`, `Transformer`, StAX readers. Their compiled factories (`Schema`, `Templates`) are thread-safe and shared.
  - JCA `Signature` and `Cipher` instances.
  - PKCS#11 sessions come from a bounded HSM session pool, one thread per session at a time.
- The engine client (REST client over Vert.x) is shared and thread-safe. The engine itself is stateless: Rust holds no global mutable state, and its template and catalog statics are immutable.
- Timeouts everywhere: database statement timeout, engine call timeout, HSM timeout, SUNAT connect and read timeouts. Nothing waits forever while holding a slot.

**Verification**

- Static checks: Error Prone, plus a rule banning mutable fields in application-scoped beans.
- Property-based tests of the unblocker: random job and resource mixes; assert no overlap, FIFO per resource, slot limits and no starvation.
- Concurrency integration tests with real Postgres:
  - N threads emitting on one series give no duplicate or skipped numbers;
  - concurrent void and send on one document stay serialized;
  - concurrent idempotent retries produce one document.
- Chaos tests: kill workers or the lock connection mid-SUNAT-call, pause workers past the heartbeat window, and assert no duplicate sends and that recovery goes through `unknown` (§15.2).

### 10.9 Why not Redis

- pulpcore's Redis worker exists for hosted-scale database load.
- Its changelog shows lock leaks, orphaned and stale locks, stalls and FIFO bugs, because state lives in Postgres while locks live in Redis.
- Our throughput is bounded by SUNAT's latency, and Postgres is enough.
- Redis may be added to xhandler-service later only if a metric demands it (shared rate limits, queue load on the database).
- Lago's own Redis is unrelated; it belongs to the billing product (§14).

---

## 11. Flows

### 11.1 Emit (software and PSE)

`POST /documents`, with an `Idempotency-Key` header:

1. **Idempotency.** Insert `(tenant_id, key, request_hash)` into `idempotency_key`.
   - On conflict with the same hash and a stored response: return that response.
   - On conflict while the first request is still running: 409.
   - On conflict with a different hash: 422.
2. **Quota** check against cached Lago entitlements. If Lago is unreachable, the last known entitlements are used for up to 24 hours; after that, emits for over-quota tenants fail closed.
3. **One short transaction** (an emit permit from a bounded semaphore is taken first; pool acquisition has a timeout):
   1. reserve the number (§8);
   2. `engine build` with the real number, then `engine validate` (XSD + rules). On errors: roll back (no number is consumed, since numbers are consumed only on commit) and return 422 with SUNAT codes;
   3. `sign/prepare`, then the HSM signs the digest (tenant key in software mode, platform PSE key in PSE mode), then `sign/finalize`. HSM calls run on a **bounded platform-thread executor** sized to the HSM session pool, because SunPKCS11 is JNI and would pin virtual threads;
   4. upload the signed XML to S3. It is content-addressed, so an orphan left by a rolled-back transaction is harmless and purged later. `input_json` and `enriched_json` are stored the same way (§25);
   5. insert `document` (`pending_send`, `next_attempt_at = now()`), `document_file`, the usage outbox row and the stored idempotency response, then `NOTIFY xhandler_reconcile`;
   6. commit.

   The series row stays locked for the engine call, the HSM call and the S3 upload, so emits on one series run one at a time. One series per POS terminal (§8) keeps that off the critical path. Per-series throughput is a measured SLO. If measurements show the S3 upload dominates, the fallback is to store the signed XML in Postgres within the transaction and copy it to S3 asynchronously.
4. Reply `201` with id, serie-número, digest/QR data, the buyer access link and the status.

Emit never creates jobs. The reconciler sends the document (§24.2), usually within a second.

### 11.2 Send

- The reconciler leases the document and creates an attempt (§24.2).
- The worker holds `document:{id}` and a credential slot. It re-reads the document, increments `attempts` and records `send_started_at` (transaction 1), then sends through the company's derived route:
  - SUNAT direct;
  - a third-party OSE;
  - GRE always over REST to SUNAT.
- Transaction 2, fenced on `attempts`, applies §9.2:
  - sets `first_sent_at` and `cdr_received_at`;
  - writes events and emits webhooks and usage events;
  - for a non-definitive outcome, sets `next_attempt_at` (backoff, ticket check or ambiguity resolution).

### 11.3 Voiding

1. `POST /documents/{id}/void` checks the §9.3 guards.
2. It builds the RA, RC (estado 3) or RR line and numbers it from `daily_counter`.
3. It moves the original(s) to `voiding`, saving `status_before_voiding`.
4. It sets the voiding document's `next_attempt_at = now()`. The reconciler creates its attempts, and each attempt holds `document:{voiding_id}` and `document:{original}` for every original.
5. Outcome:
   - voiding accepted: originals move to `voided`;
   - voiding rejected: originals return to `status_before_voiding`, and the user is told.

### 11.4 OSE inbound

1. **Receive** over SOAP (`billService`, WS-Security UsernameToken), REST or SFTP.
   - The issuer authenticates as an `ose_client`: password hashed, lockout after failed attempts.
   - The issuer must be on SUNAT's list of taxpayers associated with us as OSE, checked periodically.
2. **Harden input**: size limits; ZIP entry count, size and compression-ratio caps; DTDs disabled (XXE).
3. **Refuse** issuers on SUNAT's blocked lists.
4. **Validate** fully with xvalidator. There is no shortcut for documents the platform produced as PSE.
5. **Answer**: build the CDR and sign it with the OSE key, or return an inconsistency notice. Record an `ose_submission`.
6. **Forward**: the submission gets `next_attempt_at = now()`, and the reconciler creates priority-1 forwarding attempts that send the document and CDR to SUNAT within 1 hour, recording `cdr_issued_at`, `forwarded_at` and `sunat_ack`. The 1-hour SLO is measured from `cdr_issued_at`.
7. **Retain** the submission at least 1 month (`purge_after`). If the issuer is also our tenant, the document follows that tenant's retention instead.
8. Record usage on a separate "OSE validation" billing line, and expose the result through the public validity API.

### 11.5 Supplier reception

`POST /receptions` takes XML plus optional files, all treated as untrusted input (size and ZIP caps, DTDs disabled). It parses the document, checks it with SUNAT's validity API (`estadoCp`, `estadoRuc`), stores it, and indexes it for the purchases side of SIRE.

### 11.6 Delivery

- The PDF is rendered from `enriched_json` + company data + status.
  - Formats: A4 and 80/58 mm tickets.
  - Contents: SUNAT QR (`RUC|TIPO|SERIE|NUMERO|IGV|TOTAL|FECHA|TIPO_DOC|NUM_DOC|HASH|`, with GRE using SUNAT's QR), partner/tenant branding, and a "pendiente" banner until there is a definitive answer.
  - Templates are Qute code shipped with the service; companies customize them through options.
- Email goes through the platform sender or the tenant's SMTP, with per-company subject/body templates. Attachments: PDF, XML and optionally the CDR. Each email carries the buyer's capability link (§12).
- `delivered_at` is set by the first successful delivery (`delivered_source = service`) or by the client's attestation (`client`, §9.3). It drives the voiding guard. Every delivery is logged.

### 11.7 SIRE

For each company and period:

1. Download SUNAT's proposal (RVIE and RCE) through SIRE's API.
2. Reconcile it with stored documents and receptions.
3. Generate replacement and adjustment files.
4. Submit them and poll the tickets.
5. Track the deadline calendar (RS 281-2022 annex II).

PLE files are generated only to adjust old periods.

### 11.8 Factura negociable

- Credit facturas carry FormaPago, cuotas and the net pending amount (already in xbuilder).
- After acceptance, a `conformity` row starts the 8-day window. The service polls SUNAT's conformity API and sends webhooks on agreed, objected or silent agreement, plus reminders.
- An objection leads to a guided credit note type 13.
- An export is provided for factoring. There is no CAVALI integration.

### 11.9 Reports

One row generator feeds CSV, Excel, SIRE and legacy PLE. Reports run as jobs (`202` + job link).

---

## 12. API

- Base path `/api/v1`. The OpenAPI spec is generated by SmallRye. It must be good enough for `@hey-api/openapi-ts`: stable operation ids, typed error schemas, consistent pagination and filters.
- `201` for emit; `202` + job link for long operations; `409` for guard violations; `422` with SUNAT codes for invalid documents.
- `Idempotency-Key` on every create, stored with a request hash and the response (§11.1).
- Per-tenant rate limits are applied at the gateway.
- Free-form `labels` on documents (ERP ids), with filtering.
- One identifier format across the API, webhooks and logs: `xh:<type>:<uuid>`.
- Permissions are exposed per resource so the UI can hide or disable actions (pulp-ui-2 ADR 0008).
- Webhooks:
  - payload is a CloudEvent (`document.accepted`, `document.rejected`, `document.voided`, `conformity.objected`, …) with a versioned `dataschema`;
  - signed with HMAC-SHA256 (`X-Xhandler-Signature`, with a timestamp);
  - retried with backoff, every delivery logged, redelivery available through the API;
  - sent through the egress proxy (§16).
- Client SDKs are generated from the OpenAPI spec.
- **Versioning.**
  - Within `/api/v1`, changes are additive only.
  - A breaking change creates `/api/v2`, and v1 is kept for at least 12 months after v2 ships, with `Deprecation`/`Sunset` headers.
  - Webhook payloads follow the same rule through `dataschema` versions.
- **Public endpoints** (no login, rate limited, behind the gateway's bot protection):
  - **Buyer access by capability link.** Each document gets a random 128-bit token, stored hashed in `access_token`. It is printed in the PDF QR, sent in emails and returned by the emit API. The link opens the document's XML, CDR and PDF for the legal access period. This is the primary path.
  - **Lookup by data.** RUC + serie-número + date + total is low-entropy and guessable. It requires a CAPTCHA, has per-IP and per-RUC limits, and returns **only status and validity**, never the files.
  - **OSE validity** (RS 242-2018): status only.

---

## 13. Storage and retention

- Files are stored under `tenant/{tenant_id}/{sha256}` in S3-compatible storage and never modified. Postgres keeps the metadata.
- Retention is `tenant.retention_years`, 10 by default, and files move to an archive tier with age.
- Nothing is deleted while `tenant.audit_hold` is set, which flags a tax audit.
- OSE inbound submissions are kept at least 1 month (`ose_submission.purge_after`). If the issuer is also our tenant, they follow that tenant's retention.
- When a tenant leaves:
  1. Full export: XML, CDR, PDF and metadata, as ZIP + JSON.
  2. Deletion 2 years later, with evidence, unless the tenant buys an archive plan.

This satisfies the processor's 2-year limit (DS 016-2024-JUS art. 31.2) while the tenant keeps the copies it is legally required to hold.

---

## 14. Billing (Lago)

- Lago runs as its own deployment, with **its own Postgres and Redis** (Sidekiq). This is accepted: "no Redis" applies to xhandler-service, and Lago is a separate product. It is AGPL and used unmodified as a separate service.
- If Lago is unreachable, emits continue on cached entitlements for up to 24 hours (§11.1), and usage events wait in the outbox.
- The service writes usage events (documents accepted per type, OSE validations, receptions, storage) to `usage_event_outbox` in the business transaction. A job delivers them to Lago.
- Entitlements (plan, quotas) are synced from Lago and cached. Quotas are enforced at emit.
- Partners see their tenants' usage.
- **The platform's own subscription invoices are issued as real comprobantes through xhandler-service**, because Lago's invoices are not comprobantes.

---

## 15. Observability and operations

- **Logs**: structured JSON. A correlation id flows from the API request to its jobs, to the engine and to the SUNAT call, and is stored on jobs and events.
- **Metrics**:
  - queue wait time (ready but unclaimed) and depth per priority;
  - SUNAT latency and response codes per environment and document type;
  - documents per status;
  - deadlines at risk;
  - OSE forwarding time against the 1-hour limit;
  - PSE rejected-CDR rate;
  - certificate expiry;
  - API latency histograms;
  - HSM latency and errors.
- **Traces**: OpenTelemetry across Quarkus and the engine.
- **Health and status**: `/q/health` (liveness/readiness) for Kubernetes. `/api/v1/status` reports versions (service, engine, xhandler-rust revision), online workers, database, storage and HSM health, and queue depth.
- **SLOs**: 99.96% availability for OSE intake, and OSE forwarding under 1 hour at the 100th percentile. Dashboards and alerts for both.
- **Engine performance budget** (per document, typical invoice, one core): build + validate (XSD + rules) + sign-prepare under 5 ms at p99, excluding the HSM signature. It is tracked with `criterion` benchmarks. CI gates on **instruction counts** (`iai-callgrind`), which are stable on shared runners, failing on a regression above 10%. Wall-clock benchmarks run nightly on dedicated hardware.
- **Admin CLI** (`xhandler-admin`, Picocli):
  - `rotate-keys`;
  - `requeue-document`;
  - `cancel-job`;
  - `purge-jobs`;
  - `data-repair`;
  - `migration-status`;
  - `reset-admin`;
  - `hold` / `release` (document, company, tenant, endpoint);
  - `retry` (one document, or in bulk by filter);
  - `endpoint-status`.
- **Runbooks**: a stuck queue, SUNAT outage, HSM outage, a certificate expiring, a breach.

### 15.1 Schema migrations

- Flyway, with the **expand/contract** rule, because api and worker pods of two versions run side by side during a rolling deploy:
  1. add columns and tables in release N;
  2. backfill;
  3. switch reads in N+1;
  4. drop in N+2.
- No migration may take long locks on `document` or `job`. Large backfills run as jobs.

### 15.2 Testing strategy

- **Unit tests** (JUnit), including property-based tests of the unblocker and claim logic: no overlap on a resource, FIFO per resource, credential slots respected, no starvation.
- **Integration tests** with Dev Services: Postgres, Keycloak, SoftHSM, MinIO.
- **A SUNAT fake** (SOAP + REST) is the CI gate. It can return any code, delays, timeouts and 1033, and it can drop a response after accepting the document. SUNAT beta is flaky and lenient, so beta e2e runs nightly, not as a gate.
- **Golden files**: built XML per document type (as in xhandler-rust), adapter inputs/outputs, CDRs.
- **Chaos tests**: kill workers mid-SUNAT-call, kill the lock connection, and pause workers past the heartbeat window. Assert no duplicate sends and that recovery goes through `unknown`.
- **Tenant isolation tests**: deliberately cross tenants, including with the tenant setting missing.
- Engine benchmarks (`criterion`): build, XSD validation, rules and sign-prepare per document type, gated in CI (§15).
- Load tests for OSE intake and forwarding (P10/P11).

---

## 16. Security and compliance in the product

- ISO 27001 controls: access control, logging, change management, secure development (OWASP), penetration tests, supplier management, backups.
- Private keys only ever in clear text inside the HSM; every signature logged; secrets envelope-encrypted with rotation.
- **SSRF.** Every outbound connection to a tenant-supplied host (webhook URLs, SMTP hosts, third-party OSE endpoints, lookup URLs) goes through an egress proxy:
  - private, loopback, link-local and metadata ranges are denied, so it can't reach the engine on localhost, cloud metadata, or Keycloak admin;
  - DNS is resolved and pinned before connecting;
  - only allowed ports are open.
- **Untrusted files** (UBL uploads, receptions, OSE ZIPs, SFTP drops):
  - DTDs and external entities are disabled in every XML parser (Java and Rust);
  - ZIP entry count, size and compression ratio are capped;
  - request sizes are limited.
- Audit logs of personal-data access, kept at least 2 years; access reviews every six months.
- Weekly backups with restore tests; Postgres point-in-time recovery; disaster-recovery drills.
- Data-subject request workflow: forwarded to the tenant within 3 days, with tracked deadlines.
- Breach runbook: notify tenants immediately, and support the controller's 48-hour notice to the ANPD and CNSD.
- Sub-processor list published; the data processing agreement and signing-authorization clause in the tenant contract.
- Libro de Reclamaciones link in xhandler-ui.
- Tenant isolation tests and dependency scanning in CI.

---

## 17. Catalogs and lookup

- Optional CRUD for customers, products, vehicles, drivers and addresses.
- A document may reference catalog entries or carry everything inline.
- RUC/DNI lookup behind one interface, `lookup(tipo, numero)`: a commercial provider (tenant token) or `searchpe`. Results are cached in `customer`.

---

## 18. xhandler-ui

xhandler-ui (sibling repo, separate container) is the only UI. It **must follow the design, patterns, skills, rules and tooling of [pulp-ui-2](https://github.com/pulp/pulp-ui-2)**:

- **Stack**:
  - React 19 + PatternFly 6, Vite, TypeScript;
  - TanStack Router (file-based routes) and TanStack Query, axios;
  - an API client generated with `@hey-api/openapi-ts`;
  - react-hook-form;
  - Vitest, and Playwright e2e tests whose folders mirror `pages/`;
  - an Express production server; OIDC login.
- **Repo layout**: npm workspaces `common/`, `client/`, `server/` and `e2e/`, with the same `client/src/app/` structure (`pages/`, `queries/`, `components/`, `hooks/`, generated `client/`, `axios-config/`).
- **Guidance**:
  - `AGENTS.md`;
  - `docs/adr/` (what the UI does, each ADR with `## Rules`; design changes need a new or superseding ADR);
  - `.rulesync/` rules and skills generated into `.claude/` and `.cursor/`;
  - `docs/internals/`.
- **Tooling**: oxlint/oxfmt for the fast loop; ESLint, tsc and Prettier as the gate; husky and commitlint; PatternFly skills.
- **Adaptations**: Pulp-specific rules are rewritten (e.g. `pulp-api` → `xhandler-api`). Plugin concepts are adapted where useful (document types as data, after pulp-ui-2 ADR 0001) or dropped, and the U0 ADRs record which.

**Language:** the UI is in Spanish (es-PE), and so are emails, PDFs and API error messages (§26.9).

v1 screens:

- buyer portal (capability links, 1-year access) and OSE validity page. These are **public routes outside the OIDC login flow**: no session, and the generated client calls only the public endpoints (§12). A U0 ADR defines how public and authenticated routes coexist;
- tenant admin console (companies, certificates, series, API clients, webhooks, usage, browsing and resending documents);
- partner console;
- manual issuing;
- SIRE and conformity screens.

---

## 19. Changes in xhandler-rust

| Item | Change |
|---|---|
| R1 | **xsigner external-signer mode**, in two steps: `prepare` returns the canonical SignedInfo and its digest, and `finalize` takes the signature bytes and assembles the `ds:Signature`. Java does the HSM signing in between (engine `sign/prepare` / `sign/finalize`). Output must match .pfx signing byte for byte. |
| R5 | **Runtime XSD validation in Rust, built for speed.** A **separate crate, `xxsd`**, validates against SUNAT's XSDs. It is kept apart from `xvalidator` (R3, the SUNAT rules) and from xbuilder/xsigner, which all stay pure Rust for mobile. It with **libxml2 through the `libxml` crate**: the fastest proven option, and the same validator xbuilder's tests use today, so results match the current suite.<br>• The UBL/SUNAT schemas are parsed and compiled **once at engine startup**, one compiled schema per document type.<br>• libxml2 validation contexts are not thread-safe, so validation runs on a **fixed-size dedicated thread pool**, one context per thread. tokio's blocking pool would create and reap threads and churn contexts.<br>• Known libxml2 integration points:<br>  – `xmlInitParser` once at startup;<br>  – a global `xmlSetExternalEntityLoader` that resolves `xs:import`/`include` from the embedded schemas only;<br>  – thin `unsafe` Send wrappers around the crate's schema types where needed.<br>• libxml2's maintenance and CVE record is a tracked risk: pin it and patch it promptly.<br>• Parsing is hardened: no network (`XML_PARSE_NONET`), no entity expansion or DTD loading (no `XML_PARSE_NOENT`/`DTDLOAD`), and input size caps. Schemas come only from embedded files.<br>• XSD validation and the xvalidator rules run in **one engine call** (`/v1/validate`), parsing the document once where possible.<br>• Running inside the sidecar process also isolates libxml2: a crash in C code restarts the engine container, never the JVM.<br>• The engine image ships libxml2 (fine in a container). Mobile keeps working without XSD validation, or with a pure-Rust validator later.<br>• Behind an `XsdValidator` trait, so a pure-Rust crate (`xsd-schema`, `oxixml-schema`) can replace libxml2 once it passes the conformance corpus (all xbuilder snapshots plus negative cases) at equal or better speed. |
| R2 | **New xsender clients**: validity check (consulta integrada); factura negociable conformity; SIRE (OAuth password grant, ZIP + SHA-256, tickets); import of blocked-issuer lists. **Plus SUNAT code classification** (transient/permanent/special, §9.2), generated from the workbook's CódigosRetorno sheet and exposed through the engine API. |
| R3 | **xvalidator crate**: SUNAT's validation rules, data-driven and versioned from the v2026-02-12 catalog, returning SUNAT codes. Built incrementally: factura/boleta, then notes, summaries, percepción/retención. |
| R4 | **OSE CDR builder** (CDR-OSE structure for documents and summaries), signed through R1 |
| Optional | A shared facade crate used by both the engine and the xhandler-mobile bridge |

---

## 20. Repository layout

```
xhandler-service/
├── pom.xml                      Maven multi-module (Java 25, Quarkus)
├── app/                         Quarkus application (api + worker roles)
│   └── src/main/java/…          tenancy, identity, documents, numbering, jobs, sunat,
│                                ose, reception, delivery, sire, conformity, billing,
│                                catalogs, reports, webhooks, admin
├── admin-cli/                   xhandler-admin (Picocli)
├── engine/                      Rust sidecar (Cargo; depends on xhandler-rust pinned by rev)
├── deploy/                      Kubernetes manifests / Helm chart, Lago, Keycloak realm
├── docs/
│   ├── DESIGN.md
│   └── research/                regulatory research
└── .github/workflows/           CI: Java, Rust, images, e2e against SUNAT beta
```

---

## 21. Roadmap

Four tracks run in parallel. Each phase is one PR (or a small stack of PRs), followed by a senior-engineer review. Phases are internal milestones; the public v1 launches at GA with all three roles.

This is a deliberate choice, made after the design review. Launching all three roles at once means GA waits for OSE registration (capital, bank guarantee, audit, SUNAT tests). Pilot tenants at M1 and M2 can still run in production before GA.

```
Service      P0 ─ P1 ─ P2 ─ P3 ─ P4 ─ P5 ─ P6 ─ P7 ─ P8 ─ P9 ─ P10 ─ P11 (GA)
                                 M1 ▲          M2 ▲          M3 ▲
xhandler-rust  R1 ───── R2 ───────── R3 (incremental) ───────── R4
xhandler-ui                  U0 ─ U1 ──── U2 ──── U3 ──── U4
Certification  C1 (company, ISO 27001, legal) ──── C2 (PSE) ──── C3 (OSE)
Infrastructure I1 (dev/staging) ─── I2 (production HA: sync replication, multi-zone, HSM cluster) ── I3 (DR drills)
```

### 21.1 Service

| Phase | Goal | Deliverables | Exit criteria |
|---|---|---|---|
| **P0** Walking skeleton | Prove the architecture | Repo with `AGENTS.md`/`CLAUDE.md` conventions (§26.9); Maven parent + `app` module (Quarkus 3.40, Java 25); `engine/` Rust sidecar (axum, xhandler-rust pinned by rev) with `/v1/build`, `/v1/sign` (single-step with SUNAT's beta demo key or a PKCS#12, until R1), `/v1/send`, `/health` and a `--health` exec probe; dev-only `POST /api/v1/dev/documents` (build → sign → send to beta); Dev Services PostgreSQL + Flyway baseline; health, metrics, OpenAPI; OpenTelemetry across both containers (W3C trace context); `hack/compose.yaml` (PostgreSQL, Grafana LGTM) and `hack/e2e.sh`; container images; Kustomize base (one pod = app + engine); CI (app, engine, images, manifests) and a nightly SUNAT-beta e2e. Keycloak, MinIO and SoftHSM move to P1, where they're first used. Publishing and signing images and the dev/staging GitOps environments belong to the infrastructure track (I1). | Done: `hack/e2e.sh` gets an invoice accepted by SUNAT beta through the engine, and one trace spans `xhandler-service` → `xhandler-engine` → `sunat.send` |
| **P1** Platform core | Multi-tenant foundations | Dev Services for Keycloak, MinIO and SoftHSM; Partner/tenant/company, row-level security, Keycloak roles + client credentials, self-service signup with free-tier abuse controls (§26.8), support access (§26.1), audit log, envelope-encrypted secrets with rotation, HSM integration, certificate import/inspect, series, beta/production per company, API conventions (errors, pagination, idempotency, labels, permissions) | Cross-tenant tests fail closed; keys never leave the HSM; keys rotate without downtime |
| **P2** Job system | Pulp-style tasking + SUNAT resilience | Jobs + state machine, unblocker (FIFO per document, credential slots), claim with try-lock before the compare-and-set, heartbeat on the lock connection, recovery via `unknown`, **reconciler with leases (only creator of attempts), document-level retry state and fencing, endpoint breakers, deadline-ordered drain, `needs_attention`, holds (§24)**, LISTEN/NOTIFY, schedules, purge, metrics, `/api/v1/status`, admin CLI (hold/release/retry), SUNAT fake | Property and chaos tests: killing or pausing workers, or killing the lock connection, loses no work and causes no duplicate attempts; stale workers can't overwrite outcomes; simulated SUNAT outage + recovery drains without loss, in deadline order, with no thundering herd |
| **P3** Emit and send (software mode) | Real factura/boleta | `POST /documents` (idempotency table, short emit transaction with HSM executor and emit semaphore), numbering, XSD (R5) + sign (R1), storage, capability links, status machine, send/retry/ticket with the definitive-answer rule, per-type ambiguity resolution and 1033 digest check (§9.2), code classification, boleta D+5 → RC fallback, deadline alerts (America/Lima), webhooks, timeline | Accepted on SUNAT beta; retry tests show no send after a definitive answer; SUNAT-outage codes are retried; alerts fire |
| **P4** All documents | Complete the set | Notes; SBS exchange rates (§26.7); RA/RC/RR voiding with the delivered guard and voiding-rejection rollback; GRE REST + OAuth; percepción/retención; derived submission route (SUNAT or third-party OSE); production switch gate; 2027 rules | Every document accepted on beta; sending through a third-party OSE works; invalid routes refused |
| **P5** Delivery and catalogs | Reach buyers | Qute PDFs (A4, tickets, QR, branding, Spanish), email (platform + tenant SMTP, SPF/DKIM/DMARC, bounces, §26.5), tenant notifications (§26.6), delivery log, buyer-portal APIs, catalogs, RUC/DNI lookup | A buyer fetches XML/CDR/PDF through the API; emails delivered and logged |
| **M1** | **Software-mode beta** | Pilot tenants | |
| **P6** Billing, partners and developers | Monetization and DX | Lago pod, usage outbox, entitlements and quotas, partner APIs, white-label, self-invoicing through xhandler-service; developer experience: public API reference, published SDKs, sandbox with SUNAT-fake mode, quickstarts, webhook tools, status page (§26.2) | Quotas enforced; partner onboards a tenant through the API; Lago → our factura works |
| **P7** PSE mode | Sign for tenants | Platform PSE key in the HSM, pool of platform SOL users, PSE authorization tracking, xvalidator gate before sending, rejected-rate metric and alerts | PSE-signed documents accepted on beta; rejected rate reported per half-year; measured per-credential throughput |
| **M2** | **PSE ready** | Feeds C2 | |
| **P8** Adapters and reception | More inputs | UBL XML upload, SFS TXT, Nubefact JSON, supplier reception with validity checks | Golden-file tests per adapter; receptions validated and stored |
| **P9** Tax compliance | SIRE, negociable, reports | SIRE automation, conformity tracking, CSV/Excel, legacy PLE | SIRE round trip against SUNAT; conformity tracked end to end |
| **P10** OSE | Become an operator | SOAP billService (incl. sendPack) with WS-Security auth (`ose_client`), association-list checks, REST/SFTP intake with input hardening, full xvalidator validation, blocked lists, OSE CDR with its own key, `ose_submission` + 1-hour forwarding class with SLA metric, 1-month retention, public validity API, separate billing line | An external system switches by changing only the URL; forwarding under 1 hour under load; ready for SUNAT's tests |
| **M3** | **OSE ready** | Feeds C3 | |
| **P11** GA hardening | Enterprise operations (with the infrastructure track) | Postgres HA + PITR (CloudNativePG), multi-zone, DR drills (Postgres failover, zone loss, HSM restart, region failover; §25.1), backup restore tests, OWASP pen test, load tests at §25 figures including outage + drain, data protection workflows (data-subject requests, breach runbook, exit export and deletion, archive plan), runbooks, SLO dashboards | SLOs met over a soak period; pen-test findings closed; ISO 27001 evidence complete |

### 21.2 xhandler-rust

| Phase | Deliverable | Needed by |
|---|---|---|
| R1 | External-signer mode in xsigner (prepare/finalize) | P1/P3 |
| R5 | Runtime XSD validation in Rust (libxml2, compiled once, thread-local contexts), benchmarked | P3 |
| R2 | Validity, conformity, SIRE and blocked-list clients | P8, P9, P10 |
| R3 | xvalidator, incremental | Partly by P7, complete by P10 |
| R4 | OSE CDR builder | P10 |

### 21.3 xhandler-ui (starts after P3)

| Phase | Deliverable |
|---|---|
| U0 | Bootstrap on the pulp-ui-2 blueprint: structure, tooling, rules, skills, ADR process; first ADRs (navigation partner → tenant → company, app shell, job reporting); OIDC + generated client + CI running against the service |
| U1 | Tenant admin console |
| U2 | Buyer portal, Libro de Reclamaciones link, manual issuing |
| U3 | Partner console |
| U4 | OSE validity page, SIRE and conformity screens |

### 21.4 Infrastructure (separate project)

Kubernetes and the cloud are designed in their own project, but on the same timeline:

| Phase | Deliverable | Needed by |
|---|---|---|
| I1 | dev and staging clusters, GitOps, observability stack, SoftHSM/MinIO in dev | P0 |
| I2 | Production: multi-zone Kubernetes, Postgres with synchronous replication and PITR, cross-region replicas, clustered HSM, gateway, egress proxy with static IPs | M1 |
| I3 | DR drills and the region-failover runbook; evidence for ISO 27001 and the OSE availability requirement | P11 |

### 21.5 Why this order

- Software mode comes first because PSE and OSE are built on its jobs, documents, storage and delivery.
- The job system comes before real sends, because the retry and racing rules must be proven first.
- Billing comes before PSE so pilot tenants can pay.
- OSE comes last: it needs a complete xvalidator, and its certification needs a working system.

---

## 22. Certification and legal track

This work takes calendar time, so it starts at P0.

| Phase | Work |
|---|---|
| C1 | Incorporate the Peruvian operating company (general regime).<br>Start the ISO 27001 management system (6–12 months; evidence comes from development).<br>Register the personal-data banks with the ANPD and communicate offshore hosting.<br>Draft the data processing agreement and the signing-authorization clause.<br>Libro de Reclamaciones.<br>Send SUNAT the written question about running PSE and OSE in one company. |
| C2 (after M2) | PSE registration: ISO 27001, 150 UIT of capital, at least 5 employees, platform certificate registered in SOL. |
| C3 (after M3) | OSE registration: 300 UIT of capital, 28 UIT bank guarantee, audit by an ISO lead auditor, SUNAT's 25-business-day test process. |

---

## 23. Open items

- Which documents SUNAT's consult service (`getStatusCdr`) covers (boletas?) and how GRE queries by document. This affects §9.2.2; verify against SUNAT's web-service manuals.
- SUNAT-side throttling or WAF limits per source IP at hundreds of concurrent calls; static egress IPs (§25).

- The infrastructure/DR project (§25.1): cloud choice (AWS or other), synchronous-replication topology, HSM clustering, region-failover runbook.

- Whether SUNAT requires IOFE-accredited certificates for CPE. The source norms are image-only scans; OCR them or ask counsel.
- The exact article of the Reglamento de Comprobantes de Pago that sets the S/ 700 boleta threshold.
- Whether DU 013-2020 art. 8.2 (tax credit on credit facturas tied to conformity) is still in force.
- HSM vendor and deployment (network HSM vs cloud-agnostic options), and its FIPS level per role.
- Whether being a PSE counts as a "critical activity" under DS 126-2025-PCM (cybersecurity incident duties). Ask counsel.
- SUNAT's written answer on running PSE and OSE in one company.
- Whether to extract a shared engine/mobile facade crate.
- Re-check the §9.2 classification against the newest published workbook (the online copy at cpe.sunat.gob.pe returned 403 to automated fetches; the local copy is v2026-02-12).
- Real per-SOL-user concurrency limits on SUNAT production (§10.2), measured in pilots.
- When a pure-Rust XSD validator can replace libxml2 (R5): run the conformance and benchmark spike once those crates mature.

---

## 24. Resilience to SUNAT failures

SUNAT will fail: outages, slowness, timeouts after accepting a document, garbage responses, authentication-service failures. The architecture assumes this, so **any document can always be retried until SUNAT gives a definitive answer**.

### 24.1 Principles

1. **Intake never depends on SUNAT.** Emit builds, signs and stores locally and replies `201 pending_send`, whatever SUNAT's state.
2. **The document carries its own retry state.**
   - Every non-definitive document has `next_attempt_at` and `attempts`.
   - Jobs are only disposable attempts.
   - The reconciler (§24.2) is the **only** component that creates attempts.
   - Retries therefore survive anything that loses a job: a worker crash, a job that fails because of a bug, a purge, a lost `NOTIFY`, a bad deploy, restoring the job table from backup.
3. **Never give up automatically.**
   - Retries continue until a definitive answer, a permanent error (§9.2), or an explicit hold by an operator or tenant.
   - Repeated identical failures are flagged `needs_attention` but still retried every 6 hours (§9.2.4).
   - A document past its legal deadline is still sent, flagged as late; the tenant is alerted well before.
4. **Same bytes, always**, except the documented lost-ticket summary case (§9.2.2).
5. **Ambiguity is resolved by asking, not by resending blindly**, using the per-document-type table in §9.2.2.
6. **Retries cost SUNAT nothing while it is down.**
   - Each endpoint has a circuit breaker.
   - While it is open, the reconciler simply leaves that endpoint's documents out of its query. No rows are rewritten, only half-open probes are sent, and recovery isn't a thundering herd.
7. **Ordered drain.** When SUNAT recovers, documents go out **earliest legal deadline first**, within each endpoint's concurrency cap and the credential slots, with jittered backoff.
8. **Manual control.**
   - `POST /documents/{id}/retry` clears the hold and sets `next_attempt_at = now()`.
   - Bulk retry by filter (company, status, date range).
   - Hold or release per document, company, tenant or endpoint.
   - The same actions exist in `xhandler-admin` (§15).
9. **Visibility.**
   - Metrics: backlog size and oldest pending age per endpoint, deadlines at risk, breaker state, drain rate, documents in `needs_attention`.
   - A `document.delayed` webhook and tenant notifications (§26.6).
   - A SUNAT health panel in xhandler-ui; on-call paging (§26.4).
10. **OSE role.** Forwarding to SUNAT uses the same machinery at priority 1 and balances across SUNAT's two OSE reception endpoints (RS 117-2017 annex C). Breaching the 1-hour limit pages on-call.

### 24.2 Reconciler

- A singleton under `pg_try_advisory_xact_lock(RECONCILE)` runs every second and on `NOTIFY xhandler_reconcile`.
- **Endpoint state.** It alone owns the `sunat_endpoint` rows: breaker state (closed / open / half-open with cap 1) and a fixed, configured concurrency cap. It computes the breaker from the outcomes of the last 30 seconds of attempts. Workers never write these rows, so there's no hot row.
- **Lease and create**, in one transaction:

  ```sql
  WITH due AS (
    SELECT id FROM document
    WHERE status IN ('pending_send','in_progress','unknown')
      AND next_attempt_at <= now()
      AND hold_reason IS DISTINCT FROM 'operator'
      AND endpoint NOT IN (:open_or_paused_endpoints)
    ORDER BY deadline_at
    LIMIT :free_capacity            -- per endpoint, from caps and credential slots
    FOR UPDATE SKIP LOCKED)
  UPDATE document d SET next_attempt_at = now() + :lease   -- e.g. 5 minutes
  FROM due WHERE d.id = due.id
  RETURNING d.id;
  -- then insert one attempt job per returned document, in the same transaction
  ```

  - Because of the lease, a document never gets a second attempt while one is active: it doesn't look due until the lease expires.
  - The attempt's outcome overwrites `next_attempt_at` (or clears it on a definitive answer).
  - If the worker dies, the lease expires and the next pass retries the document. There's no "has an active job" check and no race between a finishing job and the reconciler.
- **Index.** A partial index on `(deadline_at) WHERE status IN ('pending_send','in_progress','unknown')`, filtered on `next_attempt_at`. It stays tiny in steady state and holds only the backlog during outages.
- Voiding originals (`voiding`) are not reconciled; their RA/RC/RR document carries the retry state. Boletas moved into an RC have `next_attempt_at` cleared (§9.2.4).
- **Deferred to post-v1, if metrics ask for them:** adaptive concurrency (AIMD) and tenant fairness. Facturas have a 3-day deadline, so minutes of unfairness during a drain are harmless.

### 24.3 Testing

The SUNAT fake (§15.2) simulates:

- a full outage;
- partial outages (5xx, timeouts);
- accept-then-timeout;
- 1033 after a lost response, including a 1033 with a different digest;
- lost tickets;
- slow responses;
- recovery.

Tests assert that no document is lost or double-attempted, nothing is sent after a definitive answer, the drain is ordered by deadline, stale workers can't overwrite outcomes, and the backlog drains within the expected time.

---

## 25. Capacity

Target: **thousands of documents per minute**. Design figures: 5,000/minute sustained (about 83/second), 50,000/minute burst at intake.

| Area | Implication |
|---|---|
| Intake | Stateless api pods scale horizontally; emit is local (engine + HSM + database + S3), bounded by an emit semaphore and HSM sessions |
| Engine | 5 ms p99 per document on one core (§15) for one build + validate + sign-prepare/finalize, excluding the HSM signature and localhost HTTP round trips. Scales with pods. |
| HSM | About 85 signatures/second sustained, about 850/second in bursts. Size the HSM cluster for bursts. Keep each tenant key unwrapped as a session object, cached per HSM session (LRU). |
| Postgres rows | About 7.2M `document` rows/day, about 2.6B/year, kept 10 years. **`document` is partitioned by issue year** (the uniqueness rule includes that key; series counters are monotonic, so a number can't repeat across years). `document_event`, `job`, `audit_log` and `delivery` are partitioned by month. Large JSON (`input_json`, `enriched_json`) lives in S3, not in rows. |
| Postgres writes | About 15–20 rows per document: roughly 1.5k writes/second sustained, over 7k/second during intake bursts. Size and benchmark the primary for that. `NOTIFY`s are coalesced to one per transaction. |
| SUNAT drain | Roughly 85 documents/second at ~1–2 s latency needs about 100–200 calls in flight across credentials. PSE needs a large enough pool of platform SOL users. |
| Outage backlog | 4 hours at 5,000/minute is about 1.2M pending documents. Draining at about 200/second while intake continues at about 83/second clears it in about **3 hours**, earliest deadline first. |
| SUNAT-side limits | SUNAT may throttle or block per source IP at hundreds of concurrent calls. Egress uses static IPs. The real limits are measured in pilots (§23). |
| S3 | Several objects per document (XML, CDR, JSON, PDF on demand). Content-addressed, so duplicates are free. |

P11 load tests run at these figures, including an outage followed by a drain.


### 25.1 Disaster recovery

**Targets: lose nothing (RPO = 0) and no downtime (RTO ≈ 0).**

- **Who provides them.** The infrastructure layer delivers these targets: Kubernetes plus the cloud provider (e.g. AWS), designed and operated separately from the application.
- **What "no downtime" means in practice.** Literal zero downtime isn't physically achievable. The target is automatic failover in seconds that no tenant notices: in-flight requests may be retried, but none are lost.
- **What the application guarantees.** It is built so that any infrastructure failover is safe.

**Infrastructure layer (separate project):**

| Component | Requirement |
|---|---|
| Postgres | **Synchronous replication** to at least one standby in another zone (CloudNativePG synchronous standbys, or Aurora/RDS Multi-AZ with synchronous commit). A commit is acknowledged only once it is on two zones, which gives RPO = 0. Automatic failover; point-in-time recovery to object storage; a cross-region replica for regional disasters. |
| Object storage | Multi-zone by default (S3); cross-region replication for files (XML, CDR, JSON). Content-addressed, so replication is idempotent. |
| HSM | A clustered HSM across zones, with key material replicated within the cluster. Tenant keys are wrapped and stored in Postgres (§6), so they are covered by its replication. |
| Kubernetes | api, worker and engine pods spread across at least three zones (topology spread constraints, PodDisruptionBudgets); rolling deploys; no single-replica components. |
| Keycloak, Lago, gateway, egress proxy | Each with several replicas across zones, and its own database replicated the same way. |
| Region failover | Documented and drilled (P11). Promoting the cross-region replica is a deliberate operator decision, because the remote region is asynchronous. |

**Application guarantees (this design):**

- **Stateless pods.** All state is in Postgres, object storage or the HSM, so losing any pod or zone loses no data.
- **A commit means durable.** A `201` on emit is returned only after the transaction commits, and with synchronous replication that means it's on two zones. A document that was answered can't be lost by a failover.
- **Failover-tolerant connections.**
  - Database connections retry on failover with backoff.
  - The lock connection's loss is handled as in §10.4: finish in-flight calls, write fenced outcomes, exit, restart.
  - Recovery and the reconciler's lease (§24.2) resume every interrupted attempt, so no SUNAT send is lost and none is duplicated.
- **Idempotent clients.** `Idempotency-Key` (§11.1) lets clients safely retry requests that failed during a failover.
- **Fencing and document state** (§9.2, §10.4) make work interrupted at any point safe to resume.
- **Tests.** Chaos tests in staging (§15.2) include failing over Postgres, losing a zone and restarting the HSM during load, and assert no lost or duplicated documents.


---

## 26. Operations, support and developer experience

### 26.1 Support access

- Platform operators can't see tenant data by default.
- A support session is requested, approved by the tenant (or granted by a documented break-glass procedure), limited in time (e.g. 4 hours), read-only by default, and fully audited (§16). The tenant can see the audit.
- This is required by ISO 27001 and the data protection rules.

### 26.2 Developer experience

- A public API reference generated from the OpenAPI spec.
- SDKs generated and published for **TypeScript and Java** in v1; other languages later, from the same OpenAPI spec.
- **Sandbox**: every new tenant starts on the **SUNAT fake**, with deterministic answers (accepted, rejected, 1033, outage). SUNAT beta is opt-in and uses the tenant's own SOL user. The shared demo credential would give every free tenant a single slot (N = 1), about one document per second for the whole platform.
- Quickstarts per integration style: ERP, POS, the Nubefact adapter.
- Webhook tools: a test-event sender, delivery logs and redelivery.
- A public status page for platform and SUNAT health.

### 26.3 Release and deployment

- Environments: `dev` (Dev Services), `staging` (SUNAT beta, production-like), `production`.
- GitOps (Argo CD) with container images signed and scanned in CI.
- Rolling deploys under the expand/contract migration rule (§15.1).
- The engine's xhandler-rust dependency is pinned by revision. Bumping it is its own PR, gated on the golden files and the engine benchmarks.
- Feature flags only for staged rollouts, not for regulation dates (§3.1).

### 26.4 Tool choices

| Need | Choice |
|---|---|
| Metrics, logs, traces | Prometheus + Grafana, Loki, Tempo (through the OpenTelemetry Collector) |
| Alerting and on-call | Alertmanager → an on-call pager (PagerDuty or Opsgenie); runbooks linked from every alert, including "documents stuck in needs_attention" and "OSE 1-hour SLA at risk" |
| API gateway (rate limits, bot protection, CAPTCHA on public endpoints) | Envoy Gateway (Kubernetes Gateway API) |
| Platform secrets and the HSM PIN | External Secrets Operator from the platform vault; the HSM PIN is never in images or Git |
| Egress proxy (§16) | A dedicated proxy deployment (Envoy) plus Kubernetes NetworkPolicies |
| Database HA | A Postgres operator (CloudNativePG) with point-in-time recovery to S3 |

### 26.5 Email deliverability

- The platform sender domain has SPF, DKIM and DMARC.
- Tenants with their own domain verify it through DNS records (DKIM/SPF) before sending as that domain.
- Bounces and complaints are processed. Recipients that hard-bounce are suppressed per tenant, and the tenant is told.

### 26.6 Tenant notifications

Email and in-app (xhandler-ui), separate from webhooks, configurable per tenant and role:

- documents in `exception`;
- documents delayed or at risk of missing a deadline;
- rejected documents that had already been delivered;
- certificate expiring in 30, 7 or 1 days;
- PSE authorization revoked;
- quota at 80% and 100%;
- conformity objected;
- SIRE deadlines;
- SUNAT outage started or ended.

### 26.7 Exchange rates

A daily job imports the SBS/SUNAT exchange rate (purchase and sale). It's used for the S/ 700 boleta check, the PEN equivalents in reports, and as a default `tipo_cambio` on manual issuing.

### 26.8 Free-tier abuse controls

- Signup needs a verified email. Until the RUC is verified (§6), a tenant has low limits: beta only, a small daily quota, no custom SMTP and no webhooks to arbitrary hosts.
- Signup and API calls are rate limited per IP and per account at the gateway.
- Abuse signals (many tenants from one source, RUC ownership failures) are flagged for review.

### 26.9 Repo conventions

- xhandler-service gets `AGENTS.md`/`CLAUDE.md` mirroring xhandler-rust's conventions:
  - SUNAT and domain concepts in Spanish (`Detalle`, `tipoComprobante`), infrastructure in English (`Job`, `Worker`, `…Service`, `…Repository`, `…Error`);
  - acronyms as words (`Igv`, `Sunat`, `Ruc`);
  - Java naming per Google Java Style.
- **User-facing text (UI, API error messages, emails, PDFs) is Spanish.** Logs and code are English.
- xhandler-ui follows pulp-ui-2's rules and skills (§18).

---

## Appendix A. Patterns taken from pulpcore

| pulpcore | Here |
|---|---|
| `BaseModel` (UUIDv7 `pulp_id`, created/updated) | UUIDv7 ids and timestamps on every table |
| `Domain`: multi-tenancy in every uniqueness rule, encrypted settings, protected deletion | `tenant` (plus partner/company), the same rules |
| Tasking: Postgres as the only source of truth, no-loop state machine, exclusive/shared resources, FIFO, heartbeats, abandoned-task cleanup, LISTEN/NOTIFY, `TaskSchedule`, purge | §10 |
| `pulpcore-api` / `pulpcore-worker` from one codebase | `api` / `worker` roles of one image |
| Correlation id in logs and on tasks; OTel metrics, including unblocked-waiting tasks | §15 |
| Status endpoint (versions, workers, database, storage) | `/api/v1/status` |
| CloudEvents for task status on Kafka | CloudEvents webhooks |
| `EncryptedJSONField` + MultiFernet + `rotate-db-key` | Envelope encryption with several master keys + `rotate-keys` |
| Content-addressed artifacts (sha256) | Content-addressed files; retention instead of orphan cleanup |
| 202 + task href, labels, PRN identifiers, generated bindings | §12 |
| Management commands (data repair, reset admin password) | `xhandler-admin` |

**Not taken:** the plugin system, access policies stored as data, the Redis worker, the content-serving app, and chunked uploads.
