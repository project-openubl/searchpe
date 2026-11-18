-- API gaps the console worked around (DESIGN.md §12, §21.3).

-- Partner logo (white-label, §1.2): PDFs of a partner's tenants use it when the company has
-- none. Partners are written only through SECURITY DEFINER functions (V14).
ALTER TABLE xhandler.partner ADD COLUMN logo_ref text;

CREATE FUNCTION xhandler.set_partner_logo(p_partner uuid, p_ref text) RETURNS void
    LANGUAGE sql SECURITY DEFINER SET search_path = xhandler, pg_temp
AS $$ UPDATE partner SET logo_ref = p_ref, updated_at = now() WHERE id = p_partner $$;
REVOKE ALL ON FUNCTION xhandler.set_partner_logo(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION xhandler.set_partner_logo(uuid, text) TO xhandler_app;

-- Buyer filter on the documents list: the buyer's RUC/DNI exactly, or the start of the
-- buyer's name, case-insensitive (a prefix match on text_pattern_ops stays an index scan).
CREATE INDEX document_customer_numero ON xhandler.document (tenant_id, customer_numero, created_at);
CREATE INDEX document_customer_nombre ON xhandler.document (tenant_id, lower(customer_nombre) text_pattern_ops);
