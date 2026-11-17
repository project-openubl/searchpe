/** Parsed URL params: one string per name, or an array when the name repeats. */
export type SearchRecord = Record<string, string | string[]>;

/**
 * The router's `parseSearch`: every value stays a plain string (no JSON guessing), and a
 * repeated name becomes an array. Schemas convert strings to their own types.
 */
export const parseSearch = (searchStr: string): SearchRecord => {
  const search: SearchRecord = {};
  for (const [key, value] of new URLSearchParams(searchStr)) {
    const existing = search[key];
    search[key] =
      existing === undefined
        ? value
        : [...(Array.isArray(existing) ? existing : [existing]), value];
  }
  return search;
};

const toParamValue = (value: unknown): string | undefined => {
  if (value === undefined || value === null || value === "") return undefined;
  if (typeof value === "object") return JSON.stringify(value);
  return String(value as string | number | boolean | bigint);
};

/**
 * The router's `stringifySearch`: arrays become repeated names, empty values are dropped,
 * and nothing is JSON-encoded unless a caller passes a nested object.
 */
export const stringifySearch = (search: Record<string, unknown>): string => {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(search)) {
    for (const item of Array.isArray(value) ? value : [value]) {
      const paramValue = toParamValue(item);
      if (paramValue !== undefined) params.append(key, paramValue);
    }
  }
  const searchStr = params.toString();
  return searchStr ? `?${searchStr}` : "";
};
