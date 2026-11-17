import type React from "react";
import { useState } from "react";

import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { afterEach, describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";
import { z } from "zod";

import { PersistentStateScopeProvider } from "@app/context/PersistentStateScope/PersistentStateScopeProvider";
import { PAGINATION_STATE } from "@app/utils/listState";
import { parseSearch, stringifySearch } from "@app/utils/searchParams";

import {
  definePersistentState,
  type PersistentStateSetter,
  type PersistentStorage,
  usePersistentState,
} from "./usePersistentState";

const TYPES_STATE = definePersistentState({
  key: "filters",
  schema: z.object({ types: z.array(z.string()) }),
  defaultValue: { types: [] },
});

// Every setter a Pager rendered with, to check it keeps one identity like useState's.
const renderedSetters = new Set<PersistentStateSetter<unknown>>();

interface IPagerProps {
  storage: PersistentStorage;
  label?: string;
}

const Pager: React.FC<IPagerProps> = ({ storage, label = "pager" }) => {
  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_STATE,
    storage,
  });
  renderedSetters.add(setPagination as PersistentStateSetter<unknown>);
  return (
    <section aria-label={label}>
      <p>
        page {pagination.page} of {pagination.perPage}
      </p>
      <button
        onClick={() =>
          setPagination((prev) => ({ ...prev, page: prev.page + 1 }))
        }
      >
        Next
      </button>
      <button
        onClick={() => {
          setPagination((prev) => ({ ...prev, perPage: 20 }));
          setPagination((prev) => ({ ...prev, page: prev.page + 1 }));
        }}
      >
        Twice
      </button>
      <button onClick={() => setPagination({ page: 1, perPage: 10 })}>
        Reset
      </button>
    </section>
  );
};

const TypeFilter: React.FC = () => {
  const [filters, setFilters] = usePersistentState({
    ...TYPES_STATE,
    storage: "url",
  });
  return (
    <>
      <p>types {filters.types.join(",") || "none"}</p>
      <button onClick={() => setFilters({ types: ["file", "rpm"] })}>
        Both
      </button>
    </>
  );
};

const renderAt = async ({ url, ui }: { url: string; ui: React.FC }) => {
  const root = createRootRoute({
    component: Outlet,
    errorComponent: ({ error }) => <p>{(error as Error).message}</p>,
  });
  const list = createRoute({
    getParentRoute: () => root,
    path: "/$tenantId/list",
    component: ui,
  });
  const router = createRouter({
    routeTree: root.addChildren([list]),
    history: createMemoryHistory({ initialEntries: [url] }),
    parseSearch,
    stringifySearch,
  });
  const screen = await render(<RouterProvider router={router} />);
  return { screen, router };
};

const search = (router: { history: { location: { search: string } } }) =>
  router.history.location.search;

afterEach(() => {
  renderedSetters.clear();
  window.sessionStorage.clear();
  window.localStorage.clear();
});

