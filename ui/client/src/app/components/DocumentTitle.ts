import type React from "react";
import { useEffect } from "react";

import { getBranding } from "@app/utils/branding";

interface IDocumentTitleProps {
  title?: string | null;
}

/** Sets `document.title` to `"Page · Brand"` while mounted; restores it on unmount. */
export const DocumentTitle: React.FC<IDocumentTitleProps> = ({ title }) => {
  const branding = getBranding();
  const brandTitle = branding?.application?.title || "Openubl";

  useEffect(() => {
    const previous = document.title;
    document.title = title?.trim()
      ? `${title.trim()} · ${brandTitle}`
      : brandTitle;
    return () => {
      document.title = previous;
    };
  }, [title, brandTitle]);

  return null;
};
