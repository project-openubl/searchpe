import type React from "react";
import { useId } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { Link, useNavigate } from "@tanstack/react-router";

import {
  ActionGroup,
  Breadcrumb,
  BreadcrumbItem,
  Button,
  Card,
  CardBody,
  Content,
  ContentVariants,
  Form,
  PageSection,
} from "@patternfly/react-core";

import { ControlledTextField } from "@app/components/ControlledTextField";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useCompanyCreateMutation } from "@app/queries/xhandler/companies";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { companyCreateSchema } from "./companyCreateSchema";

/** Registers a company (RUC) in the organization; it starts unverified, in SUNAT beta. */
export const CompanyCreate: React.FC = () => {
  const formId = useId();
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { can } = useTenantRole();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();

  const { control, handleSubmit, formState } = useForm({
    resolver: yupResolver(companyCreateSchema),
    defaultValues: { ruc: "", razon_social: "", nombre_comercial: "" },
    mode: "onTouched",
  });

  const createCompany = useCompanyCreateMutation({
    onSuccess: (company) =>
      addNotification({
        variant: "success",
        title: `Se registró la empresa ${company.ruc ?? ""}`.trim(),
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo registrar la empresa",
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    createCompany.mutate(
      {
        ruc: values.ruc,
        razon_social: values.razon_social,
        nombre_comercial: values.nombre_comercial || undefined,
      },
      {
        onSuccess: (company) =>
          company.id
            ? navigate({
                to: "/organizaciones/$tenantId/empresas/$companyId",
                params: { tenantId, companyId: company.id },
              })
            : navigate({
                to: "/organizaciones/$tenantId/empresas",
                params: { tenantId },
              }),
      },
    );
  });

  return (
    <>
      <DocumentTitle title="Nueva empresa" />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones/$tenantId/empresas" params={{ tenantId }}>
              Empresas
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>Nueva empresa</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Nueva empresa</Content>
        <Content component={ContentVariants.p}>
          La empresa empieza en SUNAT beta y sin verificar; verifica su RUC con
          su usuario SOL antes de pasar a producción.
        </Content>
      </PageSection>
      <PageSection>
        {can("admin") ? (
          <Card>
            <CardBody>
              <Form id={formId} onSubmit={onSubmit} maxWidth="600px">
                <FormSubmitError error={submitError} />
                <ControlledTextField
                  control={control}
                  name="ruc"
                  label="RUC"
                  isRequired
                  inputMode="numeric"
                />
                <ControlledTextField
                  control={control}
                  name="razon_social"
                  label="Razón social"
                  isRequired
                />
                <ControlledTextField
                  control={control}
                  name="nombre_comercial"
                  label="Nombre comercial"
                />
                <ActionGroup>
                  <Button
                    type="submit"
                    form={formId}
                    isLoading={createCompany.isPending}
                    isDisabled={
                      createCompany.isPending || formState.isSubmitting
                    }
                  >
                    Registrar empresa
                  </Button>
                  <Button
                    variant="link"
                    onClick={() =>
                      navigate({
                        to: "/organizaciones/$tenantId/empresas",
                        params: { tenantId },
                      })
                    }
                  >
                    Cancelar
                  </Button>
                </ActionGroup>
              </Form>
            </CardBody>
          </Card>
        ) : (
          <UnauthorizedState description="Necesitas el rol Administrador o superior para registrar empresas." />
        )}
      </PageSection>
    </>
  );
};
