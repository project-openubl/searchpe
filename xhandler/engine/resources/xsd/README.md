# XSD schemas

Public schemas used by `POST /v1/validate` (DESIGN.md §19 R5). They are
embedded in the engine binary (`src/xsd.rs`), and imports resolve only among
these files: the engine never reads schemas from disk or the network.

- `2.1/`: OASIS UBL 2.1 (Invoice, CreditNote, DebitNote, DespatchAdvice,
  ApplicationResponse for the OSE CDR), as published by SUNAT.
- `2.0/`: OASIS UBL 2.0 plus SUNAT's `UBLPE-*` extensions (SummaryDocuments,
  VoidedDocuments, Perception, Retention).

Source: copied unchanged from xhandler-rust
(`xhandler/tests/resources/xbuilder/xsd/`, rev `56ea477`), which takes them from
SUNAT's published packages (cpe.sunat.gob.pe). Files no schema imports were
left out. Update them together with the xhandler-rust pin.
