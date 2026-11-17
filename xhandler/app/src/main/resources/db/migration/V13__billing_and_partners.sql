-- P6: billing (Lago), partners and developer experience (DESIGN.md §1.2, §14, §26.2, §26.8).

-- Plans and their quotas. Lago owns prices and invoices; the service enforces quotas.
-- NULL = unlimited.
CREATE TABLE xhandler.plan (
    code                    text PRIMARY KEY,
    name                    text    NOT NULL,
    production_per_month    int,
    beta_per_day            int,
    sandbox_per_day         int,
    custom_smtp             boolean NOT NULL DEFAULT true,
    webhooks                boolean NOT NULL DEFAULT true
);
GRANT SELECT ON xhandler.plan TO xhandler_app;
INSERT INTO xhandler.plan (code, name, production_per_month, beta_per_day, sandbox_per_day, custom_smtp, webhooks) VALUES
    ('free', 'Gratis', 100, 100, 1000, false, true),
    ('starter', 'Emprendedor', 1000, 1000, 5000, true, true),
    ('business', 'Empresa', 20000, 5000, 20000, true, true),
    ('enterprise', 'Corporativo', NULL, NULL, NULL, true, true);

-- A tenant's entitlement, synced from Lago and cached (§14). If Lago is
-- unreachable the cache is used for 24 hours, then quotas fall back to the
-- free plan (fail closed, §11.1).
CREATE TABLE xhandler.entitlement (
    tenant_id     uuid PRIMARY KEY REFERENCES xhandler.tenant (id),
    plan_code     text        NOT NULL REFERENCES xhandler.plan (code),
    source        text        NOT NULL DEFAULT 'default' CHECK (source IN ('default', 'lago', 'admin')),
    synced_at     timestamptz NOT NULL DEFAULT now(),
    notified_80   text,
    notified_100  text
);
ALTER TABLE xhandler.entitlement ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.entitlement
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.entitlement TO xhandler_app;

-- Who the platform invoices (its own comprobantes, §14)
ALTER TABLE xhandler.tenant ADD COLUMN billing_ruc text;
ALTER TABLE xhandler.tenant ADD COLUMN billing_name text;
ALTER TABLE xhandler.tenant ADD COLUMN billing_email text;

-- Partners manage their tenants through the API (§1.2). Platform data.
CREATE TABLE xhandler.partner_member (
    partner_id uuid        NOT NULL REFERENCES xhandler.partner (id),
    kind       text        NOT NULL CHECK (kind IN ('user', 'client')),
    subject    text        NOT NULL,
    role       text        NOT NULL CHECK (role IN ('admin', 'viewer')),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (partner_id, kind, subject)
);
ALTER TABLE xhandler.partner ADD COLUMN status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended'));
GRANT SELECT ON xhandler.partner_member TO xhandler_app;
GRANT INSERT, UPDATE ON xhandler.partner TO xhandler_app;
GRANT INSERT, UPDATE, DELETE ON xhandler.partner_member TO xhandler_app;

