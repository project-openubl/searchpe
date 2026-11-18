import type React from "react";

import {
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { PersistentStateScopeProvider } from "@app/context/PersistentStateScope/PersistentStateScopeProvider";

import { EmailSuppressionTable } from "./components/EmailSuppressionTable";
import { SenderDomainTable } from "./components/SenderDomainTable";

/** The tenant's email setup: sender domains and the addresses mail no longer goes to. */
export const EmailSettings: React.FC = () => (
  <>
    <DocumentTitle title="Correo" />
    <TenantContextSelector />
    <PageSection>
      <Content component={ContentVariants.h1}>Correo</Content>
      <Content component={ContentVariants.p}>
        Los dominios con los que se envían los comprobantes por correo y las
        direcciones a las que ya no se envía.
      </Content>
    </PageSection>
    <PageSection>
      <Stack hasGutter>
        <StackItem>
          <Card>
            <CardTitle>Dominios remitentes</CardTitle>
            <CardBody>
              {/* Two lists on one screen: each keeps its page under its own prefix. */}
              <PersistentStateScopeProvider prefix="dominios">
                <SenderDomainTable />
              </PersistentStateScopeProvider>
            </CardBody>
          </Card>
        </StackItem>
        <StackItem>
          <Card>
            <CardTitle>Direcciones suprimidas</CardTitle>
            <CardBody>
              <PersistentStateScopeProvider prefix="supresiones">
                <EmailSuppressionTable />
              </PersistentStateScopeProvider>
            </CardBody>
          </Card>
        </StackItem>
      </Stack>
    </PageSection>
  </>
);
