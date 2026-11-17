-- P11 review fixes (DESIGN.md §13, §16, §18).

-- An exited tenant stays readable by its members until its data is deleted: exports,
-- data-subject requests; emitting is refused by the service
ALTER TABLE xhandler.tenant DROP CONSTRAINT tenant_status_check;
ALTER TABLE xhandler.tenant ADD CONSTRAINT tenant_status_check CHECK (status IN ('active', 'suspended', 'exited'));

CREATE OR REPLACE FUNCTION xhandler.member_role(p_tenant uuid, p_kind text, p_subject text) RETURNS text
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT m.role FROM tenant_member m JOIN tenant t ON t.id = m.tenant_id
    WHERE m.tenant_id = p_tenant AND m.kind = p_kind AND m.subject = p_subject
      AND t.deleted_at IS NULL AND t.status IN ('active', 'exited')
$$;

CREATE OR REPLACE FUNCTION xhandler.partner_role(p_tenant uuid, p_kind text, p_subject text) RETURNS text
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT CASE pm.role WHEN 'admin' THEN 'admin' ELSE 'viewer' END
    FROM tenant t JOIN partner p ON p.id = t.partner_id JOIN partner_member pm ON pm.partner_id = p.id
    WHERE t.id = p_tenant AND pm.kind = p_kind AND pm.subject = p_subject
      AND t.deleted_at IS NULL AND t.status IN ('active', 'exited') AND p.status = 'active'
$$;

-- Retention never below the legal minimum (Código Tributario: at least 5 years)
ALTER TABLE xhandler.tenant ADD CONSTRAINT tenant_retention_minimum CHECK (retention_years >= 5);

-- Exports remember their job, so a job that died marks the export failed
ALTER TABLE xhandler.tenant_export ADD COLUMN job_id uuid;

-- The deletion evidence is written by purge_tenant only
REVOKE INSERT, UPDATE, DELETE ON xhandler.tenant_deletion FROM xhandler_app;

-- Open documents are found through a small index (health, retention guards)
CREATE INDEX document_open ON xhandler.document (status) WHERE status IN ('pending_send', 'in_progress', 'unknown', 'exception', 'voiding');

-- Health counts what needs the platform: production, not held (a tenant's own problem), active tenants
CREATE OR REPLACE FUNCTION xhandler.document_health()
    RETURNS TABLE (exception bigint, unknown_stale bigint, past_deadline bigint, pending bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT count(*) FILTER (WHERE d.status = 'exception' AND d.updated_at > now() - interval '1 day'),
           count(*) FILTER (WHERE d.status = 'unknown' AND d.updated_at < now() - interval '1 hour'),
           count(*) FILTER (WHERE d.status IN ('pending_send', 'in_progress', 'unknown') AND d.deadline_at < now()),
           count(*) FILTER (WHERE d.status IN ('pending_send', 'in_progress', 'unknown'))
    FROM document d JOIN tenant t ON t.id = d.tenant_id
    WHERE d.status IN ('exception', 'unknown', 'pending_send', 'in_progress')
      AND d.sunat_env = 'production' AND d.hold_reason IS NULL AND t.status = 'active' AND NOT t.sending_paused
$$;

-- The backlog of jobs ready to run (not those waiting for a resource another job holds)
CREATE OR REPLACE FUNCTION xhandler.job_backlog() RETURNS TABLE (waiting bigint, oldest_seconds double precision)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT count(*), coalesce(extract(epoch FROM now() - min(unblocked_at)), 0)
    FROM job WHERE state = 'waiting' AND unblocked_at IS NOT NULL
$$;

-- Health computed once every 30 seconds by a job; the gauges read this small table
CREATE TABLE xhandler.ops_snapshot (
    name  text PRIMARY KEY,
    value double precision NOT NULL,
    at    timestamptz      NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON xhandler.ops_snapshot TO xhandler_app;

-- Retention, in Lima years and never below the minimum; files still used by a reception, a
-- report or anything else of the tenant are kept
CREATE OR REPLACE FUNCTION xhandler.expired_documents(p_limit int) RETURNS TABLE (tenant_id uuid, document_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT d.tenant_id, d.id FROM document d JOIN tenant t ON t.id = d.tenant_id
    WHERE NOT t.audit_hold AND t.deleted_at IS NULL
      AND d.issue_year < extract(year FROM now() AT TIME ZONE 'America/Lima')::int - greatest(t.retention_years, 5)
      AND d.status NOT IN ('pending_send', 'in_progress', 'unknown', 'voiding')
    LIMIT p_limit
$$;

CREATE OR REPLACE FUNCTION xhandler.purge_documents(p_tenant uuid, p_ids uuid[]) RETURNS SETOF text
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    v_ids uuid[];
BEGIN
    SELECT array_agg(d.id) INTO v_ids FROM document d JOIN tenant t ON t.id = d.tenant_id
    WHERE d.tenant_id = p_tenant AND d.id = ANY (p_ids) AND NOT t.audit_hold
      AND d.issue_year < extract(year FROM now() AT TIME ZONE 'America/Lima')::int - greatest(t.retention_years, 5)
      AND d.status NOT IN ('pending_send', 'in_progress', 'unknown', 'voiding');
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
        WHERE NOT EXISTS (SELECT 1 FROM tenant_file_refs(p_tenant) r WHERE r = p.key);
END
$$;

-- Tenant deletion: the files count goes into the evidence, and the tenant row keeps no
-- personal data (only its id and dates)
DROP FUNCTION xhandler.purge_tenant(uuid);
CREATE FUNCTION xhandler.purge_tenant(p_tenant uuid, p_files int) RETURNS jsonb
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
    UPDATE tenant SET deleted_at = now(), status = 'exited', name = '(eliminada)', billing_ruc = NULL, billing_name = NULL,
        billing_email = NULL, notification_email = NULL, updated_at = now()
    WHERE id = p_tenant;
    INSERT INTO tenant_deletion (tenant_id, deleted_at, counts, files_deleted) VALUES (p_tenant, now(), counts, p_files);
    RETURN counts;
END
$$;
REVOKE ALL ON FUNCTION xhandler.purge_tenant(uuid, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.purge_tenant(uuid, int) TO xhandler_app;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('ops-snapshot', 'ops.snapshot', 30, 2);
