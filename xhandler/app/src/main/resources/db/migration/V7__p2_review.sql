-- P2 review fixes (DESIGN.md §10, §24).

-- Indexes for lookups the reconciler and recovery make on every pass
CREATE INDEX document_voided_by ON xhandler.document (voided_by_document_id) WHERE voided_by_document_id IS NOT NULL;
CREATE INDEX document_attempt_job ON xhandler.document (attempt_job_id) WHERE attempt_job_id IS NOT NULL;

-- The breaker's half-open probe must be a call started after the half-open
-- transition, not a late answer of a call started before the breaker opened.
ALTER TABLE xhandler.sunat_endpoint ADD COLUMN half_open_at timestamptz;
ALTER TABLE xhandler.sunat_call ADD COLUMN started_at timestamptz;

-- Platform tables: the app role writes only what it must. sunat_endpoint rows
-- are created by the reconciler (definer); the admin API only pauses and caps
-- them. Schedules are dispatched by a definer function.
REVOKE INSERT, DELETE ON xhandler.sunat_endpoint FROM xhandler_app;
REVOKE UPDATE, DELETE ON xhandler.sunat_call FROM xhandler_app;
REVOKE INSERT, UPDATE, DELETE ON xhandler.job_schedule FROM xhandler_app;
REVOKE DELETE ON xhandler.worker, xhandler.job FROM xhandler_app;

-- Every running job, whatever its worker's heartbeat says. A running job whose
-- advisory lock can be taken is orphaned: the lock is taken before start_job
-- commits and released only after finish_job commits (§10.4).
CREATE FUNCTION xhandler.running_jobs() RETURNS TABLE (id uuid, seq bigint, worker text)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT id, seq, worker FROM job WHERE state IN ('running', 'canceling') $$;

