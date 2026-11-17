-- P1 review fixes.

-- Row-level security without FORCE. The app never connects as the owner:
-- production logs in as a non-owner role that is a member of xhandler_app, and
-- dev/tests SET ROLE xhandler_app (TenantRlsInterceptor). DatabaseRoleCheck
-- refuses to start otherwise. Without FORCE, the owner-run SECURITY DEFINER
-- lookups work when the owner isn't a superuser, as in production. (With FORCE
-- they saw no rows there.)
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['tenant', 'tenant_member', 'company', 'series', 'credential', 'signing_key', 'audit_log', 'support_session']
    LOOP
        EXECUTE format('ALTER TABLE xhandler.%I NO FORCE ROW LEVEL SECURITY', t);
    END LOOP;
END
$$;

-- A RUC is claimed when it is verified, not when it is typed: unverified
-- duplicates are allowed, so nobody can squat a RUC by registering it first.
-- Verification (SUNAT SOL login, P3) claims it; a second claim fails here.
DROP INDEX xhandler.company_ruc_env;
CREATE UNIQUE INDEX company_ruc_env_verified ON xhandler.company (ruc, sunat_env)
    WHERE active AND ruc_verified_at IS NOT NULL;

-- One free tenant per person, enforced by the database (no signup race)
CREATE UNIQUE INDEX tenant_one_free_per_person ON xhandler.tenant (created_by)
    WHERE plan = 'free' AND deleted_at IS NULL;

-- Key rotation must also re-wrap data of deleted tenants (kept for retention)
CREATE OR REPLACE FUNCTION xhandler.all_tenant_ids() RETURNS TABLE (id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT id FROM tenant ORDER BY id $$;

-- Suspended or deleted tenants aren't listed (member_role refuses them anyway)
CREATE OR REPLACE FUNCTION xhandler.tenants_of(p_kind text, p_subject text)
    RETURNS TABLE (id uuid, name text, role text)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT t.id, t.name, m.role FROM tenant_member m JOIN tenant t ON t.id = m.tenant_id
    WHERE m.kind = p_kind AND m.subject = p_subject AND t.deleted_at IS NULL AND t.status = 'active'
    ORDER BY t.created_at
$$;

-- Same audit actor format as the rest of the log ("user:<sub>")
CREATE OR REPLACE FUNCTION xhandler.request_support_session(p_tenant uuid, p_operator text, p_reason text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    v_id uuid := gen_random_uuid();
BEGIN
    IF NOT EXISTS (SELECT FROM tenant WHERE id = p_tenant AND deleted_at IS NULL) THEN
        RETURN NULL;
    END IF;
    INSERT INTO support_session (id, tenant_id, operator, reason) VALUES (v_id, p_tenant, p_operator, p_reason);
    INSERT INTO audit_log (id, tenant_id, actor, action, object_type, object_id)
    VALUES (gen_random_uuid(), p_tenant, 'user:' || p_operator, 'support_session.requested', 'support_session', v_id::text);
    RETURN v_id;
END
$$;
