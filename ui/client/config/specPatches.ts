/**
 * In-place patches applied to the committed OpenAPI specs before generation (hey-api's
 * `parser.patch.input`). The committed files stay byte-identical to the Maven builds; every
 * deviation is listed here with its reason (docs/internals/api-clients.md).
 */

type JsonObject = Record<string, unknown>;

interface ISpec {
  paths?: Record<string, unknown>;
  components?: { schemas?: Record<string, unknown> };
  servers?: unknown;
}

const isObject = (value: unknown): value is JsonObject =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const toSnakeCase = (name: string): string =>
  name
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .replace(/([A-Z])([A-Z][a-z])/g, "$1_$2")
    .toLowerCase();

/** Renames every object property in a schema tree to snake_case (keeps `required` in step). */
const snakeCaseSchema = (schema: unknown): void => {
  if (Array.isArray(schema)) {
    schema.forEach(snakeCaseSchema);
    return;
  }
  if (!isObject(schema)) return;
  if (isObject(schema.properties)) {
    schema.properties = Object.fromEntries(
      Object.entries(schema.properties).map(([name, value]) => {
        snakeCaseSchema(value);
        return [toSnakeCase(name), value];
      }),
    );
    if (Array.isArray(schema.required)) {
      schema.required = schema.required.map((name: string) =>
        toSnakeCase(name),
      );
    }
  }
  for (const key of ["items", "additionalProperties", "not"]) {
    snakeCaseSchema(schema[key]);
  }
  for (const key of ["allOf", "oneOf", "anyOf"]) {
    snakeCaseSchema(schema[key]);
  }
};

/** Every inline schema under the paths (request bodies, responses). */
const snakeCaseInlineSchemas = (node: unknown): void => {
  if (Array.isArray(node)) {
    node.forEach(snakeCaseInlineSchemas);
    return;
  }
  if (!isObject(node)) return;
  for (const [key, value] of Object.entries(node)) {
    if (key === "schema") snakeCaseSchema(value);
    else snakeCaseInlineSchemas(value);
  }
};

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

/**
 * xhandler-service: keep `/api/v1/*` (the SOAP endpoints aren't for the UI), and turn the
 * property names to snake_case. The service serializes JSON with Jackson's SNAKE_CASE strategy
 * but SmallRye documents the Java names (camelCase); the second step is a no-op once the spec
 * is fixed upstream.
 */
export const patchXhandlerSpec = (spec: ISpec): void => {
  rebasePaths({ spec, keep: (path) => path.startsWith("/v1/") });
  for (const schema of Object.values(spec.components?.schemas ?? {})) {
    snakeCaseSchema(schema);
  }
  snakeCaseInlineSchemas(spec.paths);
};

/** Endpoints of searchpe the UI never calls: basic-auth user management and the old UI's settings. */
const SEARCHPE_DROPPED_PATHS = [
  /^\/admin\//,
  /^\/current-user\//,
  /^\/templates\//,
];

/** searchpe: keep its REST API (camelCase JSON-B, documented as served) minus the dropped endpoints. */
export const patchSearchpeSpec = (spec: ISpec): void => {
  rebasePaths({
    spec,
    keep: (path) => !SEARCHPE_DROPPED_PATHS.some((pattern) => pattern.test(path)),
  });
};
