import type React from "react";

import {
  Masthead,
  MastheadBrand,
  MastheadContent,
  MastheadLogo,
  MastheadMain,
  Page,
  SkipToContent,
  Toolbar,
  ToolbarContent,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";

import { BrandLogo } from "./BrandLogo";
import { LibroReclamacionesLink } from "./LibroReclamacionesLink";

interface IPublicLayoutProps {
  children?: React.ReactNode;
}

const MAIN_CONTAINER_ID = "public-main-content";

/**
 * Shell of the public routes: the brand and the Libro de Reclamaciones only, nothing that needs
 * a session (ADR 0003).
 */
export const PublicLayout: React.FC<IPublicLayoutProps> = ({ children }) => (
  <Page
    skipToContent={
      <SkipToContent href={`#${MAIN_CONTAINER_ID}`}>
        Ir al contenido
      </SkipToContent>
    }
    masthead={
      <Masthead>
        <MastheadMain>
          <MastheadBrand>
            <MastheadLogo>
              <BrandLogo />
            </MastheadLogo>
          </MastheadBrand>
        </MastheadMain>
        <MastheadContent>
          <Toolbar isFullHeight isStatic>
            <ToolbarContent>
              <ToolbarGroup align={{ default: "alignEnd" }}>
                <ToolbarItem>
                  <LibroReclamacionesLink />
                </ToolbarItem>
              </ToolbarGroup>
            </ToolbarContent>
          </Toolbar>
        </MastheadContent>
      </Masthead>
    }
    mainContainerId={MAIN_CONTAINER_ID}
  >
    {children}
  </Page>
);
