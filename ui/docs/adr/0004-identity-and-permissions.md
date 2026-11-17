---
name: identity-and-permissions
description: Users live only in Keycloak (OIDC, one realm); searchpe's basic-auth user management is dropped; actions the user can't run are disabled with a tooltip, from searchpe's whoami permissions and the user's role in each xhandler tenant.
status: Accepted
---

# 0004. Keycloak owns users; actions the user can't run are disabled

## Context

Both backends trust one Keycloak realm. searchpe's old UI also managed its own basic-auth users
(a Users page: create, edit, delete, passwords) and a profile form that renamed them; with OIDC
those users and forms have no meaning. Permissions differ per backend: searchpe grants `search`,
`version:write` and `admin:app` (realm roles, reported by `whoami`); xhandler grants a role per
tenant membership (owner, admin, operator, viewer; support reads only).

## Rules

- [ ] Login and logout are Keycloak's (OIDC authorization code with PKCE); the UI has no login
      form and stores no password.
- [ ] There is no user management in the UI: searchpe's Users page is gone, and name, email,
      password and two-factor settings change in Keycloak's account console, linked from
      **Mi perfil**.
- [ ] **Mi perfil** shows who the user is, their searchpe permissions and their tenants with
      their role.
- [ ] Buttons and menu actions the user can't run stay visible, disabled, with a tooltip naming
      the permission or role they need. Small inline controls may be omitted instead.
- [ ] Until permissions load, or when they can't be read, nothing is disabled; the server still
      enforces every call. A searchpe running with authorization off allows everything.
- [ ] A page the user may not use at all shows an unauthorized state in place of its content.
- [ ] An expired session renews silently; if it can't, the user is signed out.

## Consequences

- Fewer screens, one source of truth for identity; deployments that used searchpe's basic auth
  must move their users to Keycloak before adopting this UI.
- The UI never decides access on its own: a wrong gate shows a button that the server refuses,
  never data the user shouldn't see.
