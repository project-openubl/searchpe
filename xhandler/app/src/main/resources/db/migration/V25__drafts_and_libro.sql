-- Manual issuing (DESIGN.md §9.1, §11.10): a draft is a document being edited in xhandler-ui,
-- with no number. Issuing it goes through emit, which numbers it and marks the draft issued in
-- the same transaction, so a draft gives at most one document.
CREATE TABLE xhandler.document_draft (
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES xhandler.tenant (id),
    company_id       uuid        NOT NULL REFERENCES xhandler.company (id),
    kind             text        NOT NULL,
    tipo_comprobante text        NOT NULL,
    serie            text,
    spec             jsonb       NOT NULL,
    labels           jsonb       NOT NULL DEFAULT '{}',
    status           text        NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'issued')),
    version          int         NOT NULL DEFAULT 1,
    document_id      uuid,
    created_by       text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz,
    CHECK ((status = 'issued') = (document_id IS NOT NULL))
);
CREATE INDEX document_draft_list ON xhandler.document_draft (tenant_id, status, created_at DESC) WHERE deleted_at IS NULL;

ALTER TABLE xhandler.document_draft ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON xhandler.document_draft
    USING (tenant_id = xhandler.current_tenant()) WITH CHECK (tenant_id = xhandler.current_tenant());
-- Soft delete only (§7): no DELETE
GRANT SELECT, INSERT, UPDATE ON xhandler.document_draft TO xhandler_app;

-- The issuer's own Libro de Reclamaciones (Ley 32495), linked from the buyer portal (§12)
ALTER TABLE xhandler.company ADD COLUMN libro_reclamaciones_url text;
