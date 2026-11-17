import type React from "react";

import { Bullseye } from "@patternfly/react-core";
import PathMissingIcon from "@patternfly/react-icons/dist/esm/icons/path-missing-icon";

import { FullPageEmptyState } from "@app/components/FullPageEmptyState";

export const NotFound: React.FC = () => {
  return (
    <Bullseye>
      <FullPageEmptyState
        titleText="404: La página no existe"
        icon={PathMissingIcon}
        body="Revisa la dirección o vuelve al inicio."
      />
    </Bullseye>
  );
};
