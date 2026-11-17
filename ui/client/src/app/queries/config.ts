import { QueryClient } from "@tanstack/react-query";
import axios from "axios";

const MAX_RETRIES = 3;

/** Gateway answers that a retry can fix; any other status won't change. */
const TRANSIENT_STATUSES = new Set([502, 503, 504]);

/**
 * Retry network errors (no response) and gateway hiccups; show anything else at once,
 * including a bug thrown inside a `queryFn` (not an HTTP error at all).
 */
const shouldRetry = (failureCount: number, error: unknown): boolean => {
  if (!axios.isAxiosError(error)) return false;
  const status = error.response?.status;
  const transient = status === undefined || TRANSIENT_STATUSES.has(status);
  return transient && failureCount < MAX_RETRIES;
};

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      staleTime: 10000,
      retry: shouldRetry,
    },
  },
});
