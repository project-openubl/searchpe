-- P4: all documents, voiding, routes, exchange rates (DESIGN.md §3, §9.3, §11.3, §26.7).

-- Daily exchange rate (SUNAT/SBS, §26.7). Platform data: no tenant.
CREATE TABLE xhandler.exchange_rate (
    day        date PRIMARY KEY,
    compra     numeric(10, 4) NOT NULL,
    venta      numeric(10, 4) NOT NULL,
    source     text           NOT NULL,
    fetched_at timestamptz    NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON xhandler.exchange_rate TO xhandler_app;

-- Submission route (§3.2): a regulatory status the service enforces. A company
-- using an OSE can't also send directly; OSE-obligated companies must use one.
-- GRE always goes to SUNAT.
ALTER TABLE xhandler.company ADD COLUMN submission_route text NOT NULL DEFAULT 'sunat' CHECK (submission_route IN ('sunat', 'ose'));
ALTER TABLE xhandler.company ADD COLUMN ose_obligated boolean NOT NULL DEFAULT false;

-- The third-party OSE a company sends through; its credentials are a credential of kind 'ose'
CREATE TABLE xhandler.ose_endpoint (
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id     uuid        NOT NULL UNIQUE REFERENCES xhandler.company (id),
    name           text        NOT NULL,
    invoice_url    text        NOT NULL,
    perception_url text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE xhandler.ose_endpoint ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.ose_endpoint
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE, DELETE ON xhandler.ose_endpoint TO xhandler_app;

ALTER TABLE xhandler.credential DROP CONSTRAINT credential_kind_check,
    ADD CONSTRAINT credential_kind_check CHECK (kind IN ('sol', 'gre', 'sire', 'validity', 'smtp', 'lookup', 'ose'));

-- Why a document was voided (shown in the timeline and the RA/RC/RR line)
ALTER TABLE xhandler.document ADD COLUMN void_reason text;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('exchange-rates', 'platform.exchange-rates', 21600, 4);
