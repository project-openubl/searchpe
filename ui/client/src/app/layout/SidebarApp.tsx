import type React from "react";

import { PageSidebar, PageSidebarBody } from "@patternfly/react-core";

import { NavApp } from "./NavApp";

interface ISidebarAppProps {
  isSidebarOpen: boolean;
  onNavSelect: () => void;
}

export const SidebarApp: React.FC<ISidebarAppProps> = ({
  isSidebarOpen,
  onNavSelect,
}) => (
  // A closed sidebar is only aria-hidden; inert also keeps its links out of the tab order.
  <PageSidebar isSidebarOpen={isSidebarOpen} inert={!isSidebarOpen}>
    <PageSidebarBody>
      <NavApp onSelect={onNavSelect} />
    </PageSidebarBody>
  </PageSidebar>
);
