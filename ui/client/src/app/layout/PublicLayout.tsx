import type React from "react";

import {
  Masthead,
  MastheadBrand,
  MastheadLogo,
  MastheadMain,
  Page,
  SkipToContent,
} from "@patternfly/react-core";

import { BrandLogo } from "./BrandLogo";

interface IPublicLayoutProps {
  children?: React.ReactNode;
}

const MAIN_CONTAINER_ID = "public-main-content";

/** Shell of the public routes: the brand only, nothing that needs a session (ADR 0003). */
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
      </Masthead>
    }
    mainContainerId={MAIN_CONTAINER_ID}
  >
    {children}
  </Page>
);
