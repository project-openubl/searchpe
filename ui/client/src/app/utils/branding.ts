import type { IBrandingStrings } from "@openubl-ui/common";
import { brandingStrings } from "@openubl-ui/common";

/** Indirection so consumers keep a stable API if branding resolution becomes dynamic. */
export const getBranding = (): IBrandingStrings => brandingStrings;
