-- P3: emit and send in software mode (DESIGN.md §8, §11.1, §12).

-- Idempotent creates (§11.1). The first request inserts the key and holds it
-- while it runs; a retry gets the stored response, or 409 while it runs.
CREATE TABLE xhandler.idempotency_key (
    tenant_id    uuid        NOT NULL REFERENCES xhandler.tenant (id),
    key          text        NOT NULL CHECK (length(key) BETWEEN 1 AND 255),
    request_hash text        NOT NULL,
    status_code  int,
    response     jsonb,
    created_at   timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    PRIMARY KEY (tenant_id, key)
);
CREATE INDEX idempotency_key_created ON xhandler.idempotency_key (created_at);

-- Buyer capability links (§12): a random 128-bit token, stored hashed
CREATE TABLE xhandler.access_token (
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES xhandler.tenant (id),
    document_id uuid        NOT NULL,
    token_hash  text        NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL
);
CREATE INDEX access_token_document ON xhandler.access_token (document_id);

-- RC/RA/RR numbering per company, environment and Lima day (§8)
CREATE TABLE xhandler.daily_counter (
    tenant_id   uuid     NOT NULL REFERENCES xhandler.tenant (id),
    company_id  uuid     NOT NULL REFERENCES xhandler.company (id),
    sunat_env   text     NOT NULL,
    kind        text     NOT NULL CHECK (kind IN ('RC', 'RA', 'RR')),
    day         date     NOT NULL,
    next_number int      NOT NULL DEFAULT 1,
    PRIMARY KEY (company_id, sunat_env, kind, day)
);

-- Webhooks (§12): CloudEvents signed with HMAC-SHA256, retried with backoff
CREATE TABLE xhandler.webhook_subscription (
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES xhandler.tenant (id),
    url              text        NOT NULL,
    events           text[]      NOT NULL,
    ciphertext       bytea       NOT NULL,
    wrapped_data_key bytea       NOT NULL,
    master_key_id    text        NOT NULL,
    active           boolean     NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz
);
CREATE INDEX webhook_subscription_tenant ON xhandler.webhook_subscription (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE xhandler.webhook_delivery (
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES xhandler.tenant (id),
    subscription_id  uuid        NOT NULL REFERENCES xhandler.webhook_subscription (id),
    event_id         uuid        NOT NULL,
    event_type       text        NOT NULL,
    payload          jsonb       NOT NULL,
    status           text        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'delivered', 'failed')),
    attempts         int         NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz,
    last_status_code int,
    last_error       text,
    delivered_at     timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX webhook_delivery_due ON xhandler.webhook_delivery (next_attempt_at) WHERE status = 'pending';
CREATE INDEX webhook_delivery_subscription ON xhandler.webhook_delivery (subscription_id, created_at);

-- Usage events for billing (§14), written in the business transaction and
-- delivered to Lago by a job (P6)
CREATE TABLE xhandler.usage_event_outbox (
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES xhandler.tenant (id),
    event_type  text        NOT NULL,
    quantity    int         NOT NULL DEFAULT 1,
    properties  jsonb       NOT NULL DEFAULT '{}',
    created_at  timestamptz NOT NULL DEFAULT now(),
    sent_at     timestamptz
);
CREATE INDEX usage_event_outbox_unsent ON xhandler.usage_event_outbox (created_at) WHERE sent_at IS NULL;

