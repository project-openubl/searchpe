---
name: manual-issuing-drafts
description: Manual issuing works on drafts without a number — created by company and document type, edited and saved freely, issued once after a confirmation; the document types the form offers are a data list, and an issued draft is read-only.
status: Accepted
---

# 0009. Manual issuing goes through drafts

## Context

Operators without an ERP issue facturas, boletas and notes by hand in the console (xhandler
`docs/DESIGN.md` §18, §11.10). A document gets its number only when it is issued, and an issued
document never changes: a mistake is corrected with a note. Filling a document takes time and
may be interrupted, and two operators may open the same one. pulp-ui-2 models plugin types as
data (its ADR 0001); [0001](README.md) left that open until document types needed it.

## Rules

- [ ] **Emisión manual** lists the organization's drafts, newest first, filterable by status
      (borrador, emitido).
- [ ] A new draft asks only for the company and the document type (factura, boleta de venta,
      nota de crédito, nota de débito); everything else is filled in the draft's own page.
- [ ] The document types the console issues are one data list (type, xbuilder kind, Spanish
      name); adding a type adds an entry, not a screen.
- [ ] A draft never shows a number: the series is chosen (or left to the company's default) and
      the service numbers it when issued.
- [ ] Saving keeps the draft as it is; SUNAT's fixed formats (RUC 11 digits, DNI 8, serie-número)
      are checked first. A draft changed elsewhere since it was opened isn't overwritten: the
      save fails and says so.
- [ ] Issuing asks for confirmation, saves pending changes first, and reports the number given.
      If the service refuses the document, the reason (with SUNAT's codes) shows in the form and
      the draft stays editable.
- [ ] An issued draft is read-only and shows the document it became; it can't be deleted.
- [ ] Deleting a draft asks for confirmation; it never consumed a number.

## Consequences

- Nothing is numbered until the operator decides, so abandoned drafts leave no gaps in a series.
- Document types beyond the first four (guías, percepciones, retenciones) can be offered by
  adding entries and their fields, without changing the flow.
