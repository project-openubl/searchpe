/** Query params of a generated `*Data` type, sourced from the spec so list queries stay in sync on `npm run generate`. */
type QueryOf<TData extends { query?: object }> = NonNullable<TData["query"]>;

/**
 * Params for a paged list query: the operation's query params with `limit` required, so no
 * silent server default sizes the page.
 */
export type ListParams<TData extends { query?: object }> = Omit<
  QueryOf<TData>,
  "limit"
> & {
  limit: number;
};
