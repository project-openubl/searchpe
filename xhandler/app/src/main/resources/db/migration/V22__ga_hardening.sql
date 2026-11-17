-- P11: GA hardening — data protection workflows and operational health (DESIGN.md §13, §16, §18).

-- Tenant exit (§13): export first, then the tenant leaves; its data is deleted 2 years later,
-- with evidence, unless it bought the archive plan or a tax audit holds it
ALTER TABLE xhandler.tenant ADD COLUMN exit_requested_at timestamptz;
ALTER TABLE xhandler.tenant ADD COLUMN delete_after timestamptz;
ALTER TABLE xhandler.tenant ADD COLUMN archive_plan boolean NOT NULL DEFAULT false;

-- Full exports (XML, CDR, PDF and metadata): one ZIP part per issue month, plus the index
CREATE TABLE xhandler.tenant_export (
    id           uuid PRIMARY KEY,
    tenant_id    uuid        NOT NULL REFERENCES xhandler.tenant (id),
    status       text        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'done', 'failed')),
    parts        jsonb       NOT NULL DEFAULT '[]',
    documents    int,
    detail       text,
    requested_by text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    done_at      timestamptz
);
ALTER TABLE xhandler.tenant_export ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.tenant_export
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.tenant_export TO xhandler_app;
REVOKE DELETE ON xhandler.tenant_export FROM xhandler_app;

-- Data-subject requests (Ley 29733; §16): the tenant is the controller. A request that reaches
-- the platform is forwarded to the tenant within 3 days; the tenant answers within the law's term
CREATE TABLE xhandler.data_subject_request (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid        NOT NULL REFERENCES xhandler.tenant (id),
    subject_doc_type   text        NOT NULL,
    subject_doc_number text        NOT NULL,
    subject_name       text,
    kind               text        NOT NULL CHECK (kind IN ('access', 'rectification', 'cancellation', 'opposition')),
    channel            text        NOT NULL CHECK (channel IN ('platform', 'tenant')),
    details            text,
    received_at        timestamptz NOT NULL DEFAULT now(),
    forward_due_at     timestamptz,
    forwarded_at       timestamptz,
    response_due_at    timestamptz NOT NULL,
    status             text        NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'closed')),
    resolution         text,
    closed_at          timestamptz,
    closed_by          text,
    reminded_at        timestamptz,
    created_by         text        NOT NULL
);
CREATE INDEX data_subject_request_open ON xhandler.data_subject_request (response_due_at) WHERE status = 'open';
ALTER TABLE xhandler.data_subject_request ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.data_subject_request
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
GRANT SELECT, INSERT, UPDATE ON xhandler.data_subject_request TO xhandler_app;
REVOKE DELETE ON xhandler.data_subject_request FROM xhandler_app;

-- Evidence that a tenant's data was deleted (kept after the tenant's rows are gone)
CREATE TABLE xhandler.tenant_deletion (
    tenant_id     uuid PRIMARY KEY,
    deleted_at    timestamptz NOT NULL,
    counts        jsonb       NOT NULL,
    files_deleted int
);
GRANT SELECT, UPDATE ON xhandler.tenant_deletion TO xhandler_app;

