export type SEARCHPE_AUTH_METHOD = "oidc" | "basic";

const SEARCHPE_SETTINGS = (window as any)["SEARCHPE_SETTINGS"];
export const getAuthMethod = (): SEARCHPE_AUTH_METHOD => {
  return SEARCHPE_SETTINGS.defaultAuthMethod;
};

export const getAuthFormCookieName = (): string => {
  return SEARCHPE_SETTINGS.formCookieName;
};
export const getOidcLogoutPath = (): string => {
  return SEARCHPE_SETTINGS.oidcLogoutPath;
};

export const isElasticsearchEnabled = (): boolean => {
  return SEARCHPE_SETTINGS.isElasticsearchEnabled;
};
