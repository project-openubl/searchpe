import { buildOpenublEnv, decodeEnv } from "@openubl-ui/common";

export const ENV = buildOpenublEnv(decodeEnv(window._env));
