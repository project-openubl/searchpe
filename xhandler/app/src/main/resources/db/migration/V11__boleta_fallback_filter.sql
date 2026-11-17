-- The D+5 fallback needs no send_started_at window: an attempt in flight holds
-- its document resource (the job check below), and a finished attempt left a
-- recorded outcome. The window wrongly skipped boletas just answered 1079.
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
      AND NOT EXISTS (SELECT FROM job j WHERE j.state IN ('waiting', 'running', 'canceling')
                      AND j.reserved_resources @> ARRAY['document:' || d.id])
$$;