-- Health for alerts (§18): documents that need a person, and the job backlog
CREATE FUNCTION xhandler.document_health()
    RETURNS TABLE (exception bigint, unknown_stale bigint, past_deadline bigint, pending bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT count(*) FILTER (WHERE status = 'exception'),
           count(*) FILTER (WHERE status = 'unknown' AND updated_at < now() - interval '1 hour'),
           count(*) FILTER (WHERE status IN ('pending_send', 'in_progress', 'unknown') AND deadline_at < now()),
           count(*) FILTER (WHERE status IN ('pending_send', 'in_progress', 'unknown'))
    FROM document WHERE status IN ('exception', 'unknown', 'pending_send', 'in_progress')
$$;

CREATE FUNCTION xhandler.job_backlog() RETURNS TABLE (waiting bigint, oldest_seconds double precision)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT count(*), coalesce(extract(epoch FROM now() - min(created_at)), 0) FROM job WHERE state = 'waiting' $$;

-- Retention (§13): documents past the tenant's retention years, never under a tax-audit hold
CREATE FUNCTION xhandler.expired_documents(p_limit int) RETURNS TABLE (tenant_id uuid, document_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT d.tenant_id, d.id FROM document d JOIN tenant t ON t.id = d.tenant_id
    WHERE NOT t.audit_hold AND t.deleted_at IS NULL
      AND d.issue_year < extract(year FROM now())::int - t.retention_years
    LIMIT p_limit
$$;

-- Deletes expired documents of a tenant with everything that hangs from them; returns the
-- storage keys no other document points at (the caller deletes those files)
CREATE FUNCTION xhandler.purge_documents(p_tenant uuid, p_ids uuid[]) RETURNS SETOF text
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    v_ids uuid[];
BEGIN
    -- Only what really expired, whatever the caller passed
    SELECT array_agg(d.id) INTO v_ids FROM document d JOIN tenant t ON t.id = d.tenant_id
    WHERE d.tenant_id = p_tenant AND d.id = ANY (p_ids) AND NOT t.audit_hold
      AND d.issue_year < extract(year FROM now())::int - t.retention_years;
    IF v_ids IS NULL THEN
        RETURN;
    END IF;
    CREATE TEMP TABLE IF NOT EXISTS purged_keys (key text) ON COMMIT DROP;
    TRUNCATE purged_keys;
    INSERT INTO purged_keys SELECT storage_key FROM document_file WHERE tenant_id = p_tenant AND document_id = ANY (v_ids);
    DELETE FROM document_file WHERE tenant_id = p_tenant AND document_id = ANY (v_ids);
    DELETE FROM document_event WHERE tenant_id = p_tenant AND document_id = ANY (v_ids);
    DELETE FROM delivery WHERE tenant_id = p_tenant AND document_id = ANY (v_ids);
    DELETE FROM access_token WHERE tenant_id = p_tenant AND document_id = ANY (v_ids);
    DELETE FROM conformity WHERE tenant_id = p_tenant AND document_id = ANY (v_ids);
    DELETE FROM signature_log WHERE tenant_id = p_tenant AND document_id = ANY (v_ids);
    DELETE FROM document WHERE tenant_id = p_tenant AND id = ANY (v_ids);
    RETURN QUERY SELECT DISTINCT p.key FROM purged_keys p
        WHERE NOT EXISTS (SELECT 1 FROM document_file f WHERE f.tenant_id = p_tenant AND f.storage_key = p.key);
END
$$;

-- Tenants whose exit period is over
CREATE FUNCTION xhandler.tenants_to_delete() RETURNS TABLE (tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT id FROM tenant
    WHERE delete_after IS NOT NULL AND delete_after < now() AND NOT audit_hold AND NOT archive_plan AND deleted_at IS NULL
$$;

-- Every file a tenant has (deleted before its rows)
CREATE FUNCTION xhandler.tenant_file_refs(p_tenant uuid) RETURNS SETOF text
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT storage_key FROM document_file WHERE tenant_id = p_tenant
    UNION SELECT xml_ref FROM reception WHERE tenant_id = p_tenant
    UNION SELECT pdf_ref FROM reception WHERE tenant_id = p_tenant AND pdf_ref IS NOT NULL
    UNION SELECT cdr_ref FROM reception WHERE tenant_id = p_tenant AND cdr_ref IS NOT NULL
    UNION SELECT file_ref FROM report WHERE tenant_id = p_tenant AND file_ref IS NOT NULL
    UNION SELECT proposal_ref FROM sire_period WHERE tenant_id = p_tenant AND proposal_ref IS NOT NULL
    UNION SELECT replacement_ref FROM sire_period WHERE tenant_id = p_tenant AND replacement_ref IS NOT NULL
    UNION SELECT evidence_ref FROM pse_authorization WHERE tenant_id = p_tenant AND evidence_ref IS NOT NULL
    UNION SELECT logo_ref FROM company WHERE tenant_id = p_tenant AND logo_ref IS NOT NULL
    UNION SELECT part->>'ref' FROM tenant_export, jsonb_array_elements(parts) part WHERE tenant_id = p_tenant
$$;

-- Deletes every row of a tenant whose exit period is over (tables with a tenant_id, in as many
-- passes as foreign keys need), keeps the tenant row as deleted and records the evidence
CREATE FUNCTION xhandler.purge_tenant(p_tenant uuid) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    t record;
    n bigint;
    counts jsonb := '{}';
    pass int := 0;
    failed boolean;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenant WHERE id = p_tenant AND delete_after IS NOT NULL AND delete_after < now()
                   AND NOT audit_hold AND NOT archive_plan AND deleted_at IS NULL) THEN
        RAISE EXCEPTION 'tenant % is not due for deletion', p_tenant;
    END IF;
    -- OSE users keep working for their issuers; only the billing link goes
    UPDATE ose_client SET tenant_id = NULL WHERE tenant_id = p_tenant;
    LOOP
        pass := pass + 1;
        failed := false;
        FOR t IN
            SELECT c.relname FROM pg_class c JOIN pg_namespace s ON s.oid = c.relnamespace
            JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
            WHERE s.nspname = 'xhandler' AND c.relkind IN ('r', 'p') AND NOT c.relispartition
              AND c.relname NOT IN ('tenant', 'tenant_deletion', 'ose_client')
            ORDER BY c.relname
        LOOP
            BEGIN
                EXECUTE format('DELETE FROM xhandler.%I WHERE tenant_id = $1', t.relname) USING p_tenant;
                GET DIAGNOSTICS n = ROW_COUNT;
                IF n > 0 THEN
                    counts := jsonb_set(counts, ARRAY[t.relname], to_jsonb(coalesce((counts ->> t.relname)::bigint, 0) + n));
                END IF;
            EXCEPTION WHEN foreign_key_violation THEN
                failed := true;
            END;
        END LOOP;
        EXIT WHEN NOT failed OR pass >= 10;
    END LOOP;
    IF failed THEN
        RAISE EXCEPTION 'tenant % still has dependent rows after % passes', p_tenant, pass;
    END IF;
    UPDATE tenant SET deleted_at = now(), status = 'suspended', updated_at = now() WHERE id = p_tenant;
    INSERT INTO tenant_deletion (tenant_id, deleted_at, counts) VALUES (p_tenant, now(), counts);
    RETURN counts;
END
$$;

-- Requests close to their term (the tenant is reminded) or overdue (the platform is told)
CREATE FUNCTION xhandler.privacy_requests_due(p_days int) RETURNS TABLE (tenant_id uuid, request_id uuid, response_due_at timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT tenant_id, id, response_due_at FROM data_subject_request
    WHERE status = 'open' AND response_due_at < now() + make_interval(days => p_days)
$$;

REVOKE ALL ON FUNCTION xhandler.document_health(), xhandler.job_backlog(), xhandler.expired_documents(int),
    xhandler.purge_documents(uuid, uuid[]), xhandler.tenants_to_delete(), xhandler.tenant_file_refs(uuid), xhandler.purge_tenant(uuid),
    xhandler.privacy_requests_due(int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.document_health(), xhandler.job_backlog(), xhandler.expired_documents(int),
    xhandler.purge_documents(uuid, uuid[]), xhandler.tenants_to_delete(), xhandler.tenant_file_refs(uuid), xhandler.purge_tenant(uuid),
    xhandler.privacy_requests_due(int) TO xhandler_app;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('retention-purge', 'retention.purge', 86400, 4),
    ('tenant-exit-deletion', 'tenants.exit-deletion', 86400, 4),
    ('privacy-deadlines', 'privacy.deadlines', 86400, 4);
