import { useMutation } from "@tanstack/react-query";

import { xhandlerPublicClient } from "@app/axios-config/apiInit";
import {
  lookupOseDocument,
  type OseLookupRequest,
  type OseLookupResult,
} from "@app/client/xhandler";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

/** The OSE validity answers (`OseLookupResult.state`); the spec types it as a plain string. */
export const OSE_LOOKUP_STATES = [
  "con_cdr",
  "con_inconsistencia",
  "no_encontrado",
] as const;

export type OseLookupState = (typeof OSE_LOOKUP_STATES)[number];

export const isOseLookupState = (value: string): value is OseLookupState =>
  (OSE_LOOKUP_STATES as readonly string[]).includes(value);

/**
 * `POST /api/v1/public/lookup/ose` through the token-less client (ADR 0003): RS 242-2018's
 * validity check, status only. A POST with a single-use CAPTCHA, hence a mutation; nothing is
 * cached.
 */
export const useOseLookupMutation = (
  options?: IMutationCallbacks<
    OseLookupResult,
    OseLookupRequest,
    XhandlerAxiosError
  >,
) =>
  useMutation<OseLookupResult, XhandlerAxiosError, OseLookupRequest>({
    mutationFn: async (body) =>
      (
        await lookupOseDocument({
          client: xhandlerPublicClient,
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, body) => {
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
