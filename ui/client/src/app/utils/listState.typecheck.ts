/**
 * Compile-time checks of `listSearch` through the Contribuyentes route, run by `tsc` and never
 * executed: each `@ts-expect-error` is a link that must not compile.
 */
import { linkOptions } from "@tanstack/react-router";
import { z } from "zod";

import { defineListState } from "./listState";

const to = "/padron/contribuyentes";

linkOptions({ to });
linkOptions({
  to,
  search: {
    tipoPersona: ["NATURAL"],
    texto: "bodega",
    sortBy: "nombre",
    direction: "desc",
    page: 2,
    perPage: 20,
  },
});

// @ts-expect-error not a tipo de persona
linkOptions({ to, search: { tipoPersona: ["EXTRANJERA"] } });
// @ts-expect-error not a sortable column
linkOptions({ to, search: { sortBy: "ruc" } });
// @ts-expect-error a page size the list doesn't offer
linkOptions({ to, search: { perPage: 50 } });
// @ts-expect-error a page is a number
linkOptions({ to, search: { page: "2" } });
// @ts-expect-error a typo in a filter name
linkOptions({ to, search: { textos: "bodega" } });

// A malformed piece is reported where the list's state is written.
defineListState({
  // @ts-expect-error a piece needs a defaultValue
  filters: { key: "filters", schema: z.object({ name: z.string() }) },
});
