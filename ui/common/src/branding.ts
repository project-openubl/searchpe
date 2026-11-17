export interface IMastheadBrand {
  src: string;
  alt: string;
  height: string;
}

export interface IMastheadTitle {
  text: string;
  /** Render the title as this heading; by default it is styled text, not a heading. */
  heading?: "h1" | "h2" | "h3" | "h4" | "h5" | "h6";
  size?: "md" | "lg" | "xl" | "2xl" | "3xl" | "4xl";
}

export interface IBrandingStrings {
  application: {
    title: string;
    name?: string;
    description?: string;
  };

  about: {
    displayName: string;
    imageSrc?: string;
    documentationUrl?: string;
  };

  masthead: {
    leftBrand?: IMastheadBrand;
    leftTitle?: IMastheadTitle;
    rightBrand?: IMastheadBrand;
    supportUrl?: string;
  };
}

// TS resolves this import to a stub JSON file (via `paths`); the rollup build
// replaces it with the real branding JSON via a virtual plugin.
import stringsStub from "@branding/strings.json";

export const brandingStrings: IBrandingStrings = stringsStub;
