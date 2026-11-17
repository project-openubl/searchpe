---
name: navigation-partner-tenant-company
description: xhandler pages follow the account hierarchy partner → tenant (organización) → company (empresa); tenant- and company-owned pages carry their ids in the URL, a selector switches the tenant in place, and the sidebar remembers the last tenant.
status: Accepted
---

# 0002. Navigation follows partner → tenant → company

## Context

xhandler-service's accounts are a hierarchy (DESIGN.md §1.2): a **partner** manages tenants; a
**tenant** owns one or more **companies** (one RUC each) and everything they issue. A user can
belong to several tenants with a different role in each, and a partner user manages many tenants.
Every API call below the tenant names it (`/tenants/{tenantId}/…`).

## Rules

- [ ] In Spanish the tenant is an **organización**, the company an **empresa**, the partner a
      **socio**.
- [ ] Pages of a tenant carry it in the address (`/organizaciones/<tenant>/…`); pages of a
      company carry both (`/organizaciones/<tenant>/empresas/<company>/…`); partner pages
      carry the partner (`/socios/<partner>/…`, U3). A copied link always opens the same
      tenant and company.
- [ ] **Organizaciones** lists the user's tenants with their role; picking one opens its
      companies, the tenant's landing page.
- [ ] Tenant pages show a selector of the user's tenants when there is more than one; switching
      keeps the page and re-scopes it.
- [ ] The sidebar's tenant links point at the tenant in the address, else the last tenant the
      user opened (if still a member), else their only tenant; with none of these it shows only
      **Organizaciones**. The last tenant is forgotten on logout.
- [ ] A tenant or company that doesn't exist, or that the user can't see, is the same 404 page:
      the UI never tells "forbidden" from "unknown" for tenant data.

## Consequences

- Deep links work for support and for documents shared between operators.
- Pages below a company (series, certificates, documents, U1) inherit both ids from the
  address without any global "current company" state.
