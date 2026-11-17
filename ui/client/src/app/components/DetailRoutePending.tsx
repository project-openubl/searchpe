import type React from "react";

import { PageSection, Spinner } from "@patternfly/react-core";

interface IDetailRoutePendingProps {
  label: string;
}

/** Shared route `pendingComponent` for detail pages. */
export const DetailRoutePending: React.FC<IDetailRoutePendingProps> = ({
  label,
}) => (
  <PageSection>
    <Spinner aria-label={label} />
  </PageSection>
);
