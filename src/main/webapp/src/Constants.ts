export enum Permission {
  admin = "admin:app",
  search = "search",
  version_write = "version:write",
  user_write = "user:write",
}
export const ALL_PERMISSIONS: Permission[] = [
  Permission.admin,
  Permission.search,
  Permission.version_write,
  Permission.user_write,
];

export type SEARCHPE_AUTH_METHOD = "none" | "oidc" | "basic";

interface Settings {
  defaultAuthMethod: SEARCHPE_AUTH_METHOD;
  formCookieName: string;
  oidcLogoutPath: string;
  isAdvancedSearchEnabled: boolean;
  applicationVersion: string;
}

const defaultSettings: Settings = {
  defaultAuthMethod: "basic",
  formCookieName: "searchpe-credential",
  oidcLogoutPath: "/logout",
  isAdvancedSearchEnabled: false,
  applicationVersion: "",
};

const SEARCHPE_SETTINGS: Settings =
  (window as any)["SEARCHPE_SETTINGS"] || defaultSettings;

export const isAuthDisabled = (): boolean => {
  return SEARCHPE_SETTINGS.defaultAuthMethod === "none";
};

export const isBasicDefaultAuth = (): boolean => {
  return SEARCHPE_SETTINGS.defaultAuthMethod === "basic";
};

export const isOidcDefaultAuth = (): boolean => {
  return SEARCHPE_SETTINGS.defaultAuthMethod === "oidc";
};

export const getAuthFormCookieName = (): string => {
  return SEARCHPE_SETTINGS.formCookieName;
};

export const getOidcLogoutPath = (): string => {
  return SEARCHPE_SETTINGS.oidcLogoutPath;
};

export const getApplicationVersion = () => {
  return SEARCHPE_SETTINGS.applicationVersion;
};

export const isAdvancedSearchEnabled = () => {
  return SEARCHPE_SETTINGS.isAdvancedSearchEnabled;
};
