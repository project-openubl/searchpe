import type React from "react";
import { useMemo } from "react";

import { ENV } from "@app/env";
import { useSearchpeHealthQuery } from "@app/queries/searchpe/health";
import { useXhandlerStatusQuery } from "@app/queries/xhandler/status";

import type { IApiStatusContext } from "./ApiStatusContext";
import { ApiStatusContext } from "./ApiStatusContext";

interface IApiStatusProviderProps {
  children: React.ReactNode;
}

/** Polls both backends' status; neither blocks the shell, each gates its own section. */
export const ApiStatusProvider: React.FC<IApiStatusProviderProps> = ({
  children,
}) => {
  const xhandler = useXhandlerStatusQuery();
  const isSearchpeEnabled = ENV.SEARCHPE_ENABLED === "true";
  const searchpe = useSearchpeHealthQuery({
    options: { enabled: isSearchpeEnabled },
  });

  const value = useMemo<IApiStatusContext>(
    () => ({
      xhandler: {
        isEnabled: true,
        data: xhandler.data,
        isLoading: xhandler.isLoading,
        isAvailable: xhandler.isSuccess,
        error: xhandler.error,
      },
      searchpe: {
        isEnabled: isSearchpeEnabled,
        data: searchpe.data,
        isLoading: isSearchpeEnabled && searchpe.isLoading,
        isAvailable: searchpe.isSuccess && searchpe.data?.status === "UP",
        error: searchpe.error,
      },
    }),
    [
      xhandler.data,
      xhandler.isLoading,
      xhandler.isSuccess,
      xhandler.error,
      searchpe.data,
      searchpe.isLoading,
      searchpe.isSuccess,
      searchpe.error,
      isSearchpeEnabled,
    ],
  );

  return <ApiStatusContext value={value}>{children}</ApiStatusContext>;
};
