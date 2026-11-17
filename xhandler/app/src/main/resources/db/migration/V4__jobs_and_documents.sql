-- P2: job system, documents and SUNAT endpoint health (DESIGN.md §7, §9, §10, §24).
--
-- Workers and the reconciler work across tenants. They never query tenant
-- tables directly: they call the SECURITY DEFINER functions at the end of this
-- file (DESIGN.md §6), and open a tenant (app.tenant_id) to do a job's work.

-- Partitioned tables -----------------------------------------------------------

-- Issued documents. Never deleted; only their status columns change (§7.1).
-- Partitioned by issue year (§25): the year is part of every unique key.
CREATE TABLE xhandler.document (
    id                       uuid        NOT NULL,
    issue_year               smallint    NOT NULL,
    tenant_id                uuid        NOT NULL,
    company_id               uuid        NOT NULL,
    sunat_env                text        NOT NULL CHECK (sunat_env IN ('beta', 'production', 'fake')),
    origin                   text        NOT NULL DEFAULT 'api' CHECK (origin IN ('api', 'adapter', 'ose_inbound', 'reception', 'summary')),
    kind                     text        NOT NULL,
    tipo_comprobante         text        NOT NULL,
    serie                    text        NOT NULL,
    numero                   bigint      NOT NULL,
    fecha_emision            date        NOT NULL,
    deadline_at              timestamptz NOT NULL,
    customer_tipo            text,
    customer_numero          text,
    customer_nombre          text,
    moneda                   text,
    tipo_cambio              numeric(12, 6),
    total                    numeric(14, 2),
    status                   text        NOT NULL CHECK (status IN ('draft', 'pending_send', 'in_progress', 'accepted',
                                 'accepted_with_observations', 'rejected', 'exception', 'unknown', 'voiding', 'voided')),
    status_before_voiding    text,
    sunat_code               int,
    sunat_description        text,
    sunat_notes              jsonb,
    code_class               text,
    ticket                   text,
    digest_value             text,
    endpoint                 text        NOT NULL,
    credential_id            uuid,
    affected_document_id     uuid,
    voided_by_document_id    uuid,
    summary_document_id      uuid,
    hold_reason              text CHECK (hold_reason IN ('needs_attention', 'operator')),
    attempts                 int         NOT NULL DEFAULT 0,
    next_attempt_at          timestamptz,
    attempt_job_id           uuid,
    send_started_at          timestamptz,
    first_sent_at            timestamptz,
    cdr_received_at          timestamptz,
    reversal_communicated_at timestamptz,
    last_outcome             text,
    same_outcome_count       int         NOT NULL DEFAULT 0,
    progress_at              timestamptz NOT NULL DEFAULT now(),
    delivered_at             timestamptz,
    delivered_source         text CHECK (delivered_source IN ('service', 'client')),
    labels                   jsonb       NOT NULL DEFAULT '{}',
    device_id                text,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, issue_year)
) PARTITION BY LIST (issue_year);
-- The number backstop (§8). OSE inbound documents are excluded (§7.1).
CREATE UNIQUE INDEX document_number ON xhandler.document (company_id, sunat_env, tipo_comprobante, serie, numero, issue_year)
    WHERE origin <> 'ose_inbound';
CREATE INDEX document_tenant_created ON xhandler.document (tenant_id, created_at);
CREATE INDEX document_company_status ON xhandler.document (company_id, status);
-- The reconciler's index (§24.2): tiny in steady state, the backlog during outages
CREATE INDEX document_due ON xhandler.document (endpoint, deadline_at)
    WHERE status IN ('pending_send', 'in_progress', 'unknown') AND next_attempt_at IS NOT NULL;
CREATE INDEX document_labels ON xhandler.document USING gin (labels);

-- Timeline and audit of every status change (§7.1). Partitioned by month (§25).
CREATE TABLE xhandler.document_event (
    id             uuid        NOT NULL,
    at             timestamptz NOT NULL DEFAULT now(),
    tenant_id      uuid        NOT NULL,
    document_id    uuid        NOT NULL,
    status         text        NOT NULL,
    code           int,
    message        text,
    actor          text        NOT NULL,
    correlation_id text,
    PRIMARY KEY (id, at)
) PARTITION BY RANGE (at);
CREATE INDEX document_event_document ON xhandler.document_event (document_id, at);

