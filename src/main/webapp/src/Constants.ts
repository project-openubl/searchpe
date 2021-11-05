export type SEARCHPE_AUTH_METHOD = "oidc" | "basic";

export const getAuthMethod = (): SEARCHPE_AUTH_METHOD => {
  return (window as any)["SEARCHPE_DEFAULT_AUTH_METHOD"];
};

export const isElasticsearchEnabled = (): boolean => {
  return (window as any)["SEARCHPE_IS_ELASTICSEARCH_ENABLED"];
};
