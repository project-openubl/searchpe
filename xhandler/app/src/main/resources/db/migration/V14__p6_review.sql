-- P6 review fixes (DESIGN.md §1.2, §6, §14).

-- V2's default privileges gave the app role full write access to V13's tables.
-- Narrow them: plans are reference data; partners and their members are
-- written only through SECURITY DEFINER functions (§6), never ad hoc.
REVOKE INSERT, UPDATE, DELETE ON xhandler.plan FROM xhandler_app;
REVOKE DELETE ON xhandler.platform_setting, xhandler.lago_invoice, xhandler.entitlement FROM xhandler_app;
REVOKE INSERT, UPDATE, DELETE ON xhandler.partner, xhandler.partner_member FROM xhandler_app;

CREATE FUNCTION xhandler.create_partner(p_id uuid, p_name text) RETURNS void
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ INSERT INTO partner (id, name) VALUES (p_id, p_name) $$;

CREATE FUNCTION xhandler.set_partner_member(p_partner uuid, p_kind text, p_subject text, p_role text) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (SELECT FROM partner WHERE id = p_partner) THEN
        RETURN false;
    END IF;
    INSERT INTO partner_member (partner_id, kind, subject, role) VALUES (p_partner, p_kind, p_subject, p_role)
    ON CONFLICT (partner_id, kind, subject) DO UPDATE SET role = excluded.role;
    RETURN true;
END
$$;

CREATE FUNCTION xhandler.remove_partner_member(p_partner uuid, p_kind text, p_subject text) RETURNS boolean
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    WITH gone AS (DELETE FROM partner_member WHERE partner_id = p_partner AND kind = p_kind AND subject = p_subject RETURNING 1)
    SELECT count(*) > 0 FROM gone
$$;

CREATE FUNCTION xhandler.set_partner_branding(p_partner uuid, p_branding jsonb) RETURNS void
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ UPDATE partner SET branding = p_branding, updated_at = now() WHERE id = p_partner $$;

-- One bad usage event must not block the others: failed events are set aside
ALTER TABLE xhandler.usage_event_outbox ADD COLUMN error text;

CREATE OR REPLACE FUNCTION xhandler.unsent_usage_events(p_limit int)
    RETURNS TABLE (id uuid, tenant_id uuid, event_type text, quantity int, properties jsonb, created_at timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT id, tenant_id, event_type, quantity, properties, created_at FROM usage_event_outbox
    WHERE sent_at IS NULL AND error IS NULL ORDER BY created_at LIMIT p_limit
$$;

CREATE FUNCTION xhandler.mark_usage_failed(p_id uuid, p_error text) RETURNS void
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ UPDATE usage_event_outbox SET error = left(p_error, 500) WHERE id = p_id $$;

-- Only Lago customers are synced (Lago's subscription webhooks set them); the
-- periodic sync is the safety net, oldest first
CREATE OR REPLACE FUNCTION xhandler.entitlements_to_sync(p_older_than interval, p_limit int) RETURNS TABLE (tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT e.tenant_id FROM entitlement e JOIN tenant t ON t.id = e.tenant_id
    WHERE t.deleted_at IS NULL AND e.source = 'lago' AND e.synced_at < now() - p_older_than
    ORDER BY e.synced_at LIMIT p_limit
$$;

DO $$
DECLARE
    f text;
BEGIN
    FOREACH f IN ARRAY ARRAY['create_partner(uuid,text)', 'set_partner_member(uuid,text,text,text)', 'remove_partner_member(uuid,text,text)',
        'set_partner_branding(uuid,jsonb)', 'mark_usage_failed(uuid,text)']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION xhandler.%s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION xhandler.%s TO xhandler_app', f);
    END LOOP;
END
$$;
