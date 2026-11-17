import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
} from "@tanstack/react-router";
import { describe, expect, it } from "vitest";
import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";

import {
  defineListState,
  defineSortState,
  estimateItemCount,
  listSearch,
  PAGINATION_STATE,
  schemaFromGuard,
} from "./listState";
import { parseSearch, stringifySearch } from "./searchParams";

const isWidgetType = (value: string): value is "a.a" | "b.b" =>
  value === "a.a" || value === "b.b";

const WIDGET_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  sort: defineSortState({
    columnKeys: ["name", "size"],
    defaultValue: { sortBy: "name", direction: "asc" },
  }),
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      name: z.string(),
      types: z.array(schemaFromGuard(isWidgetType)),
    }),
    defaultValue: { name: "", types: [] },
  }),
});
const widgetSearch = listSearch(WIDGET_LIST_STATE);

const createWidgetRouter = (url: string) => {
  const root = createRootRoute();
  const widgets = createRoute({
    getParentRoute: () => root,
    path: "/widgets",
    ...widgetSearch,
  });
  return createRouter({
    routeTree: root.addChildren([widgets]),
    history: createMemoryHistory({ initialEntries: [url] }),
    parseSearch,
    stringifySearch,
  });
};

describe("listSearch", () => {
  it("decodes the list's params and drops the invalid ones", () => {
    expect(
      widgetSearch.validateSearch(
        parseSearch(
          "?page=2&perPage=50&sortBy=size&direction=up&types=a.a&types=z.z",
        ),
      ),
    ).toEqual({ page: 2, sortBy: "size", types: ["a.a"] });
  });

  it("reads a single value of a list field as a one-item list", () => {
    expect(widgetSearch.validateSearch(parseSearch("?types=b.b"))).toEqual({
      types: ["b.b"],
    });
  });

  it("leaves defaults out of a link's address", async () => {
    const router = createWidgetRouter("/");
    await router.load();
    // `navigate` is typed by the app's registered routes; this test router isn't one of them.
    await router.navigate({
      to: "/widgets",
      search: { page: 1, perPage: 10, name: "x", types: [], sortBy: "size" },
    } as never);
    expect(router.state.location.searchStr).toBe("?name=x&sortBy=size");
  });

  it("keeps another list's params in the address", async () => {
    const router = createWidgetRouter("/widgets?add.page=3&page=2");
    await router.load();
    expect(router.state.location.searchStr).toBe("?add.page=3&page=2");
    expect(router.state.matches.at(-1)?.search).toMatchObject({
      "add.page": "3",
      page: 2,
    });
  });
});

describe("estimateItemCount", () => {
  it("offers one more page only when the page came back full", () => {
    const pagination = { page: 3, perPage: 10 } as const;
    expect(estimateItemCount({ pagination, pageSize: 10 })).toBe(31);
    expect(estimateItemCount({ pagination, pageSize: 4 })).toBe(24);
  });
});
