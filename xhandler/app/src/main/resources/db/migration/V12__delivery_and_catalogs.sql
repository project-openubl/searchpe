-- P5: delivery and catalogs (DESIGN.md §11.6, §12, §17, §26.5, §26.6).

-- PDF and email settings of a company (§11.6)
ALTER TABLE xhandler.company ADD COLUMN pdf_format text NOT NULL DEFAULT 'a4' CHECK (pdf_format IN ('a4', 'ticket80', 'ticket58'));
ALTER TABLE xhandler.company ADD COLUMN pdf_options jsonb NOT NULL DEFAULT '{}';
ALTER TABLE xhandler.company ADD COLUMN logo_ref text;
ALTER TABLE xhandler.company ADD COLUMN direccion text;
ALTER TABLE xhandler.company ADD COLUMN email_on_accept boolean NOT NULL DEFAULT false;
ALTER TABLE xhandler.company ADD COLUMN email_subject text;
ALTER TABLE xhandler.company ADD COLUMN email_body text;
ALTER TABLE xhandler.company ADD COLUMN lookup_provider text;

-- Where tenant notifications are emailed (§26.6)
ALTER TABLE xhandler.tenant ADD COLUMN notification_email text;

-- Partner branding for PDFs and emails (§1.2, white-label)
ALTER TABLE xhandler.partner ADD COLUMN branding jsonb NOT NULL DEFAULT '{}';

-- Certificate expiry notices already sent (30, 7, 1 days)
ALTER TABLE xhandler.signing_key ADD COLUMN expiry_notified_days int;

-- Optional catalogs (§17): one table, typed by kind, validated by the app.
-- Customers, products, vehicles, drivers and addresses of a company.
CREATE TABLE xhandler.catalog_entry (
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id uuid        NOT NULL REFERENCES xhandler.company (id),
    kind       text        NOT NULL CHECK (kind IN ('customer', 'product', 'vehicle', 'driver', 'address')),
    key        text        NOT NULL,
    data       jsonb       NOT NULL,
    device_id  text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE UNIQUE INDEX catalog_entry_key ON xhandler.catalog_entry (company_id, kind, key) WHERE deleted_at IS NULL;
CREATE INDEX catalog_entry_search ON xhandler.catalog_entry USING gin (data jsonb_path_ops);

-- Tenant notifications (§26.6): in-app list, optionally emailed
CREATE TABLE xhandler.notification (
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES xhandler.tenant (id),
    kind        text        NOT NULL,
    title       text        NOT NULL,
    body        text        NOT NULL,
    document_id uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    read_at     timestamptz
);
CREATE INDEX notification_tenant ON xhandler.notification (tenant_id, created_at DESC);

CREATE TABLE xhandler.notification_preference (
    tenant_id uuid    NOT NULL REFERENCES xhandler.tenant (id),
    kind      text    NOT NULL,
    email     boolean NOT NULL DEFAULT true,
    in_app    boolean NOT NULL DEFAULT true,
    PRIMARY KEY (tenant_id, kind)
);

-- Hard bounces and complaints: never emailed again by this tenant (§26.5)
CREATE TABLE xhandler.email_suppression (
    tenant_id  uuid        NOT NULL REFERENCES xhandler.tenant (id),
    address    text        NOT NULL,
    reason     text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, address)
);

-- A tenant's own sender domain, proven by a DNS TXT record (§26.5)
CREATE TABLE xhandler.sender_domain (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid        NOT NULL REFERENCES xhandler.tenant (id),
    domain             text        NOT NULL,
    verification_token text        NOT NULL,
    verified_at        timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, domain)
);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['catalog_entry', 'notification', 'notification_preference', 'email_suppression', 'sender_domain']
    LOOP
        EXECUTE format('ALTER TABLE xhandler.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON xhandler.%I USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON xhandler.%I TO xhandler_app', t);
    END LOOP;
END
$$;

-- Public lookups by document data (§12): rate limited per RUC, platform table
CREATE TABLE xhandler.public_lookup_counter (
    bucket timestamptz NOT NULL,
    key    text        NOT NULL,
    hits   int         NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket, key)
);
GRANT SELECT, INSERT, UPDATE ON xhandler.public_lookup_counter TO xhandler_app;

-- SUNAT outage notices sent per endpoint (§26.6); platform table
CREATE TABLE xhandler.endpoint_alert (
    endpoint text PRIMARY KEY,
    state    text        NOT NULL,
    at       timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON xhandler.endpoint_alert TO xhandler_app;

-- Status of a document found by its printed data (lookup by data, §12):
-- status only, never the files; low-entropy, so the caller rate limits and
-- requires a CAPTCHA.
CREATE FUNCTION xhandler.public_document_status(p_ruc text, p_tipo text, p_serie text, p_numero bigint, p_fecha date, p_total numeric)
    RETURNS text
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT d.status FROM document d JOIN company c ON c.id = d.company_id
    WHERE c.ruc = p_ruc AND d.tipo_comprobante = p_tipo AND d.serie = p_serie AND d.numero = p_numero
      AND d.fecha_emision = p_fecha AND d.total = p_total AND d.sunat_env = 'production' AND d.origin <> 'ose_inbound'
    ORDER BY d.created_at DESC LIMIT 1
$$;

-- Certificates expiring within p_days that weren't notified for that threshold yet
CREATE FUNCTION xhandler.expiring_certificates(p_days int)
    RETURNS TABLE (tenant_id uuid, company_id uuid, signing_key_id uuid, valid_until timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT k.tenant_id, k.company_id, k.id, k.valid_until FROM signing_key k JOIN company c ON c.id = k.company_id
    WHERE k.status = 'active' AND c.active AND c.deleted_at IS NULL
      AND k.valid_until < now() + make_interval(days => p_days) AND k.valid_until > now() - interval '1 day'
      AND (k.expiry_notified_days IS NULL OR k.expiry_notified_days > p_days)
$$;

-- Tenants with documents waiting on an endpoint (SUNAT outage notices)
CREATE FUNCTION xhandler.tenants_waiting_on(p_endpoint text) RETURNS TABLE (tenant_id uuid, documents bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT tenant_id, count(*) FROM document
    WHERE endpoint = p_endpoint AND status IN ('pending_send', 'in_progress', 'unknown')
    GROUP BY tenant_id
$$;

DO $$
DECLARE
    f text;
BEGIN
    FOREACH f IN ARRAY ARRAY['public_document_status(text,text,text,bigint,date,numeric)', 'expiring_certificates(int)',
        'tenants_waiting_on(text)']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION xhandler.%s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION xhandler.%s TO xhandler_app', f);
    END LOOP;
END
$$;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('certificate-expiry', 'platform.certificate-expiry', 86400, 4),
    ('sunat-health', 'platform.sunat-health', 60, 3);
