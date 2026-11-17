-- P9: tax compliance — registers, SIRE, factura negociable, reports (DESIGN.md §11.7–§11.9).

-- Amounts by tax (from the signed XML) for the registers of sales and purchases
ALTER TABLE xhandler.document ADD COLUMN amounts jsonb;
-- contado / credito: credit facturas are facturas negociables (§11.8)
ALTER TABLE xhandler.document ADD COLUMN payment_terms text CHECK (payment_terms IN ('contado', 'credito'));
ALTER TABLE xhandler.reception ADD COLUMN amounts jsonb;
ALTER TABLE xhandler.reception ADD COLUMN affected_serie_numero text;
ALTER TABLE xhandler.reception ADD COLUMN affected_tipo text;

-- One register (RVIE sales, RCE purchases) of one company and period in SIRE
CREATE TABLE xhandler.sire_period (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id         uuid        NOT NULL REFERENCES xhandler.company (id),
    sunat_env          text        NOT NULL,
    book               text        NOT NULL CHECK (book IN ('rvie', 'rce')),
    period             text        NOT NULL CHECK (period ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    status             text        NOT NULL CHECK (status IN ('requested', 'proposal_ready', 'submitting', 'submitted', 'accepted',
                                                              'error', 'credentials')),
    proposal_ticket    text,
    proposal_ref       text,
    reconciliation     jsonb,
    replacement_ref    text,
    replacement_ticket text,
    detail             text,
    requested_by       text        NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (company_id, sunat_env, book, period)
);
ALTER TABLE xhandler.sire_period ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.sire_period
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.sire_period TO xhandler_app;
REVOKE DELETE ON xhandler.sire_period FROM xhandler_app;

-- SUNAT's calendar of monthly obligations (RS 281-2022 annex II and its yearly successors):
-- the due date of each period by the last digit of the RUC. Loaded by platform admins.
CREATE TABLE xhandler.sire_calendar (
    period         text NOT NULL CHECK (period ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    ruc_last_digit smallint NOT NULL CHECK (ruc_last_digit BETWEEN 0 AND 9),
    due_date       date NOT NULL,
    PRIMARY KEY (period, ruc_last_digit)
);
GRANT SELECT, INSERT, UPDATE ON xhandler.sire_calendar TO xhandler_app;

-- Factura negociable conformity (§11.8): the buyer has 8 days after acceptance
CREATE TABLE xhandler.conformity (
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id     uuid        NOT NULL REFERENCES xhandler.company (id),
    document_id    uuid        NOT NULL UNIQUE,
    buyer_ruc      text,
    accepted_at    timestamptz NOT NULL,
    window_ends_at timestamptz NOT NULL,
    state          text        NOT NULL DEFAULT 'pending'
                       CHECK (state IN ('pending', 'agreed', 'objected', 'silent_agreement')),
    checked_at     timestamptz,
    check_detail   text,
    reminded_at    timestamptz,
    resolved_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX conformity_open ON xhandler.conformity (checked_at NULLS FIRST) WHERE state = 'pending';
ALTER TABLE xhandler.conformity ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.conformity
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.conformity TO xhandler_app;
REVOKE DELETE ON xhandler.conformity FROM xhandler_app;

-- Reports (§11.9): one row generator, several formats, run as jobs
CREATE TABLE xhandler.report (
    id           uuid PRIMARY KEY,
    tenant_id    uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id   uuid        NOT NULL REFERENCES xhandler.company (id),
    sunat_env    text        NOT NULL,
    register     text        NOT NULL CHECK (register IN ('sales', 'purchases')),
    format       text        NOT NULL CHECK (format IN ('csv', 'xlsx', 'ple', 'sire')),
    period       text        NOT NULL CHECK (period ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    status       text        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'done', 'failed')),
    file_ref     text,
    file_name    text,
    rows         int,
    detail       text,
    job_id       uuid,
    requested_by text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    done_at      timestamptz
);
CREATE INDEX report_company ON xhandler.report (company_id, created_at DESC);
ALTER TABLE xhandler.report ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.report
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.report TO xhandler_app;
REVOKE DELETE ON xhandler.report FROM xhandler_app;

-- SIRE periods waiting on a SUNAT ticket (cross-tenant, for the polling job)
CREATE FUNCTION xhandler.sire_periods_to_poll(p_limit int) RETURNS TABLE (tenant_id uuid, sire_period_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT s.tenant_id, s.id FROM sire_period s JOIN tenant t ON t.id = s.tenant_id
    WHERE s.status IN ('requested', 'submitted') AND t.status = 'active' AND t.deleted_at IS NULL
    ORDER BY s.updated_at LIMIT p_limit
$$;

-- Open conformity windows to check with SUNAT, least recently checked first
CREATE FUNCTION xhandler.conformities_to_check(p_limit int) RETURNS TABLE (tenant_id uuid, conformity_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT c.tenant_id, c.id FROM conformity c JOIN tenant t ON t.id = c.tenant_id
    WHERE c.state = 'pending' AND t.status = 'active' AND t.deleted_at IS NULL
      AND (c.checked_at IS NULL OR c.checked_at < now() - interval '6 hours' OR c.window_ends_at < now())
    ORDER BY c.checked_at NULLS FIRST LIMIT p_limit
$$;

-- Production companies whose sales register for a period is due within p_days and not accepted yet
CREATE FUNCTION xhandler.sire_deadlines_due(p_days int) RETURNS TABLE (tenant_id uuid, company_id uuid, ruc text, period text, due_date date)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT c.tenant_id, c.id, c.ruc, k.period, k.due_date
    FROM company c
    JOIN tenant t ON t.id = c.tenant_id AND t.status = 'active' AND t.deleted_at IS NULL
    JOIN sire_calendar k ON k.ruc_last_digit = right(c.ruc, 1)::smallint
    WHERE c.deleted_at IS NULL AND c.sunat_env = 'production'
      AND k.due_date BETWEEN current_date AND current_date + p_days
      AND NOT EXISTS (SELECT 1 FROM sire_period s WHERE s.company_id = c.id AND s.sunat_env = 'production'
                        AND s.book = 'rvie' AND s.period = k.period AND s.status = 'accepted')
$$;

REVOKE ALL ON FUNCTION xhandler.sire_periods_to_poll(int), xhandler.conformities_to_check(int), xhandler.sire_deadlines_due(int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.sire_periods_to_poll(int), xhandler.conformities_to_check(int), xhandler.sire_deadlines_due(int)
    TO xhandler_app;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('sire-poll', 'sire.poll', 300, 4),
    ('conformity-poll', 'conformity.poll', 3600, 4),
    ('sire-deadlines', 'sire.deadlines', 86400, 4);
