import type { ApiAxiosError } from "./openapiErrorTypes";

/**
 * Caller-owned, hook-level mutation callbacks (see `.rulesync/rules/toastr.md`): wired
 * into `useMutation`, so they run even if the caller unmounts mid-request.
 */
export interface IMutationCallbacks<TData, TVariables, TError = ApiAxiosError> {
  /** May return a promise (e.g. `navigate()`); hooks that clean up after the caller await it. */
  onSuccess?: (data: TData, variables: TVariables) => void | Promise<void>;
  onError?: (error: TError, variables: TVariables) => void;
}
