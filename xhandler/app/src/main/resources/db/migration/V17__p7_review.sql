-- P7 review fixes (DESIGN.md §3.2, §6).

-- A PSE authorization counts only once the platform approved it (the platform's
-- PSE status and rejected rate depend on it): tenants register, admins approve.
ALTER TABLE xhandler.pse_authorization ADD COLUMN approved_at timestamptz;
ALTER TABLE xhandler.pse_authorization ADD COLUMN approved_by text;

-- PSE signatures are traceable to the platform key that made them (§6)
ALTER TABLE xhandler.signature_log ADD COLUMN platform_key_id uuid;

-- The app only marks platform keys replaced; their reference and certificate never change
REVOKE UPDATE ON xhandler.platform_key FROM xhandler_app;
GRANT UPDATE (status) ON xhandler.platform_key TO xhandler_app;

-- The rejected-rate report scans PSE documents only
CREATE INDEX document_pse ON xhandler.document (created_at) WHERE emission_mode = 'pse';

-- Rejected rate per comprobante: no summaries and no GRE; boletas reported in a
-- resumen diario count too (by their creation), so the period is by created_at.
CREATE OR REPLACE FUNCTION xhandler.pse_outcomes(p_from timestamptz, p_to timestamptz) RETURNS TABLE (accepted bigint, rejected bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT count(*) FILTER (WHERE status IN ('accepted', 'accepted_with_observations', 'voiding', 'voided')),
           count(*) FILTER (WHERE status = 'rejected')
    FROM document
    WHERE emission_mode = 'pse' AND sunat_env = 'production' AND tipo_comprobante IN ('01', '03', '07', '08', '20', '40')
      AND created_at >= p_from AND created_at < p_to
$$;

-- Authorizations waiting for a platform admin, and their approval (cross-tenant)
CREATE FUNCTION xhandler.pending_pse_authorizations()
    RETURNS TABLE (id uuid, tenant_id uuid, company_id uuid, ruc text, razon_social text, authorized_at date, evidence_ref text, created_at timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT a.id, a.tenant_id, a.company_id, c.ruc, c.razon_social, a.authorized_at, a.evidence_ref, a.created_at
    FROM pse_authorization a JOIN company c ON c.id = a.company_id
    WHERE a.approved_at IS NULL AND a.revoked_at IS NULL ORDER BY a.created_at
$$;

CREATE FUNCTION xhandler.approve_pse_authorization(p_id uuid, p_by text) RETURNS uuid
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    UPDATE pse_authorization SET approved_at = now(), approved_by = p_by
    WHERE id = p_id AND approved_at IS NULL AND revoked_at IS NULL
    RETURNING tenant_id
$$;

CREATE FUNCTION xhandler.pse_authorization_evidence(p_id uuid) RETURNS TABLE (tenant_id uuid, evidence_ref text)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT tenant_id, evidence_ref FROM pse_authorization WHERE id = p_id $$;

REVOKE ALL ON FUNCTION xhandler.pending_pse_authorizations(), xhandler.approve_pse_authorization(uuid, text),
    xhandler.pse_authorization_evidence(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.pending_pse_authorizations(), xhandler.approve_pse_authorization(uuid, text),
    xhandler.pse_authorization_evidence(uuid) TO xhandler_app;

-- Key rotation re-wraps platform keys; their reference is otherwise immutable for the app role
CREATE FUNCTION xhandler.rewrap_platform_key(p_id uuid, p_key_ref text) RETURNS void
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ UPDATE platform_key SET key_ref = p_key_ref WHERE id = p_id $$;
REVOKE ALL ON FUNCTION xhandler.rewrap_platform_key(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.rewrap_platform_key(uuid, text) TO xhandler_app;