-- recover_job: a document whose attempt already wrote its outcome (the attempt
-- clears attempt_job_id) keeps that outcome; only an interrupted send moves to unknown.
CREATE OR REPLACE FUNCTION xhandler.recover_job(p_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
BEGIN
    UPDATE job SET state = 'failed', finished_at = now(), updated_at = now(),
                   error = jsonb_build_object('kind', 'worker_lost', 'message', 'the worker running this job stopped')
    WHERE id = p_id AND state IN ('running', 'canceling');
    IF NOT FOUND THEN
        RETURN false;
    END IF;
    WITH moved AS (
        UPDATE document SET status = 'unknown', attempt_job_id = NULL, updated_at = now()
        WHERE attempt_job_id = p_id AND status = 'pending_send' AND send_started_at IS NOT NULL
        RETURNING id, tenant_id)
    INSERT INTO document_event (id, at, tenant_id, document_id, status, message, actor)
    SELECT gen_random_uuid(), now(), tenant_id, id, 'unknown', 'worker lost during a send', 'platform:recovery' FROM moved;
    UPDATE document SET attempt_job_id = NULL WHERE attempt_job_id = p_id;
    RETURN true;
END
$$;

-- job_summary gains unblocked_at (tests, admin API)
DROP FUNCTION xhandler.job_summary(uuid);
CREATE FUNCTION xhandler.job_summary(p_id uuid)
    RETURNS TABLE (name text, state text, seq bigint, worker text, tenant_id uuid, error jsonb, unblocked_at timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT name, state, seq, worker, tenant_id, error, unblocked_at FROM job WHERE id = p_id $$;

-- Reconciler (§24.2), revised:
-- * a document with an active attempt (waiting or running) is never leased
--   again, so a lease that expires while its attempt waits in the queue can't
--   create a duplicate attempt;
-- * documents that follow a summary are skipped (they carry no retry state);
-- * only endpoints with a due backlog are visited.
CREATE OR REPLACE FUNCTION xhandler.reconcile(p_lease interval, p_slots jsonb) RETURNS int
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    e record;
    d record;
    v_free int;
    v_job uuid;
    v_resources text[];
    v_key text;
    n int := 0;
BEGIN
    INSERT INTO sunat_endpoint (id)
    SELECT DISTINCT endpoint FROM document
    WHERE status IN ('pending_send', 'in_progress', 'unknown') AND next_attempt_at IS NOT NULL
    ON CONFLICT DO NOTHING;

    FOR e IN SELECT s.id, s.breaker_state, s.concurrency_cap FROM sunat_endpoint s
             WHERE NOT s.paused AND (s.breaker_state <> 'open' OR s.breaker_until <= now())
               AND EXISTS (SELECT FROM document doc
                           WHERE doc.endpoint = s.id AND doc.status IN ('pending_send', 'in_progress', 'unknown')
                             AND doc.next_attempt_at <= now())
    LOOP
        v_free := CASE WHEN e.breaker_state = 'closed' THEN e.concurrency_cap ELSE 1 END
                  - (SELECT count(*) FROM job WHERE endpoint = e.id AND state IN ('waiting', 'running', 'canceling'));
        CONTINUE WHEN v_free <= 0;
        FOR d IN
            SELECT doc.id, doc.issue_year, doc.tenant_id, doc.sunat_env, doc.credential_id
            FROM document doc
            JOIN company c ON c.id = doc.company_id
            JOIN tenant t ON t.id = doc.tenant_id
            WHERE doc.endpoint = e.id
              AND doc.status IN ('pending_send', 'in_progress', 'unknown')
              AND doc.next_attempt_at <= now()
              AND doc.hold_reason IS DISTINCT FROM 'operator'
              AND doc.summary_document_id IS NULL
              AND NOT c.sending_paused AND NOT t.sending_paused
              AND NOT EXISTS (SELECT FROM job j WHERE j.state IN ('waiting', 'running', 'canceling')
                              AND j.reserved_resources @> ARRAY['document:' || doc.id])
            ORDER BY doc.deadline_at
            LIMIT v_free
            FOR UPDATE OF doc SKIP LOCKED
        LOOP
            UPDATE document SET next_attempt_at = now() + p_lease, updated_at = now()
            WHERE id = d.id AND issue_year = d.issue_year;
            v_job := gen_random_uuid();
            SELECT ARRAY['document:' || d.id] || coalesce(array_agg('document:' || o.id ORDER BY o.id), '{}')
            INTO v_resources FROM document o WHERE o.voided_by_document_id = d.id AND o.status = 'voiding';
            v_key := CASE WHEN d.credential_id IS NOT NULL THEN 'credential:' || d.credential_id ELSE 'shared:' || d.sunat_env END;
            INSERT INTO job (id, tenant_id, name, priority_class, reserved_resources, credential_key, credential_slots, endpoint, args)
            VALUES (v_job, d.tenant_id, 'document.attempt', 2, v_resources, v_key,
                    coalesce((p_slots ->> d.sunat_env)::int, 1), e.id, jsonb_build_object('document_id', d.id));
            n := n + 1;
        END LOOP;
    END LOOP;
    IF n > 0 THEN
        PERFORM pg_notify('xhandler_job_wakeup', '');
    END IF;
    RETURN n;
END
$$;

-- Breakers (§24.2), revised: the half-open probe is a call started after the
-- endpoint went half-open; only endpoints with recent calls or an open breaker
-- are visited.
CREATE OR REPLACE FUNCTION xhandler.update_breakers(p_min_calls int, p_base_open interval, p_max_open interval) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    e record;
    v_total int;
    v_bad int;
    v_last boolean;
BEGIN
    FOR e IN SELECT * FROM sunat_endpoint s
             WHERE s.breaker_state <> 'closed'
                OR EXISTS (SELECT FROM sunat_call c WHERE c.endpoint = s.id AND c.at > now() - interval '30 seconds')
             FOR UPDATE
    LOOP
        IF e.breaker_state = 'closed' THEN
            SELECT count(*), count(*) FILTER (WHERE NOT healthy) INTO v_total, v_bad
            FROM sunat_call WHERE endpoint = e.id AND at > now() - interval '30 seconds';
            IF v_total >= p_min_calls AND v_bad * 2 > v_total THEN
                UPDATE sunat_endpoint SET breaker_state = 'open', open_count = 1,
                    breaker_until = now() + p_base_open, updated_at = now() WHERE id = e.id;
            END IF;
        ELSIF e.breaker_state = 'open' THEN
            IF e.breaker_until <= now() THEN
                UPDATE sunat_endpoint SET breaker_state = 'half_open', half_open_at = now(), updated_at = now() WHERE id = e.id;
            END IF;
        ELSE
            SELECT healthy INTO v_last FROM sunat_call
            WHERE endpoint = e.id AND started_at >= e.half_open_at ORDER BY at DESC LIMIT 1;
            IF v_last IS TRUE THEN
                UPDATE sunat_endpoint SET breaker_state = 'closed', open_count = 0, breaker_until = NULL, half_open_at = NULL,
                    updated_at = now() WHERE id = e.id;
            ELSIF v_last IS FALSE THEN
                UPDATE sunat_endpoint SET breaker_state = 'open', open_count = e.open_count + 1, half_open_at = NULL,
                    breaker_until = now() + least(p_max_open, p_base_open * power(2, e.open_count)), updated_at = now()
                WHERE id = e.id;
            END IF;
        END IF;
    END LOOP;
    DELETE FROM sunat_call WHERE at < now() - interval '1 hour';
END
$$;

-- Cancels a tenant job with one conditional update (no read-modify-write)
CREATE FUNCTION xhandler.cancel_tenant_job(p_job uuid) RETURNS text
    LANGUAGE plpgsql SECURITY INVOKER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    v_state text;
BEGIN
    UPDATE job SET state = CASE WHEN state = 'waiting' THEN 'canceled' ELSE 'canceling' END,
                   finished_at = CASE WHEN state = 'waiting' THEN now() ELSE finished_at END, updated_at = now()
    WHERE id = p_job AND state IN ('waiting', 'running')
    RETURNING state INTO v_state;
    IF v_state = 'canceling' THEN
        PERFORM pg_notify('xhandler_job_cancel', p_job::text);
    END IF;
    RETURN v_state;
END
$$;

DO $$
DECLARE
    f text;
BEGIN
    FOREACH f IN ARRAY ARRAY['running_jobs()', 'job_summary(uuid)', 'cancel_tenant_job(uuid)']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION xhandler.%s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION xhandler.%s TO xhandler_app', f);
    END LOOP;
END
$$;
