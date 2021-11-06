export type SEARCHPE_AUTH_METHOD = "oidc" | "basic";

interface Settings {
  defaultAuthMethod: SEARCHPE_AUTH_METHOD;
  formCookieName: string;
  oidcLogoutPath: string;
  isElasticsearchEnabled: boolean;
}

const defaultSettings: Settings = {
  defaultAuthMethod: "basic",
  formCookieName: "searchpe-credential",
  oidcLogoutPath: "/logout",
  isElasticsearchEnabled: false,
};

const SEARCHPE_SETTINGS: Settings =
  (window as any)["SEARCHPE_SETTINGS"] || defaultSettings;

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
