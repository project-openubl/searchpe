-- Applied migrations for xhandler-admin migration-status. The history table belongs to the
-- migration owner; the app role only reads it through here.
CREATE FUNCTION xhandler.migration_history()
    RETURNS TABLE (version text, description text, success boolean, installed_on timestamp)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp
AS $$
    SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank
$$;
REVOKE ALL ON FUNCTION xhandler.migration_history() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.migration_history() TO xhandler_app;
