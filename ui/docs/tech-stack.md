# Tech stack

The Openubl UI is a TypeScript monorepo of four npm workspaces (`common`, `client`, `server`,
`e2e`), on Node 22, following [pulp-ui-2](https://github.com/pulp/pulp-ui-2).
The main pieces, by area:

## UI

| Technology                                | Version | Role                                |
| ----------------------------------------- | ------- | ----------------------------------- |
| [React](https://react.dev/)               | 19      | UI library                          |
| [PatternFly](https://www.patternfly.org/) | 6       | Component library and design system |
| PatternFly Data view, Component groups    | 6       | Data tables, list views, states     |
| [dayjs](https://day.js.org/)              | 1       | Date formatting (Spanish locale)    |

## Data & routing

| Technology                                                                              | Role                                   |
| --------------------------------------------------------------------------------------- | -------------------------------------- |
| [TanStack Query](https://tanstack.com/query)                                            | Server-state caching and data fetching |
| [Zustand](https://zustand.docs.pmnd.rs/)                                                | Client state that outlives a page      |
| [TanStack Router](https://tanstack.com/router)                                          | File-based, code-split routing         |
| [Axios](https://axios-http.com/)                                                        | HTTP client                            |
| [react-hook-form](https://react-hook-form.com/) + [yup](https://github.com/jquense/yup) | Forms and their validation             |
| [zod](https://zod.dev/)                                                                 | URL and storage state validation       |
| [react-oidc-context](https://github.com/authts/react-oidc-context) + oidc-client-ts     | OIDC authentication                    |

## Build tooling

| Technology                                    | Role                                       |
| --------------------------------------------- | ------------------------------------------ |
| [TypeScript](https://www.typescriptlang.org/) | Language                                   |
| [Vite](https://vite.dev/)                     | Client dev server and bundler              |
| [Rollup](https://rollupjs.org/)               | Builds for `common` and `server`           |
| [Express](https://expressjs.com/)             | Production server (static serving + proxy) |

No i18n library: the UI is Spanish only ([ADR 0005](adr/0005-spanish-ui.md)).

## Code generation

| Technology                                         | Role                                           |
| -------------------------------------------------- | ---------------------------------------------- |
| [@hey-api/openapi-ts](https://heyapi.dev/) 0.99    | Generates the two typed API clients (axios)    |
| [rulesync](https://github.com/dyoshikawa/rulesync) | Generates agent rules/skills from `.rulesync/` |

## Testing

| Technology                            | Role                      |
| ------------------------------------- | ------------------------- |
| [Vitest](https://vitest.dev/)         | Unit tests (browser mode) |
| [Playwright](https://playwright.dev/) | End-to-end tests          |

## Lint & format

| Technology                                                            | Role                                    |
| --------------------------------------------------------------------- | --------------------------------------- |
| [ESLint](https://eslint.org/) + [Prettier](https://prettier.io/)      | The gate (CI, pre-commit)               |
| [oxlint](https://oxc.rs/) + oxfmt                                     | Fast lint/format for the inner dev loop |
| [Husky](https://typicode.github.io/husky/) + lint-staged + commitlint | Git hooks and commit linting            |
