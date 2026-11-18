import type React from "react";
import { type Ref, useImperativeHandle, useRef } from "react";

import HCaptcha from "@hcaptcha/react-hcaptcha";
import { Turnstile, type TurnstileInstance } from "@marsidev/react-turnstile";

import type { ICaptchaConfig } from "@openubl-ui/common";

/** What the form can do with the widget: a token is single-use, so it asks for a new one. */
export interface ICaptchaWidgetHandle {
  reset: () => void;
}

interface ICaptchaWidgetProps {
  config: ICaptchaConfig;
  id: string;
  /** A solved challenge's token, for the request's `captcha` field. */
  onVerify: (token: string) => void;
  /** The token expired before it was sent: it no longer verifies. */
  onExpire: () => void;
  /** The widget failed (script blocked, network, challenge error). */
  onError: () => void;
  ref?: Ref<ICaptchaWidgetHandle>;
}

/**
 * The CAPTCHA provider's widget, through its React wrapper. The wrapper adds the provider's
 * script when the widget mounts, so it loads only on the page that shows it.
 */
export const CaptchaWidget: React.FC<ICaptchaWidgetProps> = ({
  config,
  id,
  onVerify,
  onExpire,
  onError,
  ref,
}) => {
  const hcaptchaRef = useRef<HCaptcha>(null);
  const turnstileRef = useRef<TurnstileInstance>(null);

  useImperativeHandle(
    ref,
    () => ({
      reset: () => {
        hcaptchaRef.current?.resetCaptcha();
        turnstileRef.current?.reset();
      },
    }),
    [],
  );

  return config.provider === "hcaptcha" ? (
    <HCaptcha
      ref={hcaptchaRef}
      id={id}
      sitekey={config.siteKey}
      languageOverride="es"
      // The wrapper reports its own errors to hCaptcha's Sentry unless told not to.
      sentry={false}
      onVerify={(token) => onVerify(token)}
      onExpire={onExpire}
      onChalExpired={onExpire}
      onError={() => onError()}
    />
  ) : (
    <Turnstile
      ref={turnstileRef}
      id={id}
      siteKey={config.siteKey}
      // The token goes in the JSON body, not in a hidden form input.
      options={{ language: "es", responseField: false }}
      onSuccess={onVerify}
      onExpire={onExpire}
      onError={() => onError()}
    />
  );
};
