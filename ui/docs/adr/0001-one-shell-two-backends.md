---
name: one-shell-two-backends
description: One app shell (masthead + expandable sidebar) covers xhandler-service and searchpe; each product's section shows only while its backend answers its status, searchpe only when deployed, and one backend down never blocks the other.
status: Accepted
---

# 0001. One shell for two backends, each gated by its status

## Context

The platform has two backends with separate APIs, databases and release cycles: xhandler-service
(SUNAT electronic documents, multi-tenant) and searchpe (RUC lookup over SUNAT's padrón). Users
sign in once (one Keycloak realm) and should not need two UIs. searchpe is optional in a
deployment, and either backend can be down while the other works.

## Rules

- [ ] One app serves both products: one masthead (sidebar toggle, brand, help and user menus)
      and one sidebar of expandable groups: **Inicio**, **Comprobantes electrónicos**
      (xhandler-service), **Padrón RUC** (searchpe) and **Cuenta**. New pages join one of them.
- [ ] The sidebar's current group opens on load and whenever navigation enters another group.
      On wide screens the sidebar starts open and remembers the user's toggle; on narrow screens
      it starts closed, overlays the page and closes after a pick. A closed sidebar can't be
      reached with the keyboard.
- [ ] Each product's pages show in the sidebar only while its backend answers its public status
      check; otherwise the group says the service is unavailable. The status is polled, so the
      section comes back on its own.
- [ ] When searchpe isn't deployed, the Padrón RUC group, its cards and its pages don't exist at
      all (its addresses are a 404), and the UI never calls it.
- [ ] One backend being down or slow never blocks the shell or the other product's pages.
- [ ] The home page shows one card per deployed product with its availability and a way in.
- [ ] The browser talks to both backends only through the UI's own origin, one path prefix per
      backend; it never learns the backends' addresses.

## Consequences

- One login, one look, one set of conventions for both products.
- Each product keeps its own API conventions (pagination, errors, permissions); shared
  components take them as data, not as a common model.
- A page of a product whose backend is down still opens from a bookmark and shows its own error
  state; only the navigation hides it.
