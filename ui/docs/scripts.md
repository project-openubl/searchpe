# npm scripts

Run from `ui/`. Workspace scripts run with `-w <workspace>` or `-ws`.

## Setup and generation

| Script                    | What it does                                                                  |
| ------------------------- | ----------------------------------------------------------------------------- |
| `npm ci`                  | Installs, then `prepare` runs `generate`                                      |
| `npm run generate`        | Generates both API clients from `client/openapi/*.json` (hey-api)             |
| `npm run openapi:refresh` | Copies the specs from the Maven builds into `client/openapi/` and regenerates |
| `npm run rules`           | Regenerates `.claude/` and `.cursor/` from `.rulesync/`                       |
| `npm run oxlint:sync`     | Regenerates `.oxlintrc.json` from `eslint.config.mjs`                         |
| `npm run hooks:install`   | Points the repository's git hooks at `ui/.husky` (lint-staged + commitlint)   |

`openapi:refresh` needs the backends built first, from the repository root:

```bash
(cd xhandler && ./mvnw -q -pl app package -DskipTests)   # xhandler/app/target/openapi/openapi.json
./mvnw -q -pl searchpe package -DskipTests \
  -Dquarkus.smallrye-openapi.store-schema-directory=target/openapi   # searchpe/target/openapi/openapi.json
```

## Develop and run

| Script              | What it does                                                      |
| ------------------- | ----------------------------------------------------------------- |
| `npm run start:dev` | Builds `common`, watches it and runs the Vite dev server on :3000 |
| `npm run start`     | Production build of `common` + `client`, then the Express server  |
| `npm run build`     | Builds every workspace                                            |
| `npm run dist`      | Collects the built workspaces into `dist/` (the image's content)  |

## Check

| Script                | What it does                                                           |
| --------------------- | ---------------------------------------------------------------------- |
| `npm run lint`        | `rules:check`, then ESLint + `tsc` in every workspace (the gate)       |
| `npm run format`      | Prettier check (the gate); `format:fix` writes                         |
| `npm run lint:fast`   | oxlint, the fast inner loop; `lint:fast:fix` fixes                     |
| `npm run format:fast` | oxfmt check; `format:fast:fix` writes (run `format:fix` after it)      |
| `npm test`            | Vitest Browser Mode (client)                                           |
| `npm run test:e2e`    | Playwright against a running stack ([e2e/README.md](../e2e/README.md)) |
