---
name: api-error-display
description: A failed request shows what failed in the UI's words ("No se pudo …") and the server's own message and detail as returned; xhandler's error `kind` drives logic only and is never shown; form submissions fail inside the form, other actions as toasts.
status: Accepted
---

# 0006. How API errors are shown

## Context

xhandler-service answers every error as `{ kind, message, detail }`: `kind` a stable code for
programs (`invalid_document`, `wrong_password`, …), `message` Spanish text for people, `detail`
an optional precision (a SUNAT code, a field). searchpe answers `{ message }` or a framework
body. A request can also fail without an answer (network, proxy), and a toast alone loses the
server's explanation.

## Rules

- [ ] A failure names what failed, in the UI's words: "No se pudo …" (and says nothing changed
      when that is the case).
- [ ] Under it, the server's `message` as returned, then its `detail` in smaller text when
      present. The UI never rewrites a server message.
- [ ] The `kind` is never shown; screens may use it to choose what to do (open a field, offer a
      retry).
- [ ] With no answer from the server the UI says it couldn't reach it; a 403 without a message
      says the user lacks permission; an HTML error page is never shown as text.
- [ ] A form's submission error shows inside the form, which stays open with its values; a
      success closes it. Other actions report failure in a danger toast, and success in a toast
      when the result isn't visible on screen.
- [ ] A list or page that fails to load shows an error state in place of its content; a 404
      shows "no existe", whatever the reason.

## Consequences

- Support can match what the user saw with the server's logs through the server's own words.
- The backend owns its Spanish error texts; the UI only adds the "what failed" line.
