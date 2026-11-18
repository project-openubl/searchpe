import type React from "react";
import { useId } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import dayjs from "dayjs";

import {
  ActionGroup,
  Alert,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Form,
  Gallery,
  Label,
  PageSection,
  Progress,
  ProgressMeasureLocation,
  ProgressVariant,
  Spinner,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { ControlledTextField } from "@app/components/ControlledTextField";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { GatedButton } from "@app/components/GatedButton";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  type IBillingView,
  quotaPercent,
  useBillingDetailsMutation,
  useBillingQuery,
} from "@app/queries/xhandler/billing";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { billingDetailsSchema } from "./billingDetailsSchema";

interface IQuotaCardProps {
  title: string;
  period: string;
  used: number | undefined;
  limit: number | null | undefined;
}

/** One quota: what was used against the plan's limit; notices go out at 80% and 100%. */
const QuotaCard: React.FC<IQuotaCardProps> = ({
  title,
  period,
  used,
  limit,
}) => {
  const percent = quotaPercent({ used, limit });
  const usedText = (used ?? 0).toLocaleString("es-PE");
  return (
    <Card isCompact>
      <CardTitle component="h3">{title}</CardTitle>
      <CardBody>
        {percent === undefined ? (
          <Content component={ContentVariants.p}>
            {`${usedText} ${period} · sin límite`}
          </Content>
        ) : (
          <Progress
            value={percent}
            title={`${usedText} de ${(limit ?? 0).toLocaleString("es-PE")} ${period}`}
            measureLocation={ProgressMeasureLocation.outside}
            variant={
              percent >= 100
                ? ProgressVariant.danger
                : percent >= 80
                  ? ProgressVariant.warning
                  : undefined
            }
            aria-label={`${title}: ${percent}% usado`}
          />
        )}
      </CardBody>
    </Card>
  );
};

/** The organization's plan, its usage against the quotas, and billing details (DESIGN.md §14). */
export const BillingUsage: React.FC = () => {
  const { data, isLoading, error } = useBillingQuery();

  const body = (() => {
    if (isLoading) return <Spinner aria-label="Cargando uso y plan" />;
    if (error || !data) {
      const message = getMutationErrorMessage({
        error,
        fallback: "No se pudo cargar el uso del plan",
      });
      return (
        <Alert variant="danger" isInline title={message.title}>
          {message.description}
        </Alert>
      );
    }
    const usage = data.usage;
    const plan = usage?.plan;
    return (
      <Stack hasGutter>
        <StackItem>
          <Card>
            <CardTitle component="h2">Plan</CardTitle>
            <CardBody>
              {usage?.stale ? (
                <Alert
                  variant="warning"
                  isInline
                  isPlain
                  title="No se pudo actualizar el plan desde facturación; se muestran los últimos datos conocidos."
                />
              ) : null}
              <DescriptionList
                aria-label="Datos del plan"
                columnModifier={{ md: "2Col" }}
              >
                <DescriptionListGroup>
                  <DescriptionListTerm>Plan</DescriptionListTerm>
                  <DescriptionListDescription>
                    {plan?.name ?? plan?.code ?? "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Actualizado</DescriptionListTerm>
                  <DescriptionListDescription>
                    {usage?.synced_at
                      ? dayjs(usage.synced_at).format(RENDER_DATETIME_FORMAT)
                      : "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Webhooks</DescriptionListTerm>
                  <DescriptionListDescription>
                    <FeatureLabel isIncluded={plan?.webhooks} />
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>
                    Servidor de correo propio
                  </DescriptionListTerm>
                  <DescriptionListDescription>
                    <FeatureLabel isIncluded={plan?.custom_smtp} />
                  </DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
            </CardBody>
          </Card>
        </StackItem>
        <StackItem>
          <Gallery hasGutter minWidths={{ default: "100%", md: "280px" }}>
            <QuotaCard
              title="Producción"
              period="comprobantes este mes"
              used={usage?.production_this_month}
              limit={plan?.production_per_month}
            />
            <QuotaCard
              title="SUNAT beta"
              period="comprobantes hoy"
              used={usage?.beta_today}
              limit={plan?.beta_per_day}
            />
            <QuotaCard
              title="Simulado"
              period="comprobantes hoy"
              used={usage?.sandbox_today}
              limit={plan?.sandbox_per_day}
            />
          </Gallery>
        </StackItem>
        <StackItem>
          <BillingDetailsCard billing={data} />
        </StackItem>
      </Stack>
    );
  })();

  return (
    <>
      <DocumentTitle title="Uso y plan" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>Uso y plan</Content>
        <Content component={ContentVariants.p}>
          El plan de la organización, cuánto ha usado y a quién se factura.
        </Content>
      </PageSection>
      <PageSection>{body}</PageSection>
    </>
  );
};

interface IFeatureLabelProps {
  isIncluded: boolean | undefined;
}

const FeatureLabel: React.FC<IFeatureLabelProps> = ({ isIncluded }) =>
  isIncluded ? (
    <Label color="green" isCompact>
      Incluido
    </Label>
  ) : (
    <Label isCompact>No incluido</Label>
  );

interface IBillingDetailsCardProps {
  billing: IBillingView;
}

/** Who the platform's invoices (real comprobantes) are issued to. */
const BillingDetailsCard: React.FC<IBillingDetailsCardProps> = ({
  billing,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const toValues = (view: IBillingView) => ({
    billing_ruc: view.billing_ruc ?? "",
    billing_name: view.billing_name ?? "",
    billing_email: view.billing_email ?? "",
  });
  const { control, handleSubmit, formState, reset } = useForm({
    resolver: yupResolver(billingDetailsSchema),
    defaultValues: toValues(billing),
    mode: "onTouched",
  });

  const save = useBillingDetailsMutation({
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se guardaron los datos de facturación",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudieron guardar los datos de facturación",
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    // `PUT …/billing` replaces all three: empty ones are sent as unset.
    save.mutate(
      {
        billing_ruc: values.billing_ruc || undefined,
        billing_name: values.billing_name || undefined,
        billing_email: values.billing_email || undefined,
      },
      { onSuccess: (data) => reset(toValues(data)) },
    );
  });

  return (
    <Card>
      <CardTitle component="h2">Datos de facturación</CardTitle>
      <CardBody>
        <Form id={formId} onSubmit={onSubmit} maxWidth="600px">
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="billing_ruc"
            label="RUC"
            inputMode="numeric"
            helperText="Sin RUC, la plataforma te emite boletas en vez de facturas."
            isDisabled={!can("admin")}
          />
          <ControlledTextField
            control={control}
            name="billing_name"
            label="Razón social"
            isDisabled={!can("admin")}
          />
          <ControlledTextField
            control={control}
            name="billing_email"
            label="Correo de facturación"
            type="email"
            isDisabled={!can("admin")}
          />
          <ActionGroup>
            <GatedButton
              type="submit"
              form={formId}
              isLoading={save.isPending}
              isDisabled={save.isPending || !formState.isDirty}
              {...disabledProps("admin")}
            >
              Guardar
            </GatedButton>
          </ActionGroup>
        </Form>
      </CardBody>
    </Card>
  );
};
