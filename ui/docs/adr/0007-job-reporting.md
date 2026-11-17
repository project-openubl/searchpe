---
name: job-reporting
description: xhandler's background jobs (shaped like Pulp's tasks) are reported from server state: a tenant Jobs page with live states, cancel for waiting/running jobs, toasts only when a response arrives, never from polling; a masthead job drawer follows with the first screen that starts jobs (U1).
status: Accepted
---

# 0007. How xhandler jobs are reported

## Context

Long xhandler operations (SUNAT sends and checks, voiding, reports, exports, webhooks) run as
jobs (DESIGN.md §10), answered as `202` with a link and shaped like Pulp's tasks: a state
(`waiting → running → completed | failed`, plus `canceling → canceled`), timestamps, an error
and the resources created. pulp-ui-2's task reporting (its ADR 0006) applies. searchpe's padrón
imports are not jobs: their progress is the version's own status.

## Rules

- [ ] A toast reports a response when it arrives (a job started, a cancel requested) and is
      never raised later by watching a job.
- [ ] A job's state on screen is always the server's, refreshed while it can still change, so it
      survives reloads and covers jobs started elsewhere (the API, the ERP, schedules).
- [ ] **Trabajos** lists a tenant's jobs, newest first, with their state, times and error as
      returned, filterable by state and name.
- [ ] Waiting and running jobs offer **Cancelar** (operators and above); cancel asks for
      confirmation, and a canceling job shows as such until its worker stops.
- [ ] Every state has a Spanish label; a state the UI doesn't know shows as returned.
- [ ] From the first screen that starts jobs (U1): every job the user starts is listed in a
      masthead drawer with its live state, as pulp-ui-2's task drawer; until then there is no
      drawer.
- [ ] A padrón version's import shows its status on its row, refreshed while it runs; versions
      that are importing can be cancelled and finished ones deleted.

## Consequences

- Users never wait on a spinner for a job.
- The UI stays correct when jobs are started outside it, at the cost of polling while
  something runs.
