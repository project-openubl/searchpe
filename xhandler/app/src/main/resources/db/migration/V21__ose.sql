-- P10: OSE role (DESIGN.md §1.1, §3.2, §11.4; RS 117-2017).
-- OSE data belongs to the platform, not to a tenant: no row-level security,
-- reached only through the OSE intake, the forwarding job and the admin API.

-- Issuers (or their PSEs) that send us documents to check (§11.4 step 1)
CREATE TABLE xhandler.ose_client (
    id              uuid PRIMARY KEY,
    issuer_ruc      text        NOT NULL CHECK (issuer_ruc ~ '^[0-9]{11}$'),
    username        text        NOT NULL UNIQUE,
    password_hash   text        NOT NULL,
    -- Billing: the tenant whose subscription carries the OSE validation line, if any
    tenant_id       uuid        REFERENCES xhandler.tenant (id),
    status          text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    failed_attempts int         NOT NULL DEFAULT 0,
    locked_until    timestamptz,
    last_login_at   timestamptz,
    created_by      text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ose_client_issuer ON xhandler.ose_client (issuer_ruc);
GRANT SELECT, INSERT, UPDATE ON xhandler.ose_client TO xhandler_app;

-- SUNAT's list of issuers associated with the platform as OSE (imported; the
-- relation counts until the 7th day of the month after it ends, RS 117-2017 art. 11.3)
CREATE TABLE xhandler.ose_association (
    ruc         text PRIMARY KEY CHECK (ruc ~ '^[0-9]{11}$'),
    starts_on   date        NOT NULL,
    ends_on     date,
    imported_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON xhandler.ose_association TO xhandler_app;

-- SUNAT's blocked-issuer lists (DL 1532, RS 000302-2024), imported per list
CREATE TABLE xhandler.blocked_issuer (
    ruc         text NOT NULL CHECK (ruc ~ '^[0-9]{11}$'),
    list        text NOT NULL,
    listed_on   date,
    imported_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (ruc, list)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON xhandler.blocked_issuer TO xhandler_app;

-- Every document checked (§11.4 steps 5–7): the CDR-OSE or the inconsistency
-- notice, the forwarding to SUNAT within 1 hour, the 1-month retention duty
CREATE TABLE xhandler.ose_submission (
    id               uuid PRIMARY KEY,
    ose_client_id    uuid        NOT NULL REFERENCES xhandler.ose_client (id),
    issuer_ruc       text        NOT NULL,
    tipo             text        NOT NULL,
    serie            text        NOT NULL,
    numero           bigint      NOT NULL,
    name             text        NOT NULL,
    channel          text        NOT NULL CHECK (channel IN ('soap', 'rest', 'sftp')),
    -- sendPack and sendSummary answer with a ticket
    ticket           text,
    received_at      timestamptz NOT NULL,
    result           text        NOT NULL CHECK (result IN ('cdr', 'inconsistency')),
    response_code    text        NOT NULL,
    description      text,
    sha256           text        NOT NULL,
    xml_ref          text,
    cdr_ref          text,
    cdr_issued_at    timestamptz,
    forward_status   text        CHECK (forward_status IN ('pending', 'forwarded', 'rejected')),
    forwarded_at     timestamptz,
    sunat_ack        text,
    forward_attempts int         NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz,
    last_error       text,
    purge_after      timestamptz NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CHECK ((result = 'cdr') = (cdr_issued_at IS NOT NULL))
);
-- Uniqueness among accepted submissions only: an issuer may resend after an inconsistency (§7.1)
CREATE UNIQUE INDEX ose_submission_accepted ON xhandler.ose_submission (issuer_ruc, tipo, serie, numero) WHERE result = 'cdr';
CREATE INDEX ose_submission_forward ON xhandler.ose_submission (next_attempt_at) WHERE forward_status = 'pending';
CREATE INDEX ose_submission_ticket ON xhandler.ose_submission (ticket) WHERE ticket IS NOT NULL;
CREATE INDEX ose_submission_purge ON xhandler.ose_submission (purge_after);
CREATE INDEX ose_submission_key ON xhandler.ose_submission (issuer_ruc, tipo, serie, numero);
GRANT SELECT, INSERT, UPDATE, DELETE ON xhandler.ose_submission TO xhandler_app;

-- The OSE validation service is billed as its own line (RS 242-2018 art. 7.16)
CREATE TABLE xhandler.ose_usage (
    ose_client_id uuid NOT NULL REFERENCES xhandler.ose_client (id),
    day           date NOT NULL,
    validations   int  NOT NULL DEFAULT 0,
    PRIMARY KEY (ose_client_id, day)
);
GRANT SELECT, INSERT, UPDATE ON xhandler.ose_usage TO xhandler_app;

INSERT INTO xhandler.job_schedule (name, job_name, interval_seconds, priority_class) VALUES
    ('ose-forward', 'ose.forward', 30, 1),
    ('ose-purge', 'ose.purge', 86400, 4),
    ('ose-sftp', 'ose.sftp', 60, 2);
