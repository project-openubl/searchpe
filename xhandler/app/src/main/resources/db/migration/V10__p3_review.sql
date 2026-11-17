-- P3 review fixes (DESIGN.md §9.2.4, §11.1).

-- Idempotency keys are owned by the request that claimed them: complete() and
-- abandon() only touch their own claim, so a request that outlives a takeover
-- can't delete or overwrite the new owner's key.
ALTER TABLE xhandler.idempotency_key ADD COLUMN claim uuid;

-- Delivered-on-emit is a per-company choice (usually true for POS, §9.3); by
-- default the buyer doesn't have the document yet, so it can still be voided.
ALTER TABLE xhandler.company ALTER COLUMN delivered_on_emit_default SET DEFAULT false;

-- The D+5 fallback (§9.2.4) only takes boletas SUNAT can't have: pending_send
-- (never received, or answered 1079) with no attempt in flight, or exception
-- after 1079. unknown boletas are resolved first; other exceptions need the
-- user; a group whose summary couldn't be built is set aside (summary_failed).
CREATE OR REPLACE FUNCTION xhandler.boletas_needing_summary(p_today date)
    RETURNS TABLE (tenant_id uuid, company_id uuid, sunat_env text, fecha_emision date)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT DISTINCT d.tenant_id, d.company_id, d.sunat_env, d.fecha_emision FROM document d
    WHERE d.origin <> 'ose_inbound' AND d.summary_document_id IS NULL
      AND (d.tipo_comprobante = '03' OR (d.tipo_comprobante IN ('07', '08') AND d.serie LIKE 'B%'))
      AND (d.fecha_emision + 5 < p_today OR d.code_class = 'route_change')
      AND (d.status = 'pending_send' OR (d.status = 'exception' AND d.code_class = 'route_change'))
      AND d.code_class IS DISTINCT FROM 'summary_failed'
      AND d.hold_reason IS DISTINCT FROM 'operator'
      AND (d.send_started_at IS NULL OR d.send_started_at < now() - interval '3 minutes')
      AND NOT EXISTS (SELECT FROM job j WHERE j.state IN ('waiting', 'running', 'canceling')
                      AND j.reserved_resources @> ARRAY['document:' || d.id])
$$;

-- Failed RUC verifications are rate limited per RUC across tenants (no
-- password-guessing oracle against SUNAT): counts the audit entries.
CREATE FUNCTION xhandler.recent_failed_verifications(p_ruc text, p_window interval) RETURNS bigint
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT count(*) FROM audit_log
    WHERE action = 'company.ruc_verification_failed' AND details ->> 'ruc' = p_ruc AND at > now() - p_window
$$;
REVOKE ALL ON FUNCTION xhandler.recent_failed_verifications(text, interval) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.recent_failed_verifications(text, interval) TO xhandler_app;
