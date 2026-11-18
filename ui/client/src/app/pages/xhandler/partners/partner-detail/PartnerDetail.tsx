import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";

import {
  Alert,
  Breadcrumb,
  BreadcrumbItem,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  Flex,
  FlexItem,
  PageSection,
  Spinner,
  Tab,
  Tabs,
  TabTitleText,
} from "@patternfly/react-core";

import { ApiErrorText } from "@app/components/ApiErrorText";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  isPartnerRole,
  PARTNER_ROLE_LABELS,
  useMyPartnersQuery,
  usePartnerBrandingQuery,
  usePartnerLogoDeleteMutation,
  useSetPartnerBrandingMutation,
} from "@app/queries/xhandler/partners";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { PartnerBrandingForm } from "./components/PartnerBrandingForm";
import { PartnerLogoModal } from "./components/PartnerLogoModal";
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

  const branding = usePartnerBrandingQuery({ partnerId });
  const [isLogoOpen, setIsLogoOpen] = useState(false);
  const [isDeleteLogoOpen, setIsDeleteLogoOpen] = useState(false);
  const deleteLogo = usePartnerLogoDeleteMutation(partnerId, {
    onSuccess: () =>
      addNotification({ variant: "success", title: "Se quitó el logo" }),
    onError: (error) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error,
          fallback: "No se pudo quitar el logo",
        }),
      }),
  });
  const hasLogo = branding.data?.has_logo === true;

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
                {branding.isLoading ? (
                  <Spinner size="lg" aria-label="Cargando la marca" />
                ) : branding.error ? (
                  <Alert
                    variant="danger"
                    isInline
                    title="No se pudo cargar la marca"
                  >
                    <ApiErrorText
                      error={getMutationErrorMessage({
                        error: branding.error,
                        fallback: "No se pudo cargar la marca",
                      })}
                    />
                  </Alert>
                ) : (
                  <PartnerBrandingForm
                    branding={branding.data?.branding}
                    submitError={submitError}
                    isSaving={setBranding.isPending}
                    gateProps={gateProps}
                    onSave={(values) => {
                      clearSubmitError();
                      setBranding.mutate(values);
                    }}
                  />
                )}
              </CardBody>
            </Card>
            <Card isPlain>
              <CardTitle>Logo</CardTitle>
              <CardBody>
                <Flex
                  alignItems={{ default: "alignItemsCenter" }}
                  spaceItems={{ default: "spaceItemsMd" }}
                >
                  <FlexItem>
                    <Content component={ContentVariants.p}>
                      {hasLogo
                        ? "Tus organizaciones usan tu logo en los PDF de las empresas que no tienen uno propio."
                        : "Sin logo: los PDF de las empresas sin logo propio salen sin logo."}
                    </Content>
                  </FlexItem>
                  <FlexItem>
                    <GatedButton
                      variant="secondary"
                      onClick={() => setIsLogoOpen(true)}
                      {...gateProps}
                    >
                      {hasLogo ? "Cambiar logo" : "Subir logo"}
                    </GatedButton>
                  </FlexItem>
                  {hasLogo ? (
                    <FlexItem>
                      <GatedButton
                        variant="link"
                        isDanger
                        onClick={() => setIsDeleteLogoOpen(true)}
                        {...gateProps}
                      >
                        Quitar logo
                      </GatedButton>
                    </FlexItem>
                  ) : null}
                </Flex>
              </CardBody>
            </Card>
          </Tab>
        </Tabs>
      </PageSection>
      <PartnerLogoModal
        isOpen={isLogoOpen}
        partnerId={partnerId}
        onClose={() => setIsLogoOpen(false)}
      />
      <ConfirmActionModal
        isOpen={isDeleteLogoOpen}
        title="¿Quitar el logo?"
        body="Los PDF de las empresas sin logo propio saldrán sin logo."
        confirmLabel="Quitar logo"
        cancelLabel="Volver"
        isConfirming={deleteLogo.isPending}
        onConfirm={() =>
          deleteLogo.mutate(undefined, {
            onSuccess: () => setIsDeleteLogoOpen(false),
          })
        }
        onCancel={() => setIsDeleteLogoOpen(false)}
      />
    </>
  );
};
