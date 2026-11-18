---
name: public-lookup-captcha
description: The public OSE validity page proves a person is asking with the CAPTCHA provider's own widget (hCaptcha or Cloudflare Turnstile, chosen by deployment), loaded only on that page; without a configured site key it falls back to a typed verification code for development and tests.
status: Accepted
---

# 0010. The OSE validity page verifies with the provider's CAPTCHA widget

## Context

The OSE validity query (`/consulta-validez`, RS 242-2018, [0003](0003-public-and-authenticated-routes.md))
is open to anyone, so the service asks every query for a CAPTCHA token and checks it with the
provider (xhandler `docs/DESIGN.md` §12). The service verifies any provider that answers a
`siteverify` call (secret + token, `{"success": true}`): hCaptcha and Cloudflare Turnstile both
do, and the deployment picks one. Until now the page had a plain text field where the solved
token had to be pasted, which no visitor can use. In development and in end-to-end tests the
service runs a test mode that accepts a fixed token and calls no provider.

A third-party widget runs the provider's script and frames inside the page, which the page's
Content-Security-Policy forbids unless allowed.

## Rules

- [ ] With a provider and site key configured, the page shows the provider's widget, labelled
      **Verificación**, in place of any text field; its texts are requested in Spanish.
- [ ] The provider's script loads only when the validity page is opened, never on other pages.
- [ ] Sending without solving the widget is refused in the form with **Completa la
      verificación**; nothing is sent.
- [ ] Each query spends its token: after every query, answered or failed, the widget is reset
      and a new query asks for a new verification.
- [ ] A widget that expires or fails drops its token; a failure says so in the form (**No se
      pudo completar la verificación. Inténtalo de nuevo.**).
- [ ] The provider's origins are allowed by the page's security policy only while a site key is
      configured; without one, the page loads nothing from outside the app and the login server.
- [ ] Without a site key the page shows a **Código de verificación** text field, which only the
      service's test mode accepts; deployments for real visitors configure a site key.
- [ ] The provider's secret never reaches the browser; only the public site key does.
- [ ] The widget's own error reporting to the provider is turned off where the provider allows it.

## Consequences

- Operators choose hCaptcha or Turnstile with two settings (provider and site key) on the UI and
  the matching secret and verify URL on the service; a mismatch fails every query with the
  service's "Verificación CAPTCHA inválida.".
- The page depends on the provider being reachable: if its script is blocked, the query can't be
  sent, and the page says the verification failed.
- End-to-end tests run against the fallback field and the service's test mode; the widget
  itself is covered by component tests with the provider stubbed.
- Supporting another provider means a new widget entry and its origins, with no change to the
  form or the request.
