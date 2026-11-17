import type React from "react";

import { AboutModal, Content, ContentVariants } from "@patternfly/react-core";
import spacing from "@patternfly/react-styles/css/utilities/Spacing/spacing";

import { ENV } from "@app/env";
import { useApiStatus } from "@app/hooks/useApiStatus";
import { getBranding } from "@app/utils/branding";

interface IButtonAboutAppProps {
  isOpen: boolean;
  onClose: () => void;
}

const TRANSPARENT_1x1_GIF =
  "data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw== ";

const COPYRIGHT_YEAR = new Date().getFullYear();

export const AboutApp: React.FC<IButtonAboutAppProps> = ({
  isOpen,
  onClose,
}) => {
  const { about } = getBranding();
  const versions = useApiStatus().xhandler.data?.versions;

  return (
    <AboutModal
      isOpen={isOpen}
      onClose={onClose}
      productName={about.displayName}
      brandImageAlt="Logo"
      closeButtonAriaLabel="Cerrar"
      brandImageSrc={about.imageSrc ?? TRANSPARENT_1x1_GIF}
      trademark={`COPYRIGHT © 2026, ${COPYRIGHT_YEAR}`}
    >
      <Content>
        <Content component={ContentVariants.p}>
          {about.displayName} reúne la emisión de comprobantes electrónicos
          SUNAT (xhandler) y la consulta del padrón RUC (searchpe).
        </Content>

        {about.documentationUrl ? (
          <Content component={ContentVariants.p}>
            Para más información consulta la{" "}
            <Content
              component={ContentVariants.a}
              href={about.documentationUrl}
              target="_blank"
            >
              documentación de {about.displayName}
            </Content>
          </Content>
        ) : null}
      </Content>
      <Content className={spacing.pyXl}>
        <Content>
          <Content component="dl">
            <Content component="dt">Versión de la UI</Content>
            <Content component="dd">{ENV.VERSION}</Content>
            {versions?.service ? (
              <>
                <Content component="dt">xhandler-service</Content>
                <Content component="dd">{versions.service}</Content>
              </>
            ) : null}
            {versions?.engine ? (
              <>
                <Content component="dt">Motor (xhandler-rust)</Content>
                <Content component="dd">
                  {versions.engine}
                  {versions.xhandler_rust ? ` (${versions.xhandler_rust})` : ""}
                </Content>
              </>
            ) : null}
          </Content>
        </Content>
      </Content>
    </AboutModal>
  );
};
