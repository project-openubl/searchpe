import axios, { type AxiosError, type AxiosInstance } from "axios";
import type { User } from "oidc-client-ts";

import {
  SEARCHPE_PROXY_PREFIX,
  XHANDLER_PROXY_PREFIX,
} from "@openubl-ui/common";

import { client as searchpeClient } from "@app/client/searchpe/client.gen";
import { createClient, createConfig } from "@app/client/xhandler/client";
import { client as xhandlerClient } from "@app/client/xhandler/client.gen";
import { currentPageState, userManager } from "@app/oidc";

// Repeat array params (`label=a&label=b`), as both Quarkus backends read them, and drop
// undefined/null values.
const serializeParams = (params: Record<string, unknown>): string => {
  const sp = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    for (const item of Array.isArray(value) ? value : [value]) {
      sp.append(key, String(item));
    }
  }
  return sp.toString();
};

const createApiAxios = (): AxiosInstance =>
  axios.create({
    adapter: "fetch",
    withCredentials: false,
    paramsSerializer: { serialize: serializeParams },
  });

/** Calls to xhandler-service with the user's token. */
export const xhandlerAxios = createApiAxios();

/** Calls to searchpe with the user's token. */
export const searchpeAxios = createApiAxios();

/** Calls to xhandler-service's public endpoints: never carries a token (ADR 0003). */
export const publicAxios = createApiAxios();

/** The generated xhandler client for public routes, on `publicAxios`. */
export const xhandlerPublicClient = createClient(
  createConfig({ axios: publicAxios, baseURL: XHANDLER_PROXY_PREFIX }),
);

type RetriableConfig = NonNullable<AxiosError["config"]> & {
  retryCounter?: number;
};

let silentRenewal: Promise<User | null> | undefined;

/**
 * Renews the token silently; concurrent callers (a page firing several requests that all get a
 * 401) share one renewal instead of each opening Keycloak's iframe.
 */
export const renewTokenSilently = (): Promise<User | null> => {
  silentRenewal ??= userManager.signinSilent().finally(() => {
    silentRenewal = undefined;
  });
  return silentRenewal;
};

let loginRedirect: Promise<void> | undefined;

/** Starts one login redirect back to the current page, however many requests ask for it. */
const redirectToLogin = (): Promise<void> => {
  loginRedirect ??= userManager
    .signinRedirect({ url_state: currentPageState() })
    .catch(() => {
      loginRedirect = undefined;
    });
  return loginRedirect;
};

/**
 * What a backend does when the token can't be renewed: xhandler-service is the session's home,
 * so it sends the user to log in again (never a logout); searchpe just fails its request and its
 * section shows as unavailable, leaving the rest of the app alone.
 */
export type RenewalFailurePolicy = "login" | "reject";

/**
 * Bearer token on every request; a 401 renews the token once (shared by concurrent requests) and
 * retries. A request with no stored user (a page racing the login) is rejected as is: there is
 * no session to renew, and nothing here ever signs the user out.
 */
export const addAuthInterceptors = (
  instance: AxiosInstance,
  { onRenewalFailure }: { onRenewalFailure: RenewalFailurePolicy },
) => {
  instance.interceptors.request.use(async (config) => {
    if (!config.headers.Authorization) {
      const token = (await userManager.getUser())?.access_token;
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const config = error.config as RetriableConfig | undefined;
      if (error.response?.status !== 401 || !config || config.retryCounter) {
        return Promise.reject(error);
      }
      if (!(await userManager.getUser())) {
        return Promise.reject(error);
      }

      const renewed = await renewTokenSilently().catch(() => null);
      if (renewed?.access_token) {
        config.retryCounter = 1;
        config.headers.Authorization = `Bearer ${renewed.access_token}`;
        return instance(config);
      }
      if (onRenewalFailure === "login") {
        void redirectToLogin();
      }
      return Promise.reject(error);
    },
  );
};

export const initInterceptors = () => {
  addAuthInterceptors(xhandlerAxios, { onRenewalFailure: "login" });
  addAuthInterceptors(searchpeAxios, { onRenewalFailure: "reject" });
  xhandlerClient.setConfig({
    axios: xhandlerAxios,
    baseURL: XHANDLER_PROXY_PREFIX,
  });
  searchpeClient.setConfig({
    axios: searchpeAxios,
    baseURL: SEARCHPE_PROXY_PREFIX,
  });
};
