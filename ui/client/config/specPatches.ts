/**
 * In-place patches applied to the committed OpenAPI specs before generation (hey-api's
 * `parser.patch.input`). The committed files stay byte-identical to the Maven builds; every
 * deviation is listed here with its reason (docs/internals/api-clients.md).
 */

interface ISpec {
  paths?: Record<string, unknown>;
  servers?: unknown;
}

/**
 * Keeps the paths under `/api/` whose remainder passes `keep`, without the `/api` prefix: the
 * generated client's `baseURL` is the UI's proxy prefix (`/api/xhandler`), which the proxies
 * rewrite back to `/api`.
 */
const rebasePaths = ({
  spec,
  keep,
}: {
  spec: ISpec;
  keep: (path: string) => boolean;
}): void => {
  spec.paths = Object.fromEntries(
    Object.entries(spec.paths ?? {}).flatMap(([path, item]) => {
      if (!path.startsWith("/api/")) return [];
      const rebased = path.slice("/api".length);
      return keep(rebased) ? [[rebased, item]] : [];
    }),
  );
  // The servers SmallRye writes are the build host's; requests go through the proxy.
  delete spec.servers;
};

/** xhandler-service: keep `/api/v1/*` (the SOAP endpoints aren't for the UI). */
export const patchXhandlerSpec = (spec: ISpec): void => {
  rebasePaths({ spec, keep: (path) => path.startsWith("/v1/") });
};

/** Endpoints of searchpe the UI never calls: basic-auth user management. */
const SEARCHPE_DROPPED_PATHS = [/^\/admin\//, /^\/current-user\//];

/** searchpe: keep its REST API (camelCase JSON-B, documented as served) minus the dropped endpoints. */
export const patchSearchpeSpec = (spec: ISpec): void => {
  rebasePaths({
    spec,
    keep: (path) => !SEARCHPE_DROPPED_PATHS.some((pattern) => pattern.test(path)),
  });
};
