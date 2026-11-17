import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

interface ILastTenantState {
  /** The organization the user last opened, so the sidebar can link to its pages. */
  lastTenantId: string | null;
  setLastTenantId: (tenantId: string) => void;
  reset: () => void;
}

/** Outlives every page: the sidebar reads it outside tenant pages (ADR 0002). */
export const useLastTenantStore = create<ILastTenantState>()(
  persist(
    (set) => ({
      lastTenantId: null,
      setLastTenantId: (tenantId) => set({ lastTenantId: tenantId }),
      reset: () => set({ lastTenantId: null }),
    }),
    {
      name: "openubl-ui/last-tenant",
      version: 1,
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ lastTenantId: state.lastTenantId }),
    },
  ),
);

/** Other tabs write the same key; re-read it so a tab doesn't overwrite theirs. */
export const syncLastTenantAcrossTabs = () => {
  window.addEventListener("storage", (event) => {
    if (event.key === "openubl-ui/last-tenant") {
      void useLastTenantStore.persist.rehydrate();
    }
  });
};