-- Plain tables -----------------------------------------------------------------

-- Files of a document in storage (content-addressed, immutable, §13)
CREATE TABLE xhandler.document_file (
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES xhandler.tenant (id),
    document_id uuid        NOT NULL,
    kind        text        NOT NULL CHECK (kind IN ('xml_signed', 'cdr_zip', 'pdf', 'input', 'enriched')),
    storage_key text        NOT NULL,
    sha256      text        NOT NULL,
    size        bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_id, kind)
);

-- Jobs (§10.1). A job is a disposable attempt: retry state lives on the document.
CREATE TABLE xhandler.job (
    id                 uuid PRIMARY KEY,
    seq                bigserial   NOT NULL UNIQUE,
    tenant_id          uuid REFERENCES xhandler.tenant (id),
    name               text        NOT NULL,
    state              text        NOT NULL DEFAULT 'waiting'
                           CHECK (state IN ('waiting', 'running', 'completed', 'failed', 'canceling', 'canceled')),
    priority_class     smallint    NOT NULL DEFAULT 2 CHECK (priority_class BETWEEN 1 AND 4),
    run_at             timestamptz NOT NULL DEFAULT now(),
    unblocked_at       timestamptz,
    reserved_resources text[]      NOT NULL DEFAULT '{}',
    credential_key     text,
    credential_slots   int CHECK (credential_slots IS NULL OR credential_slots >= 1),
    endpoint           text,
    attempt            int         NOT NULL DEFAULT 0,
    args               jsonb       NOT NULL DEFAULT '{}',
    result             jsonb,
    error              jsonb,
    worker             text,
    started_at         timestamptz,
    finished_at        timestamptz,
    correlation_id     text,
    created_resources  jsonb,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX job_resources ON xhandler.job USING gin (reserved_resources) WHERE state IN ('waiting', 'running', 'canceling');
CREATE INDEX job_waiting ON xhandler.job (priority_class, seq) WHERE state = 'waiting';
CREATE INDEX job_unblocked ON xhandler.job (unblocked_at) WHERE state = 'waiting' AND unblocked_at IS NOT NULL;
CREATE INDEX job_tenant ON xhandler.job (tenant_id, created_at);
CREATE INDEX job_finished ON xhandler.job (finished_at) WHERE state IN ('completed', 'failed', 'canceled');
CREATE INDEX job_worker ON xhandler.job (worker) WHERE state IN ('running', 'canceling');

CREATE TABLE xhandler.job_schedule (
    name             text PRIMARY KEY,
    job_name         text        NOT NULL,
    interval_seconds int         NOT NULL CHECK (interval_seconds > 0),
    priority_class   smallint    NOT NULL DEFAULT 4,
    next_run_at      timestamptz NOT NULL DEFAULT now(),
    last_job_id      uuid,
    enabled          boolean     NOT NULL DEFAULT true
);

CREATE TABLE xhandler.worker (
    name           text PRIMARY KEY,
    role           text        NOT NULL,
    started_at     timestamptz NOT NULL DEFAULT now(),
    last_heartbeat timestamptz NOT NULL DEFAULT now()
);

-- SUNAT (or third-party OSE) endpoints. Written only by the reconciler (§24.2).
CREATE TABLE xhandler.sunat_endpoint (
    id              text PRIMARY KEY,
    breaker_state   text        NOT NULL DEFAULT 'closed' CHECK (breaker_state IN ('closed', 'open', 'half_open')),
    breaker_until   timestamptz,
    open_count      int         NOT NULL DEFAULT 0,
    concurrency_cap int         NOT NULL DEFAULT 50 CHECK (concurrency_cap >= 1),
    paused          boolean     NOT NULL DEFAULT false,
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Outcome of every call to an endpoint, for its breaker. Append-only, purged after an hour.
CREATE TABLE xhandler.sunat_call (
    id       bigserial PRIMARY KEY,
    endpoint text        NOT NULL,
    at       timestamptz NOT NULL DEFAULT now(),
    healthy  boolean     NOT NULL
);
CREATE INDEX sunat_call_endpoint_at ON xhandler.sunat_call (endpoint, at);

-- The SUNAT fake is an environment too: the sandbox every tenant starts on (§26.2)
ALTER TABLE xhandler.company DROP CONSTRAINT company_sunat_env_check,
    ADD CONSTRAINT company_sunat_env_check CHECK (sunat_env IN ('beta', 'production', 'fake'));
ALTER TABLE xhandler.series DROP CONSTRAINT series_sunat_env_check,
    ADD CONSTRAINT series_sunat_env_check CHECK (sunat_env IN ('beta', 'production', 'fake'));

-- Holds per company and tenant (§24.1 manual control)
ALTER TABLE xhandler.company ADD COLUMN sending_paused boolean NOT NULL DEFAULT false;
ALTER TABLE xhandler.tenant ADD COLUMN sending_paused boolean NOT NULL DEFAULT false;

-- Row-level security -----------------------------------------------------------

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['document', 'document_event', 'document_file', 'job']
    LOOP
        EXECUTE format('ALTER TABLE xhandler.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON xhandler.%I USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant())', t);
    END LOOP;
END
$$;
GRANT SELECT, INSERT, UPDATE ON xhandler.document, xhandler.document_event, xhandler.document_file, xhandler.job TO xhandler_app;
-- Issued documents, their events and files are never deleted (§7)
REVOKE DELETE ON xhandler.document, xhandler.document_event, xhandler.document_file FROM xhandler_app;
REVOKE UPDATE ON xhandler.document_event, xhandler.document_file FROM xhandler_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA xhandler TO xhandler_app;

-- Partitions ---------------------------------------------------------------------

-- Creates the partitions around p_day: document years p_day-1..p_day+1 and
-- document_event months p_day..p_day+2. Run at migration and daily.
-- Partitions are reached only through their parent, whose policies apply, so
-- the app role gets no privileges on them.
CREATE FUNCTION xhandler.ensure_partitions(p_day date) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    y int;
    m date;
    part text;
BEGIN
    FOR y IN extract(year FROM p_day)::int - 1 .. extract(year FROM p_day)::int + 1 LOOP
        part := 'document_' || y;
        IF to_regclass('xhandler.' || part) IS NULL THEN
            EXECUTE format('CREATE TABLE xhandler.%I PARTITION OF xhandler.document FOR VALUES IN (%s)', part, y);
            EXECUTE format('ALTER TABLE xhandler.%I ENABLE ROW LEVEL SECURITY', part);
            EXECUTE format('REVOKE ALL ON xhandler.%I FROM xhandler_app', part);
        END IF;
    END LOOP;
    FOR i IN 0 .. 2 LOOP
        m := date_trunc('month', p_day)::date + make_interval(months => i);
        part := 'document_event_' || to_char(m, 'YYYYMM');
        IF to_regclass('xhandler.' || part) IS NULL THEN
            EXECUTE format('CREATE TABLE xhandler.%I PARTITION OF xhandler.document_event FOR VALUES FROM (%L) TO (%L)',
                part, m, (m + interval '1 month')::date);
            EXECUTE format('ALTER TABLE xhandler.%I ENABLE ROW LEVEL SECURITY', part);
            EXECUTE format('REVOKE ALL ON xhandler.%I FROM xhandler_app', part);
        END IF;
    END LOOP;
END
$$;

-- Safety nets: a row outside the created partitions still lands somewhere
CREATE TABLE xhandler.document_default PARTITION OF xhandler.document DEFAULT;
CREATE TABLE xhandler.document_event_default PARTITION OF xhandler.document_event DEFAULT;
ALTER TABLE xhandler.document_default ENABLE ROW LEVEL SECURITY;
ALTER TABLE xhandler.document_event_default ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON xhandler.document_default, xhandler.document_event_default FROM xhandler_app;
SELECT xhandler.ensure_partitions(current_date);

-- Job system functions (SECURITY DEFINER: they see every tenant's jobs) ---------

-- One unblocker pass (§10.3): pulpcore's two-step design. Marks a waiting job
-- runnable when it is due, none of its exclusive resources is taken by a
-- running job or an earlier due job (FIFO per resource), and its credential
-- has a free slot. The caller holds the UNBLOCK advisory lock.
CREATE FUNCTION xhandler.unblock_jobs() RETURNS int
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    taken  text[];
    slots  jsonb;
    j      record;
    used   int;
    marked int := 0;
BEGIN
    SELECT coalesce(array_agg(r), '{}') INTO taken
    FROM job, unnest(reserved_resources) r WHERE state IN ('running', 'canceling');
    SELECT coalesce(jsonb_object_agg(credential_key, n), '{}') INTO slots FROM (
        SELECT credential_key, count(*) n FROM job
        WHERE credential_key IS NOT NULL
          AND (state IN ('running', 'canceling') OR (state = 'waiting' AND unblocked_at IS NOT NULL))
        GROUP BY credential_key) s;

    FOR j IN SELECT id, reserved_resources, credential_key, credential_slots, unblocked_at
             FROM job WHERE state = 'waiting' AND run_at <= now() ORDER BY seq
    LOOP
        IF j.unblocked_at IS NOT NULL THEN
            taken := taken || j.reserved_resources;
            CONTINUE;
        END IF;
        IF j.reserved_resources && taken THEN
            taken := taken || j.reserved_resources;
            CONTINUE;
        END IF;
        IF j.credential_key IS NOT NULL AND j.credential_slots IS NOT NULL THEN
            used := coalesce((slots ->> j.credential_key)::int, 0);
            IF used >= j.credential_slots THEN
                taken := taken || j.reserved_resources;
                CONTINUE;
            END IF;
            slots := slots || jsonb_build_object(j.credential_key, used + 1);
        END IF;
        UPDATE job SET unblocked_at = now(), updated_at = now() WHERE id = j.id;
        taken := taken || j.reserved_resources;
        marked := marked + 1;
    END LOOP;
    RETURN marked;
END
$$;

-- Candidates to claim, locked until the caller's transaction ends (§10.3).
-- The caller then takes each job's session advisory lock on its lock
-- connection before start_job's compare-and-set.
CREATE FUNCTION xhandler.claimable_jobs(p_limit int) RETURNS TABLE (id uuid, seq bigint)
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT id, seq FROM job WHERE state = 'waiting' AND unblocked_at IS NOT NULL
    ORDER BY priority_class, unblocked_at
    FOR UPDATE SKIP LOCKED LIMIT p_limit
$$;

-- waiting -> running, by the worker that holds the job's advisory lock
CREATE FUNCTION xhandler.start_job(p_id uuid, p_worker text)
    RETURNS TABLE (tenant_id uuid, name text, attempt int, args jsonb, correlation_id text)
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    UPDATE job SET state = 'running', worker = p_worker, started_at = now(), attempt = attempt + 1, updated_at = now()
    WHERE id = p_id AND state = 'waiting' AND unblocked_at IS NOT NULL
    RETURNING tenant_id, name, attempt, args, correlation_id
$$;

-- running|canceling -> completed|failed|canceled, only by the job's own worker
CREATE FUNCTION xhandler.finish_job(p_id uuid, p_worker text, p_state text, p_result jsonb, p_error jsonb) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    v_state text := p_state;
BEGIN
    IF p_state NOT IN ('completed', 'failed', 'canceled') THEN
        RAISE EXCEPTION 'invalid final state %', p_state;
    END IF;
    UPDATE job SET state = CASE WHEN state = 'canceling' AND v_state <> 'failed' THEN 'canceled' ELSE v_state END,
                   result = p_result, error = p_error, finished_at = now(), updated_at = now()
    WHERE id = p_id AND worker = p_worker AND state IN ('running', 'canceling');
    RETURN FOUND;
END
$$;

-- Whether a running job was asked to cancel (checked at safe points)
CREATE FUNCTION xhandler.job_cancel_requested(p_id uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT EXISTS (SELECT FROM job WHERE id = p_id AND state = 'canceling') $$;

-- Running jobs of workers whose heartbeat is older than p_ttl (§10.4)
CREATE FUNCTION xhandler.jobs_of_dead_workers(p_ttl interval) RETURNS TABLE (id uuid, seq bigint, worker text)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT j.id, j.seq, j.worker FROM job j
    WHERE j.state IN ('running', 'canceling')
      AND NOT EXISTS (SELECT FROM worker w WHERE w.name = j.worker AND w.last_heartbeat > now() - p_ttl)
$$;

-- Fails a job whose worker is gone; the caller holds the job's advisory lock.
-- Its document stays non-definitive: one whose send had started moves to
-- unknown (SUNAT may have it), and the reconciler resumes it when its lease
-- expires. No job is created here (§10.4).
CREATE FUNCTION xhandler.recover_job(p_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    v_tenant uuid;
BEGIN
    UPDATE job SET state = 'failed', finished_at = now(), updated_at = now(),
                   error = jsonb_build_object('kind', 'worker_lost', 'message', 'the worker running this job stopped')
    WHERE id = p_id AND state IN ('running', 'canceling')
    RETURNING tenant_id INTO v_tenant;
    IF NOT FOUND THEN
        RETURN false;
    END IF;
    UPDATE document SET status = 'unknown', updated_at = now()
    WHERE attempt_job_id = p_id AND status = 'pending_send' AND send_started_at IS NOT NULL;
    INSERT INTO document_event (id, at, tenant_id, document_id, status, message, actor)
    SELECT gen_random_uuid(), now(), d.tenant_id, d.id, 'unknown', 'worker lost during a send', 'platform:recovery'
    FROM document d WHERE d.attempt_job_id = p_id AND d.status = 'unknown' AND v_tenant IS NOT NULL;
    RETURN true;
END
$$;

CREATE FUNCTION xhandler.delete_dead_workers(p_ttl interval) RETURNS int
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    WITH gone AS (
        DELETE FROM worker w WHERE w.last_heartbeat < now() - p_ttl
          AND NOT EXISTS (SELECT FROM job j WHERE j.worker = w.name AND j.state IN ('running', 'canceling'))
        RETURNING 1)
    SELECT count(*)::int FROM gone
$$;

-- Platform job (no tenant), e.g. a schedule's run
CREATE FUNCTION xhandler.enqueue_platform_job(p_id uuid, p_name text, p_priority smallint, p_args jsonb, p_resources text[],
        p_credential_key text DEFAULT NULL, p_credential_slots int DEFAULT NULL) RETURNS void
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    INSERT INTO job (id, name, priority_class, args, reserved_resources, credential_key, credential_slots)
    VALUES (p_id, p_name, p_priority, p_args, p_resources, p_credential_key, p_credential_slots);
    SELECT pg_notify('xhandler_job_wakeup', '');
$$;

-- Enqueues one job per due schedule (§10.6); the caller holds SCHEDULE_DISPATCH.
-- A schedule whose previous job hasn't finished is skipped, so slow runs don't pile up.
CREATE FUNCTION xhandler.dispatch_schedules() RETURNS int
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    s record;
    v_id uuid;
    n int := 0;
BEGIN
    FOR s IN SELECT * FROM job_schedule WHERE enabled AND next_run_at <= now() FOR UPDATE SKIP LOCKED LOOP
        IF s.last_job_id IS NOT NULL AND EXISTS (
                SELECT FROM job WHERE id = s.last_job_id AND state IN ('waiting', 'running', 'canceling')) THEN
            CONTINUE;
        END IF;
        v_id := gen_random_uuid();
        INSERT INTO job (id, name, priority_class, args, reserved_resources)
        VALUES (v_id, s.job_name, s.priority_class, jsonb_build_object('schedule', s.name), ARRAY['schedule:' || s.name]);
        UPDATE job_schedule SET last_job_id = v_id, next_run_at = now() + make_interval(secs => s.interval_seconds)
        WHERE name = s.name;
        n := n + 1;
    END LOOP;
    IF n > 0 THEN
        PERFORM pg_notify('xhandler_job_wakeup', '');
    END IF;
    RETURN n;
END
$$;

-- Removes finished jobs older than p_age (§10.4); document_event is the permanent trail
CREATE FUNCTION xhandler.purge_jobs(p_age interval) RETURNS int
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    WITH gone AS (
        DELETE FROM job WHERE state IN ('completed', 'failed', 'canceled') AND finished_at < now() - p_age RETURNING 1)
    SELECT count(*)::int FROM gone
$$;

-- Reconciler (§24.2) -----------------------------------------------------------

-- Leases due documents and creates one attempt job per document, earliest
-- deadline first, within each endpoint's free capacity. The lease makes a
-- document look not-due until it expires, so it never gets two attempts at
-- once; the attempt's outcome overwrites next_attempt_at. The caller holds
-- RECONCILE and passes the credential slot limits ({"beta": 1, "production": 4, "fake": 50}).
CREATE FUNCTION xhandler.reconcile(p_lease interval, p_slots jsonb) RETURNS int
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
    -- Endpoints seen in the backlog that have no row yet start closed
    INSERT INTO sunat_endpoint (id)
    SELECT DISTINCT endpoint FROM document
    WHERE status IN ('pending_send', 'in_progress', 'unknown') AND next_attempt_at IS NOT NULL
    ON CONFLICT DO NOTHING;

    FOR e IN SELECT s.id, s.breaker_state, s.concurrency_cap FROM sunat_endpoint s
             WHERE NOT s.paused AND (s.breaker_state <> 'open' OR s.breaker_until <= now())
    LOOP
        v_free := CASE WHEN e.breaker_state = 'closed' THEN e.concurrency_cap ELSE 1 END
                  - (SELECT count(*) FROM job WHERE endpoint = e.id AND state IN ('waiting', 'running', 'canceling'));
        CONTINUE WHEN v_free <= 0;
        FOR d IN
            SELECT doc.id, doc.issue_year, doc.tenant_id, doc.sunat_env, doc.credential_id, doc.voided_by_document_id
            FROM document doc
            JOIN company c ON c.id = doc.company_id
            JOIN tenant t ON t.id = doc.tenant_id
            WHERE doc.endpoint = e.id
              AND doc.status IN ('pending_send', 'in_progress', 'unknown')
              AND doc.next_attempt_at <= now()
              AND doc.hold_reason IS DISTINCT FROM 'operator'
              AND NOT c.sending_paused AND NOT t.sending_paused
            ORDER BY doc.deadline_at
            LIMIT v_free
            FOR UPDATE OF doc SKIP LOCKED
        LOOP
            UPDATE document SET next_attempt_at = now() + p_lease, updated_at = now()
            WHERE id = d.id AND issue_year = d.issue_year;
            v_job := gen_random_uuid();
            -- A voiding document also holds every original it voids (§10.2)
            SELECT ARRAY['document:' || d.id] || coalesce(array_agg('document:' || o.id ORDER BY o.id), '{}')
            INTO v_resources FROM document o WHERE o.voided_by_document_id = d.id AND o.status = 'voiding';
            v_key := CASE WHEN d.credential_id IS NOT NULL THEN 'credential:' || d.credential_id ELSE 'shared:' || d.sunat_env END;
            INSERT INTO job (id, tenant_id, name, priority_class, reserved_resources, credential_key, credential_slots, endpoint, args)
            VALUES (v_job, d.tenant_id, 'document.attempt', 2, v_resources, v_key,
                    coalesce((p_slots ->> d.sunat_env)::int, 1), e.id,
                    jsonb_build_object('document_id', d.id));
            n := n + 1;
        END LOOP;
    END LOOP;
    IF n > 0 THEN
        PERFORM pg_notify('xhandler_job_wakeup', '');
    END IF;
    RETURN n;
END
$$;

-- Breaker states from the last 30 seconds of calls (§24.2). Only the
-- reconciler writes sunat_endpoint, so there is no hot row.
--   closed -> open: at least p_min_calls calls and more than half unhealthy
--   open -> half_open: when breaker_until passes (the reconciler then allows one probe)
--   half_open -> closed after a healthy call, -> open (longer) after an unhealthy one
CREATE FUNCTION xhandler.update_breakers(p_min_calls int, p_base_open interval, p_max_open interval) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    e record;
    v_total int;
    v_bad int;
    v_last boolean;
BEGIN
    FOR e IN SELECT * FROM sunat_endpoint FOR UPDATE LOOP
        SELECT count(*), count(*) FILTER (WHERE NOT healthy) INTO v_total, v_bad
        FROM sunat_call WHERE endpoint = e.id AND at > now() - interval '30 seconds';
        IF e.breaker_state = 'closed' THEN
            IF v_total >= p_min_calls AND v_bad * 2 > v_total THEN
                UPDATE sunat_endpoint SET breaker_state = 'open', open_count = 1,
                    breaker_until = now() + p_base_open, updated_at = now() WHERE id = e.id;
            END IF;
        ELSIF e.breaker_state = 'open' THEN
            IF e.breaker_until <= now() THEN
                UPDATE sunat_endpoint SET breaker_state = 'half_open', updated_at = now() WHERE id = e.id;
            END IF;
        ELSE
            SELECT healthy INTO v_last FROM sunat_call
            WHERE endpoint = e.id AND at >= e.updated_at ORDER BY at DESC LIMIT 1;
            IF v_last IS TRUE THEN
                UPDATE sunat_endpoint SET breaker_state = 'closed', open_count = 0, breaker_until = NULL, updated_at = now()
                WHERE id = e.id;
            ELSIF v_last IS FALSE THEN
                UPDATE sunat_endpoint SET breaker_state = 'open', open_count = e.open_count + 1,
                    breaker_until = now() + least(p_max_open, p_base_open * power(2, e.open_count)), updated_at = now()
                WHERE id = e.id;
            END IF;
        END IF;
    END LOOP;
    DELETE FROM sunat_call WHERE at < now() - interval '1 hour';
END
$$;

-- Queue and backlog figures for metrics and /api/v1/status (no tenant data)
CREATE FUNCTION xhandler.queue_stats()
    RETURNS TABLE (priority_class smallint, waiting bigint, unblocked bigint, running bigint, oldest_unblocked timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT priority_class,
           count(*) FILTER (WHERE state = 'waiting'),
           count(*) FILTER (WHERE state = 'waiting' AND unblocked_at IS NOT NULL),
           count(*) FILTER (WHERE state IN ('running', 'canceling')),
           min(unblocked_at) FILTER (WHERE state = 'waiting')
    FROM job WHERE state IN ('waiting', 'running', 'canceling') GROUP BY priority_class
$$;

CREATE FUNCTION xhandler.backlog_stats()
    RETURNS TABLE (endpoint text, status text, documents bigint, needs_attention bigint, oldest timestamptz, earliest_deadline timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    SELECT endpoint, status, count(*), count(*) FILTER (WHERE hold_reason = 'needs_attention'), min(created_at), min(deadline_at)
    FROM document WHERE status IN ('pending_send', 'in_progress', 'unknown')
    GROUP BY endpoint, status
$$;

-- Platform operations on documents (admin API, §15 / §24.1): by id, without a tenant
CREATE FUNCTION xhandler.document_tenant(p_document uuid) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT tenant_id FROM document WHERE id = p_document $$;

CREATE FUNCTION xhandler.job_tenant(p_job uuid) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT tenant_id FROM job WHERE id = p_job $$;

-- One job's state, platform ones included (admin API, tests)
CREATE FUNCTION xhandler.job_summary(p_id uuid)
    RETURNS TABLE (name text, state text, seq bigint, worker text, tenant_id uuid, error jsonb)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ SELECT name, state, seq, worker, tenant_id, error FROM job WHERE id = p_id $$;

-- Cancels any job, platform ones included (admin CLI cancel-job)
CREATE FUNCTION xhandler.cancel_job_any(p_job uuid) RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
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
    FOREACH f IN ARRAY ARRAY['ensure_partitions(date)', 'unblock_jobs()', 'claimable_jobs(int)', 'start_job(uuid,text)',
        'finish_job(uuid,text,text,jsonb,jsonb)', 'job_cancel_requested(uuid)', 'jobs_of_dead_workers(interval)',
        'recover_job(uuid)', 'delete_dead_workers(interval)', 'enqueue_platform_job(uuid,text,smallint,jsonb,text[],text,int)',
        'dispatch_schedules()', 'purge_jobs(interval)', 'reconcile(interval,jsonb)', 'update_breakers(int,interval,interval)',
        'queue_stats()', 'backlog_stats()', 'document_tenant(uuid)', 'job_tenant(uuid)', 'job_summary(uuid)',
        'cancel_job_any(uuid)']
    LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION xhandler.%s FROM PUBLIC', f);
        EXECUTE format('GRANT EXECUTE ON FUNCTION xhandler.%s TO xhandler_app', f);
    END LOOP;
END
$$;

-- Platform schedules (§10.6). More are added by later phases.
INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('partitions', 'platform.partitions', 86400, 4),
    ('job-purge', 'platform.job-purge', 3600, 4);
