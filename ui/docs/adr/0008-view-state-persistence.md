---
name: view-state-persistence
description: A list's filters, sort, page and page size are kept where the list chooses (the address, the browser tab, the browser, or only while on screen); lists on one screen never clash, and bad or stale values fall back to defaults.
status: Accepted
---

# 0008. Where a screen's view state is kept

## Context

Lists lose their filters, sort and page on reload, on Back and when the user leaves and comes
back, and none of it can be shared as a link. Not every list wants the same lifetime: a main
list should be shareable, a picker in a dialog should start fresh. Screens can show several lists
at once, which overwrite each other if they keep their state under the same names. (Adopted
from pulp-ui-2's ADR 0013.)

## Rules

- [ ] Each piece of a list's view state (filters, sort, page and page size) is kept in one of four
      places, chosen per list: **the address** (survives reload, travels with a link), **the
      browser tab**, **the browser** (shared by all tabs) or **the screen** (gone when the list
      leaves it).
- [ ] By default a page's main list keeps its state in the address; a list in a dialog, an
      expanded row or a card keeps it on the screen only.
- [ ] Reloading, or opening a copied link, shows the list the address describes.
- [ ] Changing a filter, the sort or the page doesn't add a Back step.
- [ ] The address holds only what differs from the defaults, each piece as its own readable
      entry; filters use Spanish names (`?texto=…&tipoPersona=NATURAL&page=2`).
- [ ] Lists on one screen never overwrite each other: nested or reused ones are prefixed with a
      name the screen chooses.
- [ ] A value that isn't valid any more falls back to its default instead of failing.
- [ ] State kept in the browser tab or the browser is kept per tenant.
- [ ] Changing a filter, the sort or the page size goes back to the first page; restored filters
      show in the toolbar and clear like any other.

## Consequences

- Lists can be linked and reloaded; defaults stay out of the address.
- Links depend on filter names, so renaming one breaks old links; the fallback rule degrades
  them to the default view.
