import type React from "react";

import {
  Content,
  ContentVariants,
  EmptyState,
  EmptyStateBody,
  PageSection,
} from "@patternfly/react-core";
import WrenchIcon from "@patternfly/react-icons/dist/esm/icons/wrench-icon";

import { DocumentTitle } from "@app/components/DocumentTitle";

/**
 * The OSE validity page (RS 242-2018, DESIGN.md §12): anyone checks a document's status
 * without a session. U0 placeholder; the form (with CAPTCHA) lands with U4.
 */
export const ConsultaValidez: React.FC = () => (
  <>
    <DocumentTitle title="Consulta de validez" />
    <PageSection>
      <Content component={ContentVariants.h1}>
        Consulta de validez de comprobantes
      </Content>
      <Content component={ContentVariants.p}>
        Verifica si un comprobante electrónico recibido por el OSE es válido.
      </Content>
    </PageSection>
    <PageSection>
      <EmptyState titleText="Próximamente" headingLevel="h2" icon={WrenchIcon}>
        <EmptyStateBody>
          Esta consulta estará disponible pronto. No necesitas una cuenta para
          usarla.
        </EmptyStateBody>
      </EmptyState>
    </PageSection>
  </>
);
