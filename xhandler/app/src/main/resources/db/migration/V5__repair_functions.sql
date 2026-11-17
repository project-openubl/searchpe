-- Data repair for xhandler-admin (DESIGN.md §15).

-- Safety net (xhandler-admin data-repair requeue-stuck): non-definitive documents that no
-- automatic attempt would pick up again (no next_attempt_at, not held, not in a summary, not a
-- boleta waiting for the resumen diario) become due now.
CREATE FUNCTION xhandler.requeue_stuck_documents() RETURNS int
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
    WITH stuck AS (
        UPDATE document SET next_attempt_at = now(), updated_at = now()
        WHERE status IN ('pending_send', 'in_progress', 'unknown') AND next_attempt_at IS NULL
          AND hold_reason IS DISTINCT FROM 'operator' AND summary_document_id IS NULL
          AND code_class IS DISTINCT FROM 'route_change'
        RETURNING 1)
    SELECT count(*)::int FROM stuck
$$;

REVOKE ALL ON FUNCTION xhandler.requeue_stuck_documents() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.requeue_stuck_documents() TO xhandler_app;
