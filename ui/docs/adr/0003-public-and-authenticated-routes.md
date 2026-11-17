---
name: public-and-authenticated-routes
description: Public pages (buyer portal by capability link, OSE validity) live outside the login flow, use a bare shell and send no token; every other page requires an OIDC session and starts the login when there is none.
status: Accepted
---

# 0003. Public routes never need a session; the rest always do

## Context

Most of the UI is for signed-in users of a tenant. Some pages are for anyone: the **buyer
portal**, opened from the capability link printed in a PDF's QR code or sent by email
(`/p/documentos/<token>`, DESIGN.md §12), and the **OSE validity** check required by
RS 242-2018 (`/consulta-validez`). A buyer has no account, and a link must never bounce them
to a login page. The same app serves both.

## Rules

- [ ] Every page requires a session except the public ones; opening a private page without one
      starts the Keycloak login and comes back to that page afterwards.
- [ ] Public pages never start a login, never redirect to Keycloak and work with Keycloak down.
- [ ] Public pages use a bare shell: the brand only, no sidebar, no user menu, no status polling.
- [ ] Requests from public pages carry no token, even when the browser has a session, and call
      only the API's public endpoints.
- [ ] An invalid, revoked or expired capability link explains itself in plain words and how to
      get a new one; it never shows a generic error or a login page.
- [ ] Public addresses are short and Spanish, and never change once printed in a document.

## Consequences

- A capability link's token is the only credential; the UI treats it like a password (never
  logged, never in analytics).
- Adding a public page means adding it under the public group, which reviewers can see from the
  address alone.