describe("usePersistentState", () => {
  describe('with storage "url"', () => {
    it("reads the state from the address", async () => {
      const { screen } = await renderAt({
        url: "/d/list?page=3&perPage=20",
        ui: () => <Pager storage="url" />,
      });
      await expect.element(screen.getByText("page 3 of 20")).toBeVisible();
    });

    it("falls back per field when a value is invalid", async () => {
      const { screen } = await renderAt({
        url: "/d/list?page=abc&perPage=20",
        ui: () => <Pager storage="url" />,
      });
      await expect.element(screen.getByText("page 1 of 20")).toBeVisible();
    });

    it("writes only non-default fields and keeps other params", async () => {
      const { screen, router } = await renderAt({
        url: "/d/list?other=x",
        ui: () => <Pager storage="url" />,
      });
      await screen.getByRole("button", { name: "Next" }).click();
      await expect.element(screen.getByText("page 2 of 10")).toBeVisible();
      expect(search(router)).toBe("?other=x&page=2");

      await screen.getByRole("button", { name: "Reset" }).click();
      expect(search(router)).toBe("?other=x");
    });

    it("applies two updates from one handler on top of each other", async () => {
      const { screen, router } = await renderAt({
        url: "/d/list",
        ui: () => <Pager storage="url" />,
      });
      await screen.getByRole("button", { name: "Twice" }).click();
      await expect.element(screen.getByText("page 2 of 20")).toBeVisible();
      expect(search(router)).toBe("?perPage=20&page=2");
    });

    it("keeps one setter identity across renders, like useState", async () => {
      const { screen } = await renderAt({
        url: "/d/list",
        ui: () => <Pager storage="url" />,
      });
      await screen.getByRole("button", { name: "Next" }).click();
      await screen.getByRole("button", { name: "Next" }).click();
      await expect.element(screen.getByText("page 3 of 10")).toBeVisible();
      expect(renderedSetters.size).toBe(1);
    });

    it("doesn't add a history entry", async () => {
      const { screen, router } = await renderAt({
        url: "/d/list",
        ui: () => <Pager storage="url" />,
      });
      await screen.getByRole("button", { name: "Next" }).click();
      await expect.element(screen.getByText("page 2 of 10")).toBeVisible();
      expect(router.history.length).toBe(1);
    });

    it("reads repeated params as an array and a single one as a one-item array", async () => {
      const { screen, router } = await renderAt({
        url: "/d/list?types=file",
        ui: () => <TypeFilter />,
      });
      await expect.element(screen.getByText("types file")).toBeVisible();

      await screen.getByRole("button", { name: "Both" }).click();
      await expect.element(screen.getByText("types file,rpm")).toBeVisible();
      expect(search(router)).toBe("?types=file&types=rpm");
    });

    it("prefixes the params of a list inside a scope", async () => {
      const { screen, router } = await renderAt({
        url: "/d/list?add.page=4",
        ui: () => (
          <>
            <Pager storage="url" label="main" />
            <PersistentStateScopeProvider prefix="add">
              <Pager storage="url" label="nested" />
            </PersistentStateScopeProvider>
          </>
        ),
      });
      const main = screen.getByRole("region", { name: "main" });
      const nested = screen.getByRole("region", { name: "nested" });
      await expect.element(main.getByText("page 1 of 10")).toBeVisible();
      await expect.element(nested.getByText("page 4 of 10")).toBeVisible();

      await main.getByRole("button", { name: "Next" }).click();
      await expect.element(main.getByText("page 2 of 10")).toBeVisible();
      expect(search(router)).toBe("?add.page=4&page=2");
    });

    it("fails fast when two lists on one screen use the same params", async () => {
      const { screen } = await renderAt({
        url: "/d/list",
        ui: () => (
          <>
            <Pager storage="url" label="first" />
            <Pager storage="url" label="second" />
          </>
        ),
      });
      await expect
        .element(screen.getByText(/use the URL parameter "page"/))
        .toBeVisible();
    });
  });

  describe('with storage "session"', () => {
    it("keeps the state per tenant and restores it after a remount", async () => {
      const Toggle: React.FC = () => {
        const [shown, setShown] = useState(true);
        return (
          <>
            <button onClick={() => setShown((prev) => !prev)}>Toggle</button>
            {shown && <Pager storage="session" />}
          </>
        );
      };
      const { screen, router } = await renderAt({ url: "/d/list", ui: Toggle });
      await screen.getByRole("button", { name: "Next" }).click();
      await expect.element(screen.getByText("page 2 of 10")).toBeVisible();
      expect(search(router)).toBe("");
      expect(Object.keys(window.sessionStorage)).toEqual([
        "openubl-ui/state:/$tenantId/list:d:pagination",
      ]);

      await screen.getByRole("button", { name: "Toggle" }).click();
      await screen.getByRole("button", { name: "Toggle" }).click();
      await expect.element(screen.getByText("page 2 of 10")).toBeVisible();
    });

    it("removes the key when the state returns to its default", async () => {
      const { screen } = await renderAt({
        url: "/d/list",
        ui: () => <Pager storage="session" />,
      });
      await screen.getByRole("button", { name: "Next" }).click();
      await screen.getByRole("button", { name: "Reset" }).click();
      await expect.element(screen.getByText("page 1 of 10")).toBeVisible();
      expect(window.sessionStorage.length).toBe(0);
    });
  });

  describe('with storage "local"', () => {
    it("follows a change made in another tab", async () => {
      const { screen } = await renderAt({
        url: "/d/list",
        ui: () => <Pager storage="local" />,
      });
      const key = "openubl-ui/state:/$tenantId/list:d:pagination";
      const newValue = JSON.stringify({ page: "5" });
      window.localStorage.setItem(key, newValue);
      window.dispatchEvent(
        new StorageEvent("storage", {
          key,
          newValue,
          storageArea: window.localStorage,
        }),
      );
      await expect.element(screen.getByText("page 5 of 10")).toBeVisible();
    });
  });

  describe('with storage "state"', () => {
    it("keeps the state in the component only", async () => {
      const { screen, router } = await renderAt({
        url: "/d/list",
        ui: () => <Pager storage="state" />,
      });
      await screen.getByRole("button", { name: "Next" }).click();
      await expect.element(screen.getByText("page 2 of 10")).toBeVisible();
      expect(search(router)).toBe("");
      expect(window.sessionStorage.length + window.localStorage.length).toBe(0);
    });
  });
});
