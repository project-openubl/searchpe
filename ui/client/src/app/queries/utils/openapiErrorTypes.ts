import type { AxiosError } from "axios";

/**
 * Hand-authored error-body types: neither spec documents its error responses, so the generated
 * clients model no error shapes. Used as the `TError` of every query/mutation hook and read by
 * `getMutationErrorMessage` (ADR 0006).
 */

/**
 * xhandler-service's error body for every 4xx/5xx it answers itself: `kind` is a stable code
 * for the UI's logic, `message` is Spanish text for the user, `detail` an optional precision
 * (a SUNAT code, the offending field).
 */
export interface IXhandlerErrorData {
  kind: string;
  message: string;
  detail?: string | null;
}

/** searchpe answers `{ message }` (`ErrorDto`) or, for framework errors, a plain body. */
export interface ISearchpeErrorData {
  message?: string;
}

/** The `TError` of every xhandler-service query and mutation. */
export type XhandlerAxiosError = AxiosError<IXhandlerErrorData>;

/** The `TError` of every searchpe query and mutation. */
export type SearchpeAxiosError = AxiosError<ISearchpeErrorData | string>;

/** Either backend's error, for code shared by both products. */
export type ApiAxiosError = XhandlerAxiosError | SearchpeAxiosError;
