import { randomBytes } from "node:crypto";

import type { RequestHandler } from "express";

/** A fresh CSP nonce for one rendered `index.html` (its inline `window._env` script). */
export const newNonce = (): string => randomBytes(16).toString("base64");

/** The OIDC server's origin (`https://sso.example.com`), or `undefined` for a bad URL. */
export const originOf = (url: string | undefined): string | undefined => {
  try {
    return url ? new URL(url).origin : undefined;
  } catch {
    return undefined;
  }
};

/**
 * The page's Content-Security-Policy. Scripts are the bundle plus the one inline script carrying
 * the nonce; PatternFly and React set `style` attributes, hence `'unsafe-inline'` for styles
 * only. Silent token renewal loads Keycloak in a hidden iframe that comes back to this origin,
 * so frames allow both, and only this origin may frame the app.
 */
export const contentSecurityPolicy = ({
  nonce,
  oidcOrigin,
}: {
  nonce: string;
  oidcOrigin: string | undefined;
}): string => {
  const oidc = oidcOrigin ? ` ${oidcOrigin}` : "";
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}'`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data:",
    "font-src 'self' data:",
    `connect-src 'self'${oidc}`,
    `frame-src 'self'${oidc}`,
    "frame-ancestors 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
  ].join("; ");
};

/**
 * Security headers on every response; the nonce for the rendered page goes to
 * `res.locals.nonce`.
 */
export const securityHeaders =
  ({ oidcOrigin }: { oidcOrigin: string | undefined }): RequestHandler =>
  (_req, res, next) => {
    const nonce = newNonce();
    res.locals.nonce = nonce;
    res.setHeader(
      "Content-Security-Policy",
      contentSecurityPolicy({ nonce, oidcOrigin }),
    );
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("X-Frame-Options", "SAMEORIGIN");
    next();
  };

/**
 * Cache-Control for a file of the client build, by its path relative to the build: hashed
 * `assets/` never change under their name; everything else (favicon, manifest, branding) is
 * revalidated.
 */
export const staticCacheControl = (relativePath: string): string =>
  /^assets[\\/]/.test(relativePath)
    ? "public, max-age=31536000, immutable"
    : "no-cache";
