-- P7: PSE mode (DESIGN.md §1.1, §3.2, §6, §10.2).

-- Platform keys (§6): the PSE signing key and, later, the OSE CDR key.
-- Separate, permanent vault objects; only their reference and certificate here.
CREATE TABLE xhandler.platform_key (
    id          uuid PRIMARY KEY,
    purpose     text        NOT NULL CHECK (purpose IN ('pse', 'ose_cdr')),
    key_ref     text        NOT NULL,
    certificate bytea       NOT NULL,
    subject     text        NOT NULL,
    ruc         text,
    valid_from  timestamptz NOT NULL,
    valid_until timestamptz NOT NULL,
    status      text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'replaced')),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX platform_key_active ON xhandler.platform_key (purpose) WHERE status = 'active';
GRANT SELECT, INSERT, UPDATE ON xhandler.platform_key TO xhandler_app;
REVOKE DELETE ON xhandler.platform_key FROM xhandler_app;

-- Platform credentials (§7.1): the pool of PSE SOL users, the OSE's SUNAT
-- credentials, the validity API. Envelope-encrypted like tenant secrets.
CREATE TABLE xhandler.platform_credential (
    id               uuid PRIMARY KEY,
    purpose          text        NOT NULL CHECK (purpose IN ('pse_sol', 'ose_sunat', 'validity')),
    label            text        NOT NULL,
    ciphertext       bytea       NOT NULL,
    wrapped_data_key bytea       NOT NULL,
    master_key_id    text        NOT NULL,
    active           boolean     NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX platform_credential_purpose ON xhandler.platform_credential (purpose) WHERE active;
GRANT SELECT, INSERT, UPDATE ON xhandler.platform_credential TO xhandler_app;
REVOKE DELETE ON xhandler.platform_credential FROM xhandler_app;

-- The tenant authorized the platform as its PSE in SUNAT's SOL (§3.2, RS 199-2015)
CREATE TABLE xhandler.pse_authorization (
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id    uuid        NOT NULL REFERENCES xhandler.company (id),
    authorized_at date        NOT NULL,
    evidence_ref  text,
    registered_by text        NOT NULL,
    revoked_at    timestamptz,
    revoked_by    text,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX pse_authorization_active ON xhandler.pse_authorization (company_id) WHERE revoked_at IS NULL;
ALTER TABLE xhandler.pse_authorization ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.pse_authorization
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.pse_authorization TO xhandler_app;
REVOKE DELETE ON xhandler.pse_authorization FROM xhandler_app;

-- How a document was signed and sent: by the issuer (software) or by the platform as PSE
ALTER TABLE xhandler.document ADD COLUMN emission_mode text NOT NULL DEFAULT 'software' CHECK (emission_mode IN ('software', 'pse'));

-- PSE rejected-CDR rate per half-year (RS 199-2015 art. 7: ≤ 10% the first year, then ≤ 5%)
CREATE TABLE xhandler.pse_report (
    period     text PRIMARY KEY,
    accepted   bigint      NOT NULL,
    rejected   bigint      NOT NULL,
    rate       numeric(6, 4) NOT NULL,
    computed_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON xhandler.pse_report TO xhandler_app;
REVOKE DELETE ON xhandler.pse_report FROM xhandler_app;

-- PSE documents with a definitive answer in a period (production only): the rejected rate.
-- "Rejected" is SUNAT's rejection (2000–3999), not exceptions.
CREATE FUNCTION xhandler.pse_outcomes(p_from timestamptz, p_to timestamptz) RETURNS TABLE (accepted bigint, rejected bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT count(*) FILTER (WHERE status IN ('accepted', 'accepted_with_observations', 'voiding', 'voided')),
           count(*) FILTER (WHERE status = 'rejected')
    FROM document
    WHERE emission_mode = 'pse' AND sunat_env = 'production' AND first_sent_at >= p_from AND first_sent_at < p_to
$$;
REVOKE ALL ON FUNCTION xhandler.pse_outcomes(timestamptz, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.pse_outcomes(timestamptz, timestamptz) TO xhandler_app;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('pse-report', 'platform.pse-report', 21600, 4);
