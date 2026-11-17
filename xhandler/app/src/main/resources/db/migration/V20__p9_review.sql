-- P9 review fixes (DESIGN.md §11.7–§11.9).

-- SIRE periods: an "unknown" state (a replacement may have reached SUNAT), polling
-- with backoff, recovery of stale submissions, and the state to resume after credentials
ALTER TABLE xhandler.sire_period DROP CONSTRAINT sire_period_status_check;
ALTER TABLE xhandler.sire_period ADD CONSTRAINT sire_period_status_check
    CHECK (status IN ('requested', 'proposal_ready', 'submitting', 'submitted', 'accepted', 'error', 'credentials', 'unknown'));
ALTER TABLE xhandler.sire_period ADD COLUMN submitting_since timestamptz;
ALTER TABLE xhandler.sire_period ADD COLUMN poll_attempts int NOT NULL DEFAULT 0;
ALTER TABLE xhandler.sire_period ADD COLUMN next_poll_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE xhandler.sire_period ADD COLUMN resume_status text;

CREATE OR REPLACE FUNCTION xhandler.sire_periods_to_poll(p_limit int) RETURNS TABLE (tenant_id uuid, sire_period_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT s.tenant_id, s.id FROM sire_period s JOIN tenant t ON t.id = s.tenant_id
    WHERE t.status = 'active' AND t.deleted_at IS NULL AND s.next_poll_at <= now()
      AND (s.status IN ('requested', 'submitted')
           OR (s.status = 'submitting' AND s.submitting_since < now() - interval '10 minutes'))
    ORDER BY s.next_poll_at LIMIT p_limit
$$;

-- Deadline notices, once per company and period
CREATE TABLE xhandler.sire_deadline_notice (
    tenant_id   uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id  uuid        NOT NULL REFERENCES xhandler.company (id),
    period      text        NOT NULL,
    notified_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, period)
);
ALTER TABLE xhandler.sire_deadline_notice ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.sire_deadline_notice
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT ON xhandler.sire_deadline_notice TO xhandler_app;

-- Conformity: closed when the factura is voided; not polled forever
ALTER TABLE xhandler.conformity DROP CONSTRAINT conformity_state_check;
ALTER TABLE xhandler.conformity ADD CONSTRAINT conformity_state_check
    CHECK (state IN ('pending', 'agreed', 'objected', 'silent_agreement', 'closed'));

CREATE OR REPLACE FUNCTION xhandler.conformities_to_check(p_limit int) RETURNS TABLE (tenant_id uuid, conformity_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT c.tenant_id, c.id FROM conformity c JOIN tenant t ON t.id = c.tenant_id
    WHERE c.state = 'pending' AND t.status = 'active' AND t.deleted_at IS NULL
      AND c.window_ends_at > now() - interval '3 days'
      AND (c.checked_at IS NULL OR c.checked_at < now() - interval '6 hours'
           OR (c.window_ends_at < now() AND c.checked_at < now() - interval '1 hour'))
    ORDER BY c.checked_at NULLS FIRST LIMIT p_limit
$$;

-- Documents and receptions created before V19 have no amounts by tax: completed by a job
CREATE FUNCTION xhandler.documents_without_amounts(p_limit int) RETURNS TABLE (tenant_id uuid, document_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT tenant_id, id FROM document
    WHERE amounts IS NULL AND tipo_comprobante IN ('01', '03', '07', '08')
      AND status IN ('accepted', 'accepted_with_observations', 'voiding', 'voided')
    ORDER BY created_at DESC LIMIT p_limit
$$;
CREATE FUNCTION xhandler.receptions_without_amounts(p_limit int) RETURNS TABLE (tenant_id uuid, reception_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT tenant_id, id FROM reception WHERE amounts IS NULL ORDER BY created_at DESC LIMIT p_limit
$$;
REVOKE ALL ON FUNCTION xhandler.documents_without_amounts(int), xhandler.receptions_without_amounts(int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.documents_without_amounts(int), xhandler.receptions_without_amounts(int) TO xhandler_app;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('amounts-backfill', 'documents.amounts-backfill', 600, 4);
