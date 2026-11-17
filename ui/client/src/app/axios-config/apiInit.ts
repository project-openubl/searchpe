import axios, { type AxiosError, type AxiosInstance } from "axios";
import { User, UserManager } from "oidc-client-ts";

import {
  SEARCHPE_PROXY_PREFIX,
  XHANDLER_PROXY_PREFIX,
} from "@openubl-ui/common";

import { client as searchpeClient } from "@app/client/searchpe/client.gen";
import { createClient, createConfig } from "@app/client/xhandler/client";
import { client as xhandlerClient } from "@app/client/xhandler/client.gen";
import {
  OIDC_CLIENT_ID,
  OIDC_SERVER_URL,
  oidcClientSettings,
  oidcSignoutArgs,
} from "@app/oidc";

const getOidcUser = () => {
  const oidcStorage = sessionStorage.getItem(
    `oidc.user:${OIDC_SERVER_URL}:${OIDC_CLIENT_ID}`,
  );
  if (!oidcStorage) {
    return null;
  }
  return User.fromStorageString(oidcStorage);
};

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

const addBearerToken = (instance: AxiosInstance) => {
  instance.interceptors.request.use((config) => {
    if (!config.headers.Authorization) {
      const token = getOidcUser()?.access_token;
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  });

  // A 401 renews the token silently and retries the request once; if renewal fails the
  // session is over, so sign out.
  instance.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const config = error.config as RetriableConfig | undefined;
      if (error.response?.status === 401 && config && !config.retryCounter) {
        const userManager = new UserManager(oidcClientSettings);
        try {
          const refreshedUser = await userManager.signinSilent();
          const accessToken = refreshedUser?.access_token;
          if (accessToken) {
            config.headers.Authorization = `Bearer ${accessToken}`;
            config.retryCounter = 1;
            return instance(config);
          }
        } catch {
          await userManager.signoutRedirect(oidcSignoutArgs);
        }
      }
      return Promise.reject(error);
    },
  );
};

export const initInterceptors = () => {
  addBearerToken(xhandlerAxios);
  addBearerToken(searchpeAxios);
  xhandlerClient.setConfig({
    axios: xhandlerAxios,
    baseURL: XHANDLER_PROXY_PREFIX,
  });
  searchpeClient.setConfig({
    axios: searchpeAxios,
    baseURL: SEARCHPE_PROXY_PREFIX,
  });
};
