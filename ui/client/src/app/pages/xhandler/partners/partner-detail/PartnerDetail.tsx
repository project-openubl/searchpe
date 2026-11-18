import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";

import {
  Breadcrumb,
  BreadcrumbItem,
  Card,
  CardBody,
  Content,
  ContentVariants,
  PageSection,
  Tab,
  Tabs,
  TabTitleText,
} from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  isPartnerRole,
  PARTNER_ROLE_LABELS,
  useMyPartnersQuery,
  useSetPartnerBrandingMutation,
} from "@app/queries/xhandler/partners";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { PartnerBrandingForm } from "./components/PartnerBrandingForm";
import { PartnerTenantsTab } from "./components/PartnerTenantsTab";

interface IPartnerDetailProps {
  partnerId: string;
}

const ADMIN_TOOLTIP = {
  content: "Necesitas el rol Administrador en este socio.",
};

/**
 * The partner console (U3, DESIGN.md §1.2): the partner's tenants and their usage per month,
 * and the white-label branding. Partner admins manage; partner viewers only look.
 */
export const PartnerDetail: React.FC<IPartnerDetailProps> = ({ partnerId }) => {
  const { addNotification } = useNotifications();
  const [activeTab, setActiveTab] = useState<string | number>("organizaciones");
  // GET /partners is unpaged (one row per partner membership): read whole.
  const { data: partners, isSuccess } = useMyPartnersQuery();
  const partner = partners?.find((candidate) => candidate.id === partnerId);
  // Like the tenant gates (ADR 0004): allowed until the memberships load, the server decides.
  const isAdmin = !isSuccess || partner?.role === "admin";
  const gateProps = isAdmin
    ? {}
    : { isAriaDisabled: true, tooltipProps: ADMIN_TOOLTIP };

  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const setBranding = useSetPartnerBrandingMutation(partnerId, {
    onSuccess: () =>
      addNotification({ variant: "success", title: "Se guardó la marca" }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo guardar la marca",
        }),
      ),
  });

  const title = partner?.name ?? "Socio";
  const role = partner?.role;

  return (
    <>
      <DocumentTitle title={title} />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/socios">Socios</Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>{title}</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>{title}</Content>
        {role ? (
          <Content component={ContentVariants.p}>
            Tu rol: {isPartnerRole(role) ? PARTNER_ROLE_LABELS[role] : role}
          </Content>
        ) : null}
      </PageSection>
      <PageSection>
        <Tabs
          activeKey={activeTab}
          onSelect={(_event, key) => setActiveTab(key)}
          aria-label="Secciones del socio"
        >
          <Tab
            eventKey="organizaciones"
            title={<TabTitleText>Organizaciones</TabTitleText>}
          >
            <PartnerTenantsTab partnerId={partnerId} gateProps={gateProps} />
          </Tab>
          <Tab eventKey="marca" title={<TabTitleText>Marca</TabTitleText>}>
            <Card isPlain>
              <CardBody>
                <Content component={ContentVariants.p}>
                  La marca con la que salen los PDF y correos de tus
                  organizaciones. Al guardar se reemplaza la marca actual: los
                  campos vacíos se quitan.
                </Content>
                <PartnerBrandingForm
                  submitError={submitError}
                  isSaving={setBranding.isPending}
                  gateProps={gateProps}
                  onSave={(branding) => {
                    clearSubmitError();
                    setBranding.mutate(branding);
                  }}
                />
              </CardBody>
            </Card>
          </Tab>
        </Tabs>
      </PageSection>
    </>
  );
};
