-- P8 review fixes (DESIGN.md §5, §11.5).

-- Adapter tokens: the token's id (the actor of what it does), and never for a deleted company
CREATE FUNCTION xhandler.adapter_token_company_v2(p_hash text, p_kind text) RETURNS TABLE (tenant_id uuid, company_id uuid, label text, token_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT a.tenant_id, a.company_id, a.label, a.id FROM adapter_token a
    JOIN tenant t ON t.id = a.tenant_id
    JOIN company c ON c.id = a.company_id
    WHERE a.token_hash = p_hash AND a.kind = p_kind AND a.revoked_at IS NULL AND t.status = 'active' AND t.deleted_at IS NULL
      AND c.deleted_at IS NULL
$$;
REVOKE ALL ON FUNCTION xhandler.adapter_token_company_v2(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.adapter_token_company_v2(text, text) TO xhandler_app;
REVOKE EXECUTE ON FUNCTION xhandler.adapter_token_company(text, text) FROM xhandler_app;

-- Receptions to check: active tenants only, least recently checked first (every check stamps
-- validity_checked_at, also when it can't be done, so nothing starves the rest)
CREATE OR REPLACE FUNCTION xhandler.receptions_to_check(p_limit int) RETURNS TABLE (tenant_id uuid, reception_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT r.tenant_id, r.id FROM reception r JOIN tenant t ON t.id = r.tenant_id
    WHERE r.validity_state IN ('unchecked', 'unknown') AND t.status = 'active' AND t.deleted_at IS NULL
      AND (r.validity_checked_at IS NULL OR r.validity_checked_at < now() - interval '1 hour')
      AND r.created_at > now() - interval '1 year'
    ORDER BY r.validity_checked_at NULLS FIRST LIMIT p_limit
$$;
