import type { SetStateAction } from "react";
import {
  use,
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
} from "react";

import {
  type AnyRouter,
  useMatch,
  useParams,
  useRouter,
} from "@tanstack/react-router";
import type { z } from "zod";

import { PersistentStateScopeContext } from "@app/context/PersistentStateScope/PersistentStateScopeContext";

import {
  decode,
  encode,
  isEqualValue,
  type PersistentStateSchema,
} from "./persistentStateCodec";
import {
  claimKeys,
  createMemoryStore,
  createUrlStore,
  createWebStorageStore,
  type IFieldStore,
} from "./persistentStateStores";

export type { PersistentStateSchema } from "./persistentStateCodec";

/** Where a state is kept, each with its natural lifetime (ADR 0008). */
export type PersistentStorage = "url" | "session" | "local" | "state";

/** What a state is, independent of where it's kept; declare it at module scope. */
export interface IPersistentStateDefinition<S extends PersistentStateSchema> {
  /** Names the state in session/local storage; URL params use the field names. */
  key: string;
  /** One schema per field; stored values arrive as strings (`z.coerce.number()`, `z.stringbool()`). */
  schema: S;
  /** List fields need a list default (`[]`). */
  defaultValue: z.output<S>;
}

export interface IUsePersistentStateOptions<
  S extends PersistentStateSchema,
> extends IPersistentStateDefinition<S> {
  storage: PersistentStorage;
}

export type PersistentStateSetter<T> = (next: SetStateAction<T>) => void;

/** Type-checks a definition's default against its schema; declare it at module scope so the setter stays stable. */
export const definePersistentState = <S extends PersistentStateSchema>(
  definition: IPersistentStateDefinition<S>,
): IPersistentStateDefinition<S> => definition;

type StoreConfig =
  | { storage: "url"; params: Record<string, string> }
  | { storage: "session" | "local"; key: string }
  | { storage: "state" };

/** Where a state's store points: its URL params, its storage key, or nowhere (memory). */
const getStoreConfig = ({
  storage,
  fieldNames,
  key,
  prefix,
  routeId,
  tenantId,
}: {
  storage: PersistentStorage;
  fieldNames: string[];
  key: string;
  prefix: string;
  routeId: string | undefined;
  tenantId: string | undefined;
}): StoreConfig => {
  const scoped = (name: string) => (prefix ? `${prefix}.${name}` : name);
  switch (storage) {
    case "url":
      return {
        storage,
        params: Object.fromEntries(
          fieldNames.map((name) => [name, scoped(name)]),
        ),
      };
    case "state":
      return { storage };
    default:
      return {
        storage,
        key: `openubl-ui/state:${routeId ?? ""}:${tenantId ?? ""}:${scoped(key)}`,
      };
  }
};

const claimsOf = (config: StoreConfig): string[] => {
  switch (config.storage) {
    case "url":
      return Object.values(config.params).map(
        (param) => `the URL parameter "${param}"`,
      );
    case "state":
      return [];
    default:
      return [`the ${config.storage} storage key "${config.key}"`];
  }
};

const createStore = ({
  config,
  router,
  memoryStore,
}: {
  config: StoreConfig;
  router: AnyRouter;
  memoryStore: IFieldStore;
}): IFieldStore => {
  switch (config.storage) {
    case "url":
      return createUrlStore({ router, params: config.params });
    case "state":
      return memoryStore;
    default:
      return createWebStorageStore({ area: config.storage, key: config.key });
  }
};

/** Returns the previous snapshot while it's unchanged, as `useSyncExternalStore` requires. */
const createSnapshotCache = (store: IFieldStore) => {
  let cached: { token: string; value: Record<string, unknown> } | undefined;
  return <T extends Record<string, unknown>>(read: () => T): T => {
    const token = store.token();
    if (cached?.token !== token) {
      const value = read();
      cached = {
        token,
        value:
          cached && isEqualValue(cached.value, value) ? cached.value : value,
      };
    }
    return cached.value as T;
  };
};

/**
 * `useState` kept in the URL, the tab, the browser or the component, as `storage` says (ADR 0008).
 * The setter is stable while `schema` and `defaultValue` are.
 */
export const usePersistentState = <S extends PersistentStateSchema>({
  key,
  storage,
  schema,
  defaultValue,
}: IUsePersistentStateOptions<S>): [
  z.output<S>,
  PersistentStateSetter<z.output<S>>,
] => {
  type T = z.output<S>;
  const router = useRouter();
  const prefix = use(PersistentStateScopeContext);
  const { tenantId } = useParams({ strict: false });
  const routeId = useMatch({ strict: false, select: (match) => match.routeId });
  const [memoryStore] = useState(createMemoryStore);

  // A string, so the store is rebuilt only when where it points really changes.
  const configKey = JSON.stringify(
    getStoreConfig({
      storage,
      fieldNames: Object.keys(schema.shape),
      key,
      prefix,
      routeId,
      tenantId,
    }),
  );

  // Memoized for identity: useSyncExternalStore resubscribes whenever `subscribe` changes.
  const { store, readSnapshot } = useMemo(() => {
    const created = createStore({
      config: JSON.parse(configKey) as StoreConfig,
      router,
      memoryStore,
    });
    return { store: created, readSnapshot: createSnapshotCache(created) };
  }, [configKey, router, memoryStore]);

  const read = useCallback(
    (): T => decode({ fields: store.read(), schema, defaultValue }),
    [store, schema, defaultValue],
  );
  const value = useSyncExternalStore(store.subscribe, () => readSnapshot(read));

  useEffect(() => {
    if (!import.meta.env.DEV) return;
    return claimKeys(claimsOf(JSON.parse(configKey) as StoreConfig));
  }, [configKey]);

  // Stable like useState's setter; reads the latest value so updater calls compose.
  const setValue = useCallback<PersistentStateSetter<T>>(
    (next) => {
      const resolved = next instanceof Function ? next(read()) : next;
      store.write(encode({ value: resolved, defaultValue }));
    },
    [store, read, defaultValue],
  );

  return [value, setValue];
};
