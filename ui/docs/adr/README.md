# Architecture decision records

Each record is one UI design decision: the problem, the rules the UI follows, and what follows
from them. Records describe the product, never the code.

**For AI agents:** before changing a screen or a flow, pick the records that apply from their
`description` (`grep -H '^description:' docs/adr/0*.md`) and follow their `## Rules`. The
rules are a pass/fail checklist for how the running UI behaves. Where a rule maps to code, a
self-contained code rule in [.rulesync/rules/](../../.rulesync/rules/) restates it for the code.

To change a decision, add a new record with the next number, set the old one's `status` to
`Superseded by NNNN`, and update the code rules that cover the changed behaviour.

The UI follows [pulp-ui-2](https://github.com/pulp/pulp-ui-2)'s design (xhandler
`docs/DESIGN.md` §18). Its plugin ADRs (0001, 0010) have no counterpart here: xhandler has one
API and no plugins, so "plugins as data" is dropped until document types need it (U2, manual
issuing); its task ADRs become [0007](0007-job-reporting.md).

| #                                                 | Decision                                                            |
| ------------------------------------------------- | ------------------------------------------------------------------- |
| [0001](0001-one-shell-two-backends.md)            | One shell for two backends, each gated by its status                |
| [0002](0002-navigation-partner-tenant-company.md) | Navigation follows partner → tenant → company; the URL carries them |
| [0003](0003-public-and-authenticated-routes.md)   | Public routes never need a session; the rest always do              |
| [0004](0004-identity-and-permissions.md)          | Keycloak owns users; actions the user can't run are disabled        |
| [0005](0005-spanish-ui.md)                        | The UI is in Spanish (es-PE), with no i18n library                  |
| [0006](0006-api-error-display.md)                 | How API errors are shown: `{ kind, message, detail }`               |
| [0007](0007-job-reporting.md)                     | How xhandler jobs are reported, after Pulp's tasks                  |
| [0008](0008-view-state-persistence.md)            | Where a screen's view state (lists) is kept                         |

## Template

```markdown
---
name: kebab-case-slug
description: One line an agent can match a change against.
status: Accepted
---

# NNNN. Title

## Context

What problem or constraint forces a decision.

## Rules

- [ ] Observable behaviour the UI must have. No code, file or component names.

## Consequences

What follows, good and bad.
```
