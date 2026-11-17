import type React from "react";
import { use } from "react";

import { PersistentStateScopeContext } from "./PersistentStateScopeContext";

interface IPersistentStateScopeProviderProps {
  /** Short name, no dots; nested scopes join with "." (`add.page`). */
  prefix: string;
  children: React.ReactNode;
}

/**
 * Namespaces every `usePersistentState` below it, so a reused list doesn't clash with the
 * screen's main list (ADR 0008). The screen that shows the list chooses the prefix.
 */
export const PersistentStateScopeProvider: React.FC<
  IPersistentStateScopeProviderProps
> = ({ prefix, children }) => {
  const parent = use(PersistentStateScopeContext);
  return (
    <PersistentStateScopeContext
      value={parent ? `${parent}.${prefix}` : prefix}
    >
      {children}
    </PersistentStateScopeContext>
  );
};
