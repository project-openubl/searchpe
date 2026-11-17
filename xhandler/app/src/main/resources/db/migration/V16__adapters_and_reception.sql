-- P8: adapters and supplier reception (DESIGN.md §5, §11.5).

-- Supplier invoices received by a company (the purchases side of SIRE, §11.7)
CREATE TABLE xhandler.reception (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id          uuid        NOT NULL REFERENCES xhandler.company (id),
    supplier_ruc        text        NOT NULL,
    supplier_name       text,
    tipo_comprobante    text        NOT NULL,
    serie               text        NOT NULL,
    numero              bigint      NOT NULL,
    fecha_emision       date,
    moneda              text,
    total               numeric(14, 2),
    digest_value        text,
    validity_state      text        NOT NULL DEFAULT 'unchecked'
                            CHECK (validity_state IN ('unchecked', 'valid', 'not_found', 'voided', 'rejected', 'unknown')),
    validity_checked_at timestamptz,
    xml_ref             text        NOT NULL,
    pdf_ref             text,
    cdr_ref             text,
    labels              jsonb       NOT NULL DEFAULT '{}',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (company_id, supplier_ruc, tipo_comprobante, serie, numero)
);
CREATE INDEX reception_period ON xhandler.reception (company_id, fecha_emision);
ALTER TABLE xhandler.reception ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.reception
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.reception TO xhandler_app;
REVOKE DELETE ON xhandler.reception FROM xhandler_app;

-- Tokens of the Nubefact-compatible API (§5): a client switches by changing
-- the URL and the token. Stored hashed; shown once.
CREATE TABLE xhandler.adapter_token (
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id uuid        NOT NULL REFERENCES xhandler.company (id),
    kind       text        NOT NULL CHECK (kind IN ('nubefact')),
    token_hash text        NOT NULL UNIQUE,
    label      text        NOT NULL,
    created_by text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz
);
ALTER TABLE xhandler.adapter_token ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.adapter_token
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.adapter_token TO xhandler_app;
REVOKE DELETE ON xhandler.adapter_token FROM xhandler_app;

-- The company an adapter token opens (the public adapter endpoints authenticate with it)
CREATE FUNCTION xhandler.adapter_token_company(p_hash text, p_kind text) RETURNS TABLE (tenant_id uuid, company_id uuid, label text)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT a.tenant_id, a.company_id, a.label FROM adapter_token a JOIN tenant t ON t.id = a.tenant_id
    WHERE a.token_hash = p_hash AND a.kind = p_kind AND a.revoked_at IS NULL AND t.status = 'active' AND t.deleted_at IS NULL
$$;

-- Receptions whose validity is still to be known (re-checked by a job)
CREATE FUNCTION xhandler.receptions_to_check(p_limit int) RETURNS TABLE (tenant_id uuid, reception_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT tenant_id, id FROM reception
    WHERE validity_state IN ('unchecked', 'unknown') AND (validity_checked_at IS NULL OR validity_checked_at < now() - interval '1 hour')
    ORDER BY validity_checked_at NULLS FIRST LIMIT p_limit
$$;

REVOKE ALL ON FUNCTION xhandler.adapter_token_company(text, text), xhandler.receptions_to_check(int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.adapter_token_company(text, text), xhandler.receptions_to_check(int) TO xhandler_app;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('reception-validity', 'receptions.validity', 900, 4);
