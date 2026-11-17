package io.github.projectopenubl.xhandler.documents;

/**
 * Fired synchronously inside the transaction that changed a document's status, with the tenant
 * open, so observers (voiding, webhooks, usage, notifications) write in the same transaction.
 * Observers receive the managed entity: they must not keep it beyond the call (§10.8).
 */
public record DocumentStatusChanged(Document document, String previousStatus, String correlationId) {}
