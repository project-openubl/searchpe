-- Roles as in production (DESIGN.md §6). Neither the owner nor the app is a
-- superuser (only the bootstrap "postgres" user is):
--   xhandler_owner  owns the schema and runs the migrations
--   xhandler_login  the app: a non-owner member of xhandler_app, so row-level
--                   security applies to everything it does
CREATE ROLE xhandler_owner LOGIN PASSWORD 'xhandler_owner' NOSUPERUSER NOBYPASSRLS CREATEROLE;
CREATE ROLE xhandler_app NOLOGIN;
GRANT xhandler_app TO xhandler_owner WITH ADMIN OPTION;
CREATE ROLE xhandler_login LOGIN PASSWORD 'xhandler_login' NOSUPERUSER NOBYPASSRLS INHERIT;
GRANT xhandler_app TO xhandler_login;
GRANT CREATE ON DATABASE xhandler TO xhandler_owner;
GRANT ALL ON SCHEMA public TO xhandler_owner;
