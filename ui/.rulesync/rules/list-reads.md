---
name: list-reads
description: "Apply when a page, component or query reads a list from either backend: every read is one server page sized by the UI (table page, count, dropdown), never a hardcoded limit or a fetch-all; the few unpaged endpoints are named exceptions."
root: false
targets: ["claudecode", "cursor"]
globs:
  [
    "client/src/app/queries/**",
    "client/src/app/components/**",
    "client/src/app/pages/**",
  ]
---

# Reading lists

Every list read is **one page** of a generic list query (`use<X>ListQuery({ params })`); the
caller passes its own filters, `limit` and `offset`. [queries](queries.md) owns the
one-query-per-endpoint rule.

## `limit` comes from the UI, never a literal

- **Paged table** — `toPageParams(pagination)` (`@app/utils/listState`): `perPage` from the
  list's pagination state ([list-state](list-state.md)), offset `(page - 1) * perPage`.
- **Count-only** — `limit: 1`, read the count (searchpe's `meta.count`). xhandler pages have no
  count: don't fake one with a big `limit`.
- **Dropdown** — `DROPDOWN_PAGE_SIZE`, `offset: 0`, with a server filter on the typed term when
  the endpoint has one.

Anything else is a violation.

## Totals

- searchpe lists answer `meta.count`: pass it as `itemCount`.
- xhandler pages (`{ items, limit, offset }`) answer no total: pass
  `estimateItemCount({ pagination, pageSize })` and `isCountEstimated` to `ListPagination`.

## Never fetch every page

No fetch-all helper, no `useAll*` query, no client-side join/filter/sort over a whole
collection. A list the user scrolls gets a pager, a picker searches server-side.

**Named exceptions** (the API offers no paging; each is read whole and paged on screen, with a
comment at the call):

- searchpe `GET /versions` (the clean-up job keeps few versions);
- xhandler `GET /tenants` (one row per membership of the user);
- xhandler `listMyPartners` (one row per partner membership of the user);
- xhandler `listPartnerTenants` (one row per tenant of the partner, filtered by month);
- xhandler `listAdapterTokens` (a company has a few adapter tokens);
- xhandler `listCredentials` (one row per credential kind);
- xhandler `listPseAuthorizations` (a handful per company, newest first);
- xhandler `listSeries` (a company has a handful of series);
- xhandler `listDocumentEvents` (one row per status change of the document);
- xhandler `listEmailSuppressions` (one row per address that bounced or complained);
- xhandler `listExports` (the service answers the latest 20);
- xhandler `listMembers` (one row per member of the tenant);
- xhandler `listSenderDomains` (a handful per tenant);
- xhandler `listWebhooks` (a tenant has a few subscriptions).

## Checklist

Pass/fail rubric for `/eval`. Check list reads in changed `queries/`, `components/` and
`pages/` files.

- [ ] List reads use the generic list query with caller-defined `params`.
- [ ] Every `limit` comes from `toPageParams`, is `1` for a count, or `DROPDOWN_PAGE_SIZE` for a
      dropdown — no other literal.
- [ ] Totals: `meta.count` for searchpe, `estimateItemCount` + `isCountEstimated` for xhandler.
- [ ] No read fetches every page, except the named unpaged endpoints, with a comment.