-- Platform settings changed at runtime (e.g. which company issues the platform's own invoices)
CREATE TABLE xhandler.platform_setting (
    key        text PRIMARY KEY,
    value      text        NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON xhandler.platform_setting TO xhandler_app;

-- Lago invoices already issued as comprobantes (idempotent self-invoicing)
CREATE TABLE xhandler.lago_invoice (
    lago_invoice_id text PRIMARY KEY,
    tenant_id       uuid        NOT NULL,
    document_id     uuid,
    created_at      timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON xhandler.lago_invoice TO xhandler_app;

-- Cross-tenant functions (SECURITY DEFINER) -----------------------------------

-- A partner member's role in a tenant of that partner: partner admins administer their
-- tenants, partner viewers see them (§1.2)
CREATE FUNCTION xhandler.partner_role(p_tenant uuid, p_kind text, p_subject text) RETURNS text
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT CASE pm.role WHEN 'admin' THEN 'admin' ELSE 'viewer' END
    FROM tenant t JOIN partner p ON p.id = t.partner_id JOIN partner_member pm ON pm.partner_id = p.id
    WHERE t.id = p_tenant AND pm.kind = p_kind AND pm.subject = p_subject
      AND t.deleted_at IS NULL AND t.status = 'active' AND p.status = 'active'
$$;

-- Partners a subject belongs to
CREATE FUNCTION xhandler.partners_of(p_kind text, p_subject text) RETURNS TABLE (id uuid, name text, role text)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT p.id, p.name, pm.role FROM partner_member pm JOIN partner p ON p.id = pm.partner_id
    WHERE pm.kind = p_kind AND pm.subject = p_subject AND p.status = 'active' ORDER BY p.name
$$;

-- A partner's tenants with their usage in a period (partners see their tenants' usage, §14)
CREATE FUNCTION xhandler.partner_tenants(p_partner uuid, p_from timestamptz, p_to timestamptz)
    RETURNS TABLE (id uuid, name text, status text, plan_code text, production_documents bigint, other_documents bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT t.id, t.name, t.status, coalesce(e.plan_code, 'free'),
           (SELECT count(*) FROM document d WHERE d.tenant_id = t.id AND d.sunat_env = 'production' AND d.origin = 'api'
              AND d.created_at >= p_from AND d.created_at < p_to),
           (SELECT count(*) FROM document d WHERE d.tenant_id = t.id AND d.sunat_env <> 'production' AND d.origin = 'api'
              AND d.created_at >= p_from AND d.created_at < p_to)
    FROM tenant t LEFT JOIN entitlement e ON e.tenant_id = t.id
    WHERE t.partner_id = p_partner AND t.deleted_at IS NULL ORDER BY t.name
$$;

-- Creates a tenant under a partner (partner API), owned by the given subject
CREATE FUNCTION xhandler.create_partner_tenant(p_partner uuid, p_id uuid, p_name text, p_created_by text,
        p_owner_kind text, p_owner_subject text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
BEGIN
    INSERT INTO tenant (id, partner_id, name, plan, created_by) VALUES (p_id, p_partner, p_name, 'partner', p_created_by);
    IF p_owner_subject IS NOT NULL THEN
        INSERT INTO tenant_member (id, tenant_id, kind, subject, role)
        VALUES (gen_random_uuid(), p_id, p_owner_kind, p_owner_subject, 'owner');
    END IF;
    INSERT INTO entitlement (tenant_id, plan_code, source) VALUES (p_id, 'free', 'default');
END
$$;

-- Usage events not delivered to Lago yet, oldest first (the delivery job)
CREATE FUNCTION xhandler.unsent_usage_events(p_limit int)
    RETURNS TABLE (id uuid, tenant_id uuid, event_type text, quantity int, properties jsonb, created_at timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT id, tenant_id, event_type, quantity, properties, created_at FROM usage_event_outbox
    WHERE sent_at IS NULL ORDER BY created_at LIMIT p_limit
$$;

CREATE FUNCTION xhandler.mark_usage_sent(p_ids uuid[]) RETURNS int
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    WITH sent AS (UPDATE usage_event_outbox SET sent_at = now() WHERE id = ANY (p_ids) AND sent_at IS NULL RETURNING 1)
    SELECT count(*)::int FROM sent
$$;

-- Tenants whose entitlement is due a sync with Lago
CREATE FUNCTION xhandler.entitlements_to_sync(p_older_than interval, p_limit int) RETURNS TABLE (tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT t.id FROM tenant t LEFT JOIN entitlement e ON e.tenant_id = t.id
    WHERE t.deleted_at IS NULL AND (e.tenant_id IS NULL OR (e.source <> 'admin' AND e.synced_at < now() - p_older_than))
    ORDER BY e.synced_at NULLS FIRST LIMIT p_limit
$$;

-- The partner of a tenant (white-label branding in emails)
CREATE FUNCTION xhandler.tenant_partner(p_tenant uuid) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT partner_id FROM tenant WHERE id = p_tenant $$;

DO $$
DECLARE
    f text;
BEGIN
    FOREACH f IN ARRAY ARRAY['partner_role(uuid,text,text)', 'partners_of(text,text)', 'partner_tenants(uuid,timestamptz,timestamptz)',
        'create_partner_tenant(uuid,uuid,text,text,text,text)', 'unsent_usage_events(int)', 'mark_usage_sent(uuid[])',
        'entitlements_to_sync(interval,int)', 'tenant_partner(uuid)']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION xhandler.%s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION xhandler.%s TO xhandler_app', f);
    END LOOP;
END
$$;

-- Partner tenants don't count toward a person's one free tenant
DROP INDEX xhandler.tenant_one_free_per_person;
CREATE UNIQUE INDEX tenant_one_free_per_person ON xhandler.tenant (created_by)
    WHERE plan = 'free' AND deleted_at IS NULL AND partner_id IS NULL;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('usage-delivery', 'billing.usage-delivery', 60, 4),
    ('entitlement-sync', 'billing.entitlement-sync', 900, 4);
