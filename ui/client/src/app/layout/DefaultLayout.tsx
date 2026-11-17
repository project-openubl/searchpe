import type React from "react";
import { useState } from "react";

import { Page, SkipToContent } from "@patternfly/react-core";

import { useLocalStorage } from "@app/hooks/useStorage";

import { HeaderApp } from "./HeaderApp";
import { SidebarApp } from "./SidebarApp";

interface IDefaultLayoutProps {
  children?: React.ReactNode;
}

/** PatternFly's masthead + sidebar layout: https://www.patternfly.org/components/navigation#expandable */
export const DefaultLayout: React.FC<IDefaultLayoutProps> = ({ children }) => {
  const pageId = "main-content-page-layout-expandable-nav";
  const PageSkipToContent = (
    <SkipToContent href={`#${pageId}`}>Ir al contenido</SkipToContent>
  );

  const [isMobile, setIsMobile] = useState(false);
  // On desktop the sidebar starts open and remembers the user's toggle.
  const [isDesktopSidebarOpen, setIsDesktopSidebarOpen] =
    useLocalStorage<boolean>({
      key: "sidebar-expanded-preference",
      defaultValue: true,
    });
  // On mobile the sidebar overlays the page, so it starts closed and closes after a pick.
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false);

  const isSidebarOpen = isMobile ? isMobileSidebarOpen : isDesktopSidebarOpen;

  const onSidebarToggle = () => {
    if (isMobile) {
      setIsMobileSidebarOpen(!isMobileSidebarOpen);
    } else {
      setIsDesktopSidebarOpen(!isDesktopSidebarOpen);
    }
  };

  const onNavSelect = () => {
    if (isMobile) {
      setIsMobileSidebarOpen(false);
    }
  };

  return (
    <Page
      masthead={
        <HeaderApp
          isSidebarOpen={isSidebarOpen}
          onSidebarToggle={onSidebarToggle}
        />
      }
      sidebar={
        <SidebarApp isSidebarOpen={isSidebarOpen} onNavSelect={onNavSelect} />
      }
      onPageResize={(_event, { mobileView }) => setIsMobile(mobileView)}
      skipToContent={PageSkipToContent}
      mainContainerId={pageId}
    >
      {children}
    </Page>
  );
};
