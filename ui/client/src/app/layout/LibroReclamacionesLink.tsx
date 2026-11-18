import type React from "react";

import { Button } from "@patternfly/react-core";
import ExternalLinkAltIcon from "@patternfly/react-icons/dist/esm/icons/external-link-alt-icon";

import { ENV } from "@app/env";
import { isHttpsUrl } from "@app/utils/url";

/**
 * The platform's own virtual Libro de Reclamaciones, permanently visible in every shell
 * (Ley 32495). Nothing renders while `LIBRO_RECLAMACIONES_URL` is unset or not https.
 */
export const LibroReclamacionesLink: React.FC = () => {
  const url = ENV.LIBRO_RECLAMACIONES_URL?.trim() ?? "";
  if (!isHttpsUrl(url)) {
    return null;
  }
  return (
    <Button
      component="a"
      variant="link"
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      icon={<ExternalLinkAltIcon />}
      iconPosition="end"
    >
      Libro de Reclamaciones
    </Button>
  );
};
