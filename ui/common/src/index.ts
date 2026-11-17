import { CLIENT_ENV_KEYS } from "./environment.js";

export * from "./environment.js";
export * from "./branding.js";
export * from "./proxyPath.js";

/** Return a base64-encoded JSON string of the `include`d keys of `env`, and nothing else. */
export const encodeEnv = (
  env: object,
  include: readonly string[] = CLIENT_ENV_KEYS,
): string =>
  btoa(
    JSON.stringify(
      Object.fromEntries(
        Object.entries(env).filter(([key]) => include.includes(key)),
      ),
    ),
  );

/** Return an object from a base64-encoded JSON string. */
export const decodeEnv = (env: string): object =>
  !env ? {} : JSON.parse(atob(env));
