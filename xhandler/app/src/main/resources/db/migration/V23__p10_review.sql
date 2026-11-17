-- P10 review fixes (DESIGN.md §11.4).

-- What a ticket answers for: every document of a sendSummary/sendPack, including identical
-- resends of documents accepted before (they point at the existing submission)
CREATE TABLE xhandler.ose_ticket_item (
    ticket        text NOT NULL,
    ose_client_id uuid NOT NULL REFERENCES xhandler.ose_client (id),
    submission_id uuid NOT NULL REFERENCES xhandler.ose_submission (id) ON DELETE CASCADE,
    PRIMARY KEY (ticket, submission_id)
);
CREATE INDEX ose_ticket_item_client ON xhandler.ose_ticket_item (ose_client_id, ticket);
GRANT SELECT, INSERT, DELETE ON xhandler.ose_ticket_item TO xhandler_app;
INSERT INTO xhandler.ose_ticket_item (ticket, ose_client_id, submission_id)
    SELECT ticket, ose_client_id, id FROM xhandler.ose_submission WHERE ticket IS NOT NULL;

-- A forward SUNAT refused for a reason that isn't the document (credentials, package): kept
-- queued, waiting for a person
ALTER TABLE xhandler.ose_submission DROP CONSTRAINT ose_submission_forward_status_check;
ALTER TABLE xhandler.ose_submission ADD CONSTRAINT ose_submission_forward_status_check
    CHECK (forward_status IN ('pending', 'forwarded', 'rejected', 'blocked'));
CREATE INDEX ose_submission_blocked ON xhandler.ose_submission (cdr_issued_at) WHERE forward_status = 'blocked';

-- The forwarding run that holds a row: outcomes are written only by that run
ALTER TABLE xhandler.ose_submission ADD COLUMN forward_lease uuid;
