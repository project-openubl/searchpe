---
name: spanish-ui
description: Every user-facing text is Spanish (es-PE) written directly in the code, with Peruvian date and number formats; no i18n library, keys or language switch; SUNAT terms stay as SUNAT writes them.
status: Accepted
---

# 0005. The UI is in Spanish (es-PE), with no i18n library

## Context

Users are Peruvian taxpayers, accountants and their software vendors; SUNAT, its forms and its
law are in Spanish, and so are xhandler's API error messages, emails and PDFs (DESIGN.md
§26.9). A second language isn't planned. An i18n library would add keys, extraction and
indirection for one language.

## Rules

- [ ] Every text the user sees (titles, labels, buttons, tooltips, toasts, empty states, errors
      the UI writes, accessible names) is Spanish, written in the screen that shows it.
- [ ] SUNAT and domain terms are used as SUNAT writes them (RUC, DNI, razón social, serie,
      comprobante, CDR, padrón reducido) and are never translated or anglicized.
- [ ] Dates show as `DD/MM/YYYY` (with `HH:mm:ss` when the time matters) and relative times in
      Spanish; numbers and amounts use es-PE grouping.
- [ ] Values the server returns (states, SUNAT descriptions, error messages) are shown as
      returned unless the UI has a Spanish label for a known value; an unknown value still shows
      as returned.
- [ ] PatternFly's built-in English texts (pagination, filters, close buttons) are replaced
      wherever the component accepts it.

## Consequences

- Copy is reviewed with the code; there is no translation step.
- Adding a language later means extracting every string: a deliberate, new decision.
