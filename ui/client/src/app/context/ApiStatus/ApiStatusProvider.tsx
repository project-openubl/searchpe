import type React from "react";
import { useMemo } from "react";

import { ENV } from "@app/env";
import { useAuth } from "@app/hooks/useAuth";
import { useSearchpeHealthQuery } from "@app/queries/searchpe/health";
import { useWhoamiQuery } from "@app/queries/searchpe/whoami";
import { useXhandlerStatusQuery } from "@app/queries/xhandler/status";
import { isHttpError } from "@app/utils/isHttpError";

import type { IApiStatusContext } from "./ApiStatusContext";
import { ApiStatusContext } from "./ApiStatusContext";

interface IApiStatusProviderProps {
  children: React.ReactNode;
}

/**
 * Polls both backends' status; neither blocks the shell, each gates its own section. searchpe
 * also reads as unavailable when it keeps refusing the user's token (a 401 after the renewal),
 * so the rest of the app goes on without it.
 */
export const ApiStatusProvider: React.FC<IApiStatusProviderProps> = ({
  children,
}) => {
  const xhandler = useXhandlerStatusQuery();
  const isSearchpeEnabled = ENV.SEARCHPE_ENABLED === "true";
  const searchpe = useSearchpeHealthQuery({
    options: { enabled: isSearchpeEnabled },
  });
  const auth = useAuth();
  const whoami = useWhoamiQuery({
    enabled: auth.isAuthenticated() && searchpe.isSuccess,
  });
  const isSearchpeRefusingToken = isHttpError({
    error: whoami.error,
    status: 401,
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
        isAvailable:
          searchpe.isSuccess &&
          searchpe.data?.status === "UP" &&
          !isSearchpeRefusingToken,
        error: isSearchpeRefusingToken ? whoami.error : searchpe.error,
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
      isSearchpeRefusingToken,
      whoami.error,
    ],
  );

  return <ApiStatusContext value={value}>{children}</ApiStatusContext>;
};
