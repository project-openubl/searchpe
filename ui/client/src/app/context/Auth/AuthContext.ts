import { createContext } from "react";

export interface IUser {
  username: string;
}

export interface IAuthContext {
  user: IUser | null;
  isAuthenticated: () => boolean;
  isLoading: boolean;
  /** The OIDC setup failed (unreachable or misconfigured Keycloak); public routes ignore it. */
  error: Error | null;
  /** Start the OIDC login redirect, coming back to `redirectTo`. */
  login: (redirectTo?: string) => void;
  logout: () => void;
}

export const AuthContext = createContext<IAuthContext | null>(null);
