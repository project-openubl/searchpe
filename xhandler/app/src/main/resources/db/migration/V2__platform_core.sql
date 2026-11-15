-- P1 platform core (DESIGN.md §6, §7).
--
-- Tenant isolation is enforced by PostgreSQL row-level security. The app runs
-- every connection as the non-owner role xhandler_app (TenantRlsInterceptor) and
-- sets app.tenant_id per transaction (TenantAccess). A query without a tenant
-- sees no rows. Cross-tenant lookups (memberships, signup checks, support
-- sessions) go through the SECURITY DEFINER functions at the end, which run as
-- the owner.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'xhandler_app') THEN
        CREATE ROLE xhandler_app NOLOGIN;
    END IF;
END
$$;
-- The connecting user must be able to SET ROLE xhandler_app
GRANT xhandler_app TO CURRENT_USER;

CREATE FUNCTION xhandler.current_tenant() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

CREATE TABLE xhandler.partner (
    id         uuid PRIMARY KEY,
    name       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE xhandler.tenant (
    id              uuid PRIMARY KEY,
    partner_id      uuid REFERENCES xhandler.partner (id),
    name            text        NOT NULL,
    plan            text        NOT NULL DEFAULT 'free',
    status          text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    retention_years int         NOT NULL DEFAULT 10,
    audit_hold      boolean     NOT NULL DEFAULT false,
    created_by      text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);

-- A person (subject = token "sub") or a machine client (subject = token "azp")
CREATE TABLE xhandler.tenant_member (
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES xhandler.tenant (id),
    kind       text        NOT NULL CHECK (kind IN ('user', 'client')),
    subject    text        NOT NULL,
    role       text        NOT NULL CHECK (role IN ('owner', 'admin', 'operator', 'viewer')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, kind, subject)
);
CREATE INDEX tenant_member_subject ON xhandler.tenant_member (kind, subject);

CREATE TABLE xhandler.company (
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES xhandler.tenant (id),
    ruc              char(11)    NOT NULL CHECK (ruc ~ '^[0-9]{11}$'),
    razon_social     text        NOT NULL,
    nombre_comercial text,
    sunat_env        text        NOT NULL DEFAULT 'beta' CHECK (sunat_env IN ('beta', 'production')),
    emission_mode    text        NOT NULL DEFAULT 'software' CHECK (emission_mode IN ('software', 'pse')),
    ruc_verified_at  timestamptz,
    active           boolean     NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz
);
-- SUNAT numbering is unique per RUC, so a RUC exists once on the whole
-- platform per environment (DESIGN.md §6). Global: not scoped by tenant.
CREATE UNIQUE INDEX company_ruc_env ON xhandler.company (ruc, sunat_env) WHERE active;
CREATE INDEX company_tenant ON xhandler.company (tenant_id);

CREATE TABLE xhandler.series (
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id       uuid        NOT NULL REFERENCES xhandler.company (id),
    sunat_env        text        NOT NULL CHECK (sunat_env IN ('beta', 'production')),
    tipo_comprobante text        NOT NULL,
    serie            text        NOT NULL CHECK (serie ~ '^[A-Z0-9]{4}$'),
    next_number      bigint      NOT NULL DEFAULT 1 CHECK (next_number >= 1),
    is_default       boolean     NOT NULL DEFAULT false,
    active           boolean     NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (company_id, sunat_env, tipo_comprobante, serie)
);

-- Secrets (SOL, GRE, SIRE, SMTP...), envelope-encrypted (DESIGN.md §6):
-- AES-256-GCM with a per-secret data key, wrapped by a master key in the vault.
CREATE TABLE xhandler.credential (
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id       uuid        NOT NULL REFERENCES xhandler.company (id),
    kind             text        NOT NULL CHECK (kind IN ('sol', 'gre', 'sire', 'validity', 'smtp', 'lookup')),
    label            text        NOT NULL,
    ciphertext       bytea       NOT NULL,
    wrapped_data_key bytea       NOT NULL,
    master_key_id    text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (company_id, kind)
);
CREATE INDEX credential_master_key ON xhandler.credential (master_key_id);

-- Signing keys: the private key lives in the key vault (HSM); only its
-- reference and the certificate are stored here.
CREATE TABLE xhandler.signing_key (
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id  uuid        NOT NULL REFERENCES xhandler.company (id),
    key_ref     text        NOT NULL,
    certificate bytea       NOT NULL,
    subject     text        NOT NULL,
    issuer      text        NOT NULL,
    serial      text        NOT NULL,
    ruc         char(11),
    valid_from  timestamptz NOT NULL,
    valid_until timestamptz NOT NULL,
    status      text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'replaced', 'revoked')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX signing_key_active ON xhandler.signing_key (company_id) WHERE status = 'active';

-- Every access to personal data and every change (DESIGN.md §16); kept ≥ 2 years
CREATE TABLE xhandler.audit_log (
    id          uuid PRIMARY KEY,
    at          timestamptz NOT NULL DEFAULT now(),
    tenant_id   uuid        NOT NULL REFERENCES xhandler.tenant (id),
    actor       text        NOT NULL,
    action      text        NOT NULL,
    object_type text        NOT NULL,
    object_id   text,
    details     jsonb
);
CREATE INDEX audit_log_tenant_at ON xhandler.audit_log (tenant_id, at);

-- Platform operators see a tenant's data only through an approved,
-- time-limited, read-only session (DESIGN.md §26.1)
CREATE TABLE xhandler.support_session (
    id           uuid PRIMARY KEY,
    tenant_id    uuid        NOT NULL REFERENCES xhandler.tenant (id),
    operator     text        NOT NULL,
    reason       text        NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT now(),
    approved_by  text,
    approved_at  timestamptz,
    expires_at   timestamptz,
    revoked_at   timestamptz
);

-- Row-level security ---------------------------------------------------------

ALTER TABLE xhandler.tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE xhandler.tenant FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.tenant
    USING (id = xhandler.current_tenant()) WITH CHECK (id = xhandler.current_tenant());

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['tenant_member', 'company', 'series', 'credential', 'signing_key', 'audit_log', 'support_session']
    LOOP
        EXECUTE format('ALTER TABLE xhandler.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE xhandler.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON xhandler.%I USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant())', t);
    END LOOP;
END
$$;

GRANT USAGE ON SCHEMA xhandler TO xhandler_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA xhandler TO xhandler_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA xhandler GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO xhandler_app;
-- The audit log is append-only for the app
REVOKE UPDATE, DELETE ON xhandler.audit_log FROM xhandler_app;
-- Partners are managed by the platform (P6)
REVOKE INSERT, UPDATE, DELETE ON xhandler.partner FROM xhandler_app;

-- Cross-tenant lookups (SECURITY DEFINER: run as the owner) ------------------

-- Role of a subject in a tenant, or NULL
CREATE FUNCTION xhandler.member_role(p_tenant uuid, p_kind text, p_subject text) RETURNS text
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT m.role FROM tenant_member m JOIN tenant t ON t.id = m.tenant_id
    WHERE m.tenant_id = p_tenant AND m.kind = p_kind AND m.subject = p_subject
      AND t.deleted_at IS NULL AND t.status = 'active'
$$;

-- Tenants a subject belongs to
CREATE FUNCTION xhandler.tenants_of(p_kind text, p_subject text)
    RETURNS TABLE (id uuid, name text, role text)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT t.id, t.name, m.role FROM tenant_member m JOIN tenant t ON t.id = m.tenant_id
    WHERE m.kind = p_kind AND m.subject = p_subject AND t.deleted_at IS NULL
    ORDER BY t.created_at
$$;

-- Free tenants a person created (signup abuse control, DESIGN.md §26.8)
CREATE FUNCTION xhandler.free_tenants_created_by(p_subject text) RETURNS bigint
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT count(*) FROM tenant WHERE created_by = p_subject AND plan = 'free' AND deleted_at IS NULL
$$;

-- True when the operator has an approved, unexpired, unrevoked session
CREATE FUNCTION xhandler.has_support_session(p_tenant uuid, p_operator text) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT EXISTS (
        SELECT FROM support_session
        WHERE tenant_id = p_tenant AND operator = p_operator
          AND approved_at IS NOT NULL AND revoked_at IS NULL AND expires_at > now())
$$;

-- An operator asks a tenant for access. Returns the session id.
CREATE FUNCTION xhandler.request_support_session(p_tenant uuid, p_operator text, p_reason text) RETURNS uuid
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
    VALUES (gen_random_uuid(), p_tenant, p_operator, 'support_session.requested', 'support_session', v_id::text);
    RETURN v_id;
END
$$;

REVOKE ALL ON FUNCTION xhandler.member_role, xhandler.tenants_of, xhandler.free_tenants_created_by,
    xhandler.has_support_session, xhandler.request_support_session FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.current_tenant, xhandler.member_role, xhandler.tenants_of,
    xhandler.free_tenants_created_by, xhandler.has_support_session, xhandler.request_support_session TO xhandler_app;

-- All tenant ids, for platform jobs that work tenant by tenant (key rotation)
CREATE FUNCTION xhandler.all_tenant_ids() RETURNS TABLE (id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT id FROM tenant WHERE deleted_at IS NULL ORDER BY id $$;
REVOKE ALL ON FUNCTION xhandler.all_tenant_ids FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.all_tenant_ids TO xhandler_app;
