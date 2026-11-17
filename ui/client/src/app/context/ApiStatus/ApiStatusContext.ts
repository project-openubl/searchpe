import { createContext } from "react";

import type { Status } from "@app/client/xhandler";
import type { ISearchpeHealth } from "@app/queries/searchpe/health";

/** One backend's availability, from its public status endpoint (ADR 0001). */
export interface IBackendStatus<T> {
  /** Deployed with this UI (searchpe is optional); a disabled backend has no section at all. */
  isEnabled: boolean;
  data: T | undefined;
  isLoading: boolean;
  /** Answered successfully: its navigation shows. */
  isAvailable: boolean;
  error: Error | null;
}

export interface IApiStatusContext {
  xhandler: IBackendStatus<Status>;
  searchpe: IBackendStatus<ISearchpeHealth>;
}

export const ApiStatusContext = createContext<IApiStatusContext | null>(null);
