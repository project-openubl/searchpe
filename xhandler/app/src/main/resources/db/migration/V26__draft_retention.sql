-- Retention of deleted drafts (DESIGN.md §11.10, §16): a draft holds the buyer's personal data in
-- its spec and is no tax record, so a soft-deleted draft is purged for good some days after its
-- deletion (the daily drafts.purge job; 30 days by default). Issued drafts are kept: they are the
-- history of a document, which follows the document's retention (§13).
CREATE INDEX document_draft_deleted ON xhandler.document_draft (deleted_at) WHERE deleted_at IS NOT NULL;

-- Across tenants, so owner-run (§6); the app role has no DELETE on document_draft
CREATE FUNCTION xhandler.purge_deleted_drafts(p_days int, p_limit int) RETURNS int
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$
DECLARE
    v_count int;
BEGIN
    IF p_days < 1 THEN
        RAISE EXCEPTION 'deleted drafts are kept at least 1 day';
    END IF;
    WITH expired AS (
        SELECT id FROM document_draft
        WHERE deleted_at IS NOT NULL AND deleted_at < now() - make_interval(days => p_days)
        ORDER BY deleted_at
        LIMIT p_limit
        FOR UPDATE SKIP LOCKED
    )
    DELETE FROM document_draft d USING expired e WHERE d.id = e.id;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END
$$;
REVOKE ALL ON FUNCTION xhandler.purge_deleted_drafts(int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.purge_deleted_drafts(int, int) TO xhandler_app;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('drafts-purge', 'drafts.purge', 86400, 4);
