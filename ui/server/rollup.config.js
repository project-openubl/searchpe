import commonjs from "@rollup/plugin-commonjs";
import json from "@rollup/plugin-json";
import nodeResolve from "@rollup/plugin-node-resolve";
import run from "@rollup/plugin-run";
import typescript from "@rollup/plugin-typescript";

const buildAndRun = process.env?.ROLLUP_RUN === "true";

/** @type {import('rollup').RollupOptions} */
export default {
  strictDeprecations: true,

  input: "src/index.ts",
  output: {
    file: "dist/index.js",
    format: "esm",
    sourcemap: true,
  },
  watch: {
    clearScreen: false,
  },

  plugins: [
    typescript({ compilerOptions: { rootDir: "src", outDir: "dist" } }),
    nodeResolve({
      preferBuiltins: true,
    }),
    commonjs(),
    json(),
    buildAndRun &&
      run({
        execArgv: ["--enable-source-maps"],
      }),
  ],
};
