import type { AnyRouter } from "@tanstack/react-router";

import {
  type RawFields,
  type RawWrite,
  toRawFields,
} from "./persistentStateCodec";

/** Where one persistent state's fields live (ADR 0008). */
export interface IFieldStore {
  read: () => RawFields;
  /** Changes whenever `read()` may return something different. */
  token: () => string;
  write: (fields: RawWrite) => void;
  subscribe: (onChange: () => void) => () => void;
}

const applyWrite = <V>({
  current,
  write,
}: {
  current: Record<string, V>;
  write: RawWrite;
}): Record<string, V | string | string[]> => {
  const next: Record<string, V | string | string[]> = { ...current };
  for (const [name, value] of Object.entries(write)) {
    if (value === undefined) delete next[name];
    else next[name] = value;
  }
  return next;
};

/**
 * Fields in the URL, one param each. Reads history directly because it updates synchronously;
 * writes merge with `replace`, so two writes in one tick both land and add no Back step.
 */
export const createUrlStore = ({
  router,
  params,
}: {
  router: AnyRouter;
  /** Field name → query param name (`page` → `add.page` inside a scope). */
  params: Record<string, string>;
}): IFieldStore => {
  const toParams = (write: RawWrite): RawWrite =>
    Object.fromEntries(
      Object.entries(write).flatMap(([name, value]) =>
        name in params ? [[params[name], value]] : [],
      ),
    );
  return {
    read: () => {
      const search = router.options.parseSearch(
        router.history.location.search,
      ) as Record<string, unknown>;
      return toRawFields(
        Object.fromEntries(
          Object.entries(params).map(([name, param]) => [name, search[param]]),
        ),
      );
    },
    token: () => router.history.location.search,
    write: (write) => {
      void router.navigate({
        to: ".",
        search: (prev: Record<string, unknown>) =>
          applyWrite({ current: prev, write: toParams(write) }),
        replace: true,
        resetScroll: false,
      });
    },
    subscribe: (onChange) => router.history.subscribe(onChange),
  };
};

// The `storage` event only reaches other tabs, so same-tab writes notify through this.
const sameTabListeners = new Map<string, Set<() => void>>();

/** Fields as one JSON object under `key`; local storage also follows other tabs. */
export const createWebStorageStore = ({
  area,
  key,
}: {
  area: "session" | "local";
  key: string;
}): IFieldStore => {
  const storage = () =>
    area === "session" ? window.sessionStorage : window.localStorage;
  const listenerKey = `${area}:${key}`;
  const read = (): RawFields => {
    try {
      const parsed: unknown = JSON.parse(storage().getItem(key) ?? "{}");
      return parsed && typeof parsed === "object"
        ? toRawFields(parsed as Record<string, unknown>)
        : {};
    } catch {
      return {};
    }
  };
  return {
    read,
    token: () => storage().getItem(key) ?? "",
    write: (write) => {
      const next = applyWrite({ current: read(), write });
      try {
        if (Object.keys(next).length === 0) storage().removeItem(key);
        else storage().setItem(key, JSON.stringify(next));
      } catch (error) {
        // Quota exceeded or storage disabled: the state just isn't kept.
        console.error(error);
      }
      sameTabListeners.get(listenerKey)?.forEach((listener) => listener());
    },
    subscribe: (onChange) => {
      const listeners = sameTabListeners.get(listenerKey) ?? new Set();
      sameTabListeners.set(listenerKey, listeners.add(onChange));
      const onOtherTab = (event: StorageEvent) => {
        if (event.key === key && event.storageArea === storage()) onChange();
      };
      if (area === "local") window.addEventListener("storage", onOtherTab);
      return () => {
        listeners.delete(onChange);
        if (listeners.size === 0) sameTabListeners.delete(listenerKey);
        window.removeEventListener("storage", onOtherTab);
      };
    },
  };
};

/** Fields in memory; the owning component keeps the store, so they go when it unmounts. */
export const createMemoryStore = (): IFieldStore => {
  let fields: RawFields = {};
  let version = 0;
  const listeners = new Set<() => void>();
  return {
    read: () => fields,
    token: () => String(version),
    write: (write) => {
      fields = toRawFields(applyWrite({ current: fields, write }));
      version += 1;
      listeners.forEach((listener) => listener());
    },
    subscribe: (onChange) => {
      listeners.add(onChange);
      return () => listeners.delete(onChange);
    },
  };
};

const claimed = new Set<string>();

/** Claims a mounted state's URL params or storage key; throws on a clash, so a missing scope fails fast in dev. */
export const claimKeys = (keys: readonly string[]): (() => void) => {
  const taken = keys.find((key) => claimed.has(key));
  if (taken) {
    throw new Error(
      `Two persistent states on one screen use ${taken}. Wrap one of them in <PersistentStateScopeProvider prefix="…">.`,
    );
  }
  keys.forEach((key) => claimed.add(key));
  return () => keys.forEach((key) => claimed.delete(key));
};
