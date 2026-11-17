import type React from "react";

import { Button, type ButtonProps, Tooltip } from "@patternfly/react-core";

interface IGatedButtonProps extends ButtonProps {
  /** Why the button is disabled; the shape `disabledProps` / `lockedProps` spread. */
  tooltipProps?: { content: React.ReactNode };
}

/**
 * A `Button` that takes the same `isAriaDisabled` + `tooltipProps` a menu item does, so
 * permission and lock props spread onto it unchanged.
 */
export const GatedButton: React.FC<IGatedButtonProps> = ({
  tooltipProps,
  ...buttonProps
}) => {
  const button = <Button {...buttonProps} />;
  return buttonProps.isAriaDisabled && tooltipProps ? (
    <Tooltip content={tooltipProps.content}>{button}</Tooltip>
  ) : (
    button
  );
};