-- Every signature (§6): who, document, key, time. Written in its own
-- transaction, so a signature whose emit rolled back stays logged; its
-- document_id then matches no document: the signature was discarded (§8).
CREATE TABLE xhandler.signature_log (
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES xhandler.tenant (id),
    document_id    uuid        NOT NULL,
    serie_numero   text        NOT NULL,
    signing_key_id uuid,
    digest_value   text        NOT NULL,
    actor          text        NOT NULL,
    at             timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX signature_log_tenant_at ON xhandler.signature_log (tenant_id, at);

-- Every delivery to a buyer (§11.6): email, portal download
CREATE TABLE xhandler.delivery (
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES xhandler.tenant (id),
    document_id uuid        NOT NULL,
    channel     text        NOT NULL CHECK (channel IN ('email', 'portal')),
    recipient   text,
    at          timestamptz NOT NULL DEFAULT now(),
    result      text        NOT NULL,
    detail      text
);
CREATE INDEX delivery_document ON xhandler.delivery (document_id, at);

ALTER TABLE xhandler.document ADD COLUMN deadline_alerted_at timestamptz;
ALTER TABLE xhandler.company ADD COLUMN delivered_on_emit_default boolean NOT NULL DEFAULT true;
ALTER TABLE xhandler.company ADD COLUMN email text;
-- Every new company starts on the SUNAT fake: the sandbox (§26.2)
ALTER TABLE xhandler.company ALTER COLUMN sunat_env SET DEFAULT 'fake';

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['idempotency_key', 'access_token', 'daily_counter', 'webhook_subscription', 'webhook_delivery',
        'usage_event_outbox', 'signature_log', 'delivery']
    LOOP
        EXECUTE format('ALTER TABLE xhandler.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON xhandler.%I USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON xhandler.%I TO xhandler_app', t);
    END LOOP;
END
$$;
REVOKE DELETE ON xhandler.access_token, xhandler.usage_event_outbox, xhandler.webhook_delivery, xhandler.delivery FROM xhandler_app;
-- Append-only
REVOKE UPDATE, DELETE ON xhandler.signature_log FROM xhandler_app;

-- Cross-tenant lookups (SECURITY DEFINER) -------------------------------------

-- The document a capability link opens, if the link is valid (public buyer access, §12)
CREATE FUNCTION xhandler.access_token_document(p_hash text) RETURNS TABLE (tenant_id uuid, document_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT tenant_id, document_id FROM access_token WHERE token_hash = p_hash AND expires_at > now() $$;

-- Leases due webhook deliveries (like the reconciler's documents): a delivery
-- doesn't look due again until the lease expires or its outcome is written.
CREATE FUNCTION xhandler.lease_webhook_deliveries(p_limit int, p_lease interval) RETURNS TABLE (id uuid, tenant_id uuid, attempts int)
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    WITH due AS (
        SELECT d.id FROM webhook_delivery d
        WHERE d.status = 'pending' AND d.next_attempt_at <= now()
        ORDER BY d.next_attempt_at LIMIT p_limit FOR UPDATE SKIP LOCKED)
    UPDATE webhook_delivery w SET next_attempt_at = now() + p_lease, updated_at = now()
    FROM due WHERE w.id = due.id
    RETURNING w.id, w.tenant_id, w.attempts
$$;

-- Boletas (and their notes) that must go in a resumen diario (§9.2.4): not
-- accepted when D+5 passed (Lima), or answered 1079. Grouped by company,
-- environment and issue day.
CREATE FUNCTION xhandler.boletas_needing_summary(p_today date)
    RETURNS TABLE (tenant_id uuid, company_id uuid, sunat_env text, fecha_emision date)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT DISTINCT tenant_id, company_id, sunat_env, fecha_emision FROM document
    WHERE origin <> 'ose_inbound' AND summary_document_id IS NULL
      AND status IN ('pending_send', 'unknown', 'exception')
      AND (tipo_comprobante = '03' OR (tipo_comprobante IN ('07', '08') AND serie LIKE 'B%'))
      AND (fecha_emision + 5 < p_today OR code_class = 'route_change')
      AND hold_reason IS DISTINCT FROM 'operator'
$$;

-- Non-definitive documents whose legal deadline is near and that weren't alerted yet (§9.2.4)
CREATE FUNCTION xhandler.documents_at_risk(p_horizon interval) RETURNS TABLE (tenant_id uuid, document_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT tenant_id, id FROM document
    WHERE status IN ('pending_send', 'in_progress', 'unknown', 'exception')
      AND deadline_alerted_at IS NULL AND deadline_at < now() + p_horizon AND summary_document_id IS NULL
$$;

-- Idempotency keys are kept 24 hours (§11.1)
CREATE FUNCTION xhandler.purge_idempotency_keys(p_age interval) RETURNS int
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    WITH gone AS (DELETE FROM idempotency_key WHERE created_at < now() - p_age RETURNING 1)
    SELECT count(*)::int FROM gone
$$;

DO $$
DECLARE
    f text;
BEGIN
    FOREACH f IN ARRAY ARRAY['access_token_document(text)', 'lease_webhook_deliveries(int,interval)',
        'boletas_needing_summary(date)', 'documents_at_risk(interval)', 'purge_idempotency_keys(interval)']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION xhandler.%s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION xhandler.%s TO xhandler_app', f);
    END LOOP;
END
$$;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('webhooks', 'webhooks.deliver', 2, 3),
    ('boleta-summary-fallback', 'documents.boleta-summary-fallback', 3600, 2),
    ('deadline-alerts', 'documents.deadline-alerts', 600, 2),
    ('idempotency-purge', 'platform.idempotency-purge', 3600, 4);
