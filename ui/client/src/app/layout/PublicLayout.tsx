import type React from "react";

import {
  Masthead,
  MastheadBrand,
  MastheadLogo,
  MastheadMain,
  Page,
} from "@patternfly/react-core";

import { BrandLogo } from "./BrandLogo";

interface IPublicLayoutProps {
  children?: React.ReactNode;
}

/** Shell of the public routes: the brand only, nothing that needs a session (ADR 0003). */
export const PublicLayout: React.FC<IPublicLayoutProps> = ({ children }) => (
  <Page
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
    mainContainerId="public-main-content"
  >
    {children}
  </Page>
);
