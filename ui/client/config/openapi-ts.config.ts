import { defineConfig } from "@hey-api/openapi-ts";

import { patchSearchpeSpec, patchXhandlerSpec } from "./specPatches";

// One generated client per backend; specs are copied from the Maven builds by
// `npm run openapi:refresh` and patched in memory (./specPatches.ts).
export default defineConfig([
  {
    input: "./openapi/xhandler.json",
    output: { path: "src/app/client/xhandler" },
    parser: {
      patch: {
        input: (spec) => patchXhandlerSpec(spec as Parameters<typeof patchXhandlerSpec>[0]),
      },
    },
    plugins: ["@hey-api/client-axios"],
  },
  {
    input: "./openapi/searchpe.json",
    output: { path: "src/app/client/searchpe" },
    parser: {
      patch: {
        input: (spec) => patchSearchpeSpec(spec as Parameters<typeof patchSearchpeSpec>[0]),
      },
    },
    plugins: ["@hey-api/client-axios"],
  },
]);
