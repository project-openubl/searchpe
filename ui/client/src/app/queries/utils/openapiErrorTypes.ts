import type { AxiosError } from "axios";

import type { ApiError } from "@app/client/xhandler";

/**
 * Error-body types used as the `TError` of every query/mutation hook and read by
 * `getMutationErrorMessage` (ADR 0006). xhandler-service documents its body (`ApiError`:
 * `kind` for the UI's logic, Spanish `message`, optional `detail`); searchpe's spec documents
 * none, so its body is hand-written here.
 */

/** searchpe answers `{ message }` (`ErrorDto`) or, for framework errors, a plain body. */
export interface ISearchpeErrorData {
  message?: string;
}

/** The `TError` of every xhandler-service query and mutation. */
export type XhandlerAxiosError = AxiosError<ApiError>;

/** The `TError` of every searchpe query and mutation. */
export type SearchpeAxiosError = AxiosError<ISearchpeErrorData | string>;

/** Either backend's error, for code shared by both products. */
export type ApiAxiosError = XhandlerAxiosError | SearchpeAxiosError;
