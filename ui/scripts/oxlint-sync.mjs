// Regenerates .oxlintrc.json from eslint.config.mjs (ESLint stays the source of truth).
// @oxlint/migrate does most of the work; this cleans up what it gets wrong for our config.
import { execFileSync } from "node:child_process";
import { readFileSync, rmSync, writeFileSync } from "node:fs";

import eslintConfig from "../eslint.config.mjs";

const OUTPUT = ".oxlintrc.json";
// Workspaces without a `lint` script, which ESLint never sees.
const UNLINTED = ["e2e/**"];
// JS-plugin rules that need type information, which oxlint only gives its
// native rules; ESLint still runs them.
const TYPED_JS_PLUGIN_RULES = ["@eslint-react/no-leaked-conditional-rendering"];

execFileSync(
  "npx",
  ["@oxlint/migrate", "eslint.config.mjs", "--type-aware", "--details"],
  { stdio: "inherit" },
);

const config = JSON.parse(readFileSync(OUTPUT, "utf8"));

const severity = (value) => (Array.isArray(value) ? value[0] : value);
const pluginOf = (rule) =>
  rule.includes("/") ? rule.slice(0, rule.lastIndexOf("/")) : undefined;

// oxlint ignores `ignores` inside overrides; every linted file is .ts/.tsx, so
// hoisting them to the top level is equivalent.
const blockIgnores = eslintConfig.flatMap((block) =>
  block.files && block.ignores ? block.ignores : [],
);
config.ignorePatterns = [
  ...new Set([...(config.ignorePatterns ?? []), ...blockIgnores, ...UNLINTED]),
];

for (const override of config.overrides ?? []) {
  // eslint-config-prettier turns off stylistic rules from plugins we don't
  // install, and prettier/prettier is Prettier's job (`npm run format:fast`).
  override.rules = Object.fromEntries(
    Object.entries(override.rules ?? {}).filter(
      ([rule, value]) =>
        severity(value) !== "off" &&
        pluginOf(rule) !== "prettier" &&
        !TYPED_JS_PLUGIN_RULES.includes(rule),
    ),
  );
  if (override.jsPlugins) {
    const used = new Set(Object.keys(override.rules).map(pluginOf));
    override.jsPlugins = override.jsPlugins.filter((name) =>
      [...used].some((plugin) => plugin && jsPluginMatches(name, plugin)),
    );
    if (override.jsPlugins.length === 0) delete override.jsPlugins;
  }
}

writeFileSync(OUTPUT, `${JSON.stringify(config, null, 2)}\n`);
execFileSync("npx", ["prettier", "--write", OUTPUT], { stdio: "inherit" });
// @oxlint/migrate backs up the file it overwrites; git already does that.
rmSync(`${OUTPUT}.bak`, { force: true });

// "@tanstack/eslint-plugin-query" → "@tanstack/query", "eslint-plugin-x" → "x".
function jsPluginMatches(packageName, rulePrefix) {
  const prefix = packageName
    .replace(/^(@[^/]+)\/eslint-plugin(-(.+))?$/, (_, scope, __, name) =>
      name ? `${scope}/${name}` : scope,
    )
    .replace(/^eslint-plugin-/, "");
  return prefix === rulePrefix;
}
