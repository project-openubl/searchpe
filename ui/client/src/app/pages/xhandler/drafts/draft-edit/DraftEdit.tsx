import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { Link, useNavigate } from "@tanstack/react-router";

import {
  ActionGroup,
  Alert,
  AlertActionCloseButton,
  Breadcrumb,
  BreadcrumbItem,
  Card,
  CardBody,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Flex,
  FlexItem,
  Form,
  FormFieldGroupExpandable,
  FormFieldGroupHeader,
  Grid,
  GridItem,
  PageSection,
  Switch,
} from "@patternfly/react-core";

import type {
  CompanyView,
  DocumentView,
  DraftView,
} from "@app/client/xhandler";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { GatedButton } from "@app/components/GatedButton";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import { useDocumentDetailQuery } from "@app/queries/xhandler/documents";
import {
  type DraftConflictKind,
  getDraftConflictKind,
  useDraftDeleteMutation,
  useDraftIssueMutation,
  useDraftUpdateMutation,
  useSuspenseDraftDetailQuery,
} from "@app/queries/xhandler/drafts";
import { useSeriesListQuery } from "@app/queries/xhandler/series";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { draftTypeLabel } from "@app/utils/draftTypes";

import { DetallesField } from "./components/DetallesField";
import { SelectField } from "./components/SelectField";
import {
  draftFormSchema,
  type IDraftFormValues,
  TIPOS_DOCUMENTO_IDENTIDAD,
} from "./draftFormSchema";
import {
  EMPTY_DETALLE,
  TIPOS_NOTA,
  toDraftInput,
  toFormValues,
} from "./draftFormValues";

const MONEDAS = [
  { value: "PEN", label: "Soles (PEN)" },
  { value: "USD", label: "Dólares (USD)" },
] as const;

interface IDraftEditProps {
  draftId: string;
}

const CONFLICT_MESSAGES: Record<DraftConflictKind, string> = {
  draft_issued:
    "Este borrador ya se emitió en otra sesión, así que tus cambios no se guardaron.",
  draft_changed:
    "Este borrador cambió en otra sesión. Se cargó la versión actual y tus cambios no se guardaron: revísala y vuelve a intentarlo.",
};

/**
 * Manual issuing (DESIGN.md §11.10): edit a draft (no number yet) and issue it, which numbers,
 * signs and sends it like any document. An issued draft is shown read-only.
 */
export const DraftEdit: React.FC<IDraftEditProps> = ({ draftId }) => {
  const tenantId = useSelectedTenantId();
  const { data: draft } = useSuspenseDraftDetailQuery({ draftId });
  const { data: company } = useSuspenseCompanyDetailQuery({
    companyId: draft.company_id ?? "",
  });
  const title = `${draftTypeLabel(draft)}: borrador`;
  // A 409 already refetched the draft (drafts.ts): remount the form on the stored version.
  const [conflict, setConflict] = useState<DraftConflictKind | null>(null);
  const [formGeneration, setFormGeneration] = useState(0);
  const onConflict = (kind: DraftConflictKind) => {
    setConflict(kind);
    setFormGeneration((generation) => generation + 1);
  };

  return (
    <>
      <DocumentTitle title={title} />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link
              to="/organizaciones/$tenantId/borradores"
              params={{ tenantId }}
            >
              Emisión manual
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>{draftTypeLabel(draft)}</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>{title}</Content>
        <Content component={ContentVariants.p}>
          {company.razon_social} (RUC {company.ruc}){" "}
          <SunatEnvLabel sunatEnv={company.sunat_env} />
        </Content>
      </PageSection>
      <PageSection>
        {conflict ? (
          <Alert
            isInline
            variant="warning"
            title={CONFLICT_MESSAGES[conflict]}
            actionClose={
              <AlertActionCloseButton
                aria-label="Cerrar el aviso"
                onClose={() => setConflict(null)}
              />
            }
          />
        ) : null}
        {draft.status === "issued" ? (
          <IssuedDraft draft={draft} />
        ) : (
          <DraftForm
            key={`${draft.id}-${formGeneration}`}
            draft={draft}
            company={company}
            onConflict={onConflict}
          />
        )}
      </PageSection>
    </>
  );
};

interface IIssuedDraftProps {
  draft: DraftView;
}

/** What an issued draft became: its document, numbered by the service. */
const IssuedDraft: React.FC<IIssuedDraftProps> = ({ draft }) => {
  const { data: document } = useDocumentDetailQuery({
    documentId: draft.document_id,
  });
  return (
    <Card>
      <CardBody>
        <Alert
          isInline
          isPlain
          variant="success"
          title="Este borrador ya se emitió: los comprobantes emitidos no se modifican."
        />
      </CardBody>
      <CardBody>
        <DescriptionList aria-label="Comprobante emitido">
          <DescriptionListGroup>
            <DescriptionListTerm>Serie y número</DescriptionListTerm>
            <DescriptionListDescription>
              {document?.serie_numero ?? "-"}
            </DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Cliente</DescriptionListTerm>
            <DescriptionListDescription>
              {draft.customer?.nombre ?? "-"}
            </DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Identificador</DescriptionListTerm>
            <DescriptionListDescription>
              {draft.document_id ? `xh:document:${draft.document_id}` : "-"}
            </DescriptionListDescription>
          </DescriptionListGroup>
        </DescriptionList>
      </CardBody>
    </Card>
  );
};

interface IDraftFormProps {
  draft: DraftView;
  company: CompanyView;
  /** A save or issue answered 409: the draft was issued or changed elsewhere. */
  onConflict: (kind: DraftConflictKind) => void;
}

const DraftForm: React.FC<IDraftFormProps> = ({
  draft,
  company,
  onConflict,
}) => {
  const formId = useId();
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const [issueTarget, setIssueTarget] = useState<IDraftFormValues | null>(null);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const canWrite = can("operator");
  const draftId = draft.id ?? "";
  // The version this form started from, moved on by its own saves only: a change made elsewhere
  // meanwhile makes the next save a 409 instead of overwriting it.
  const [editedVersion, setEditedVersion] = useState(draft.version);

  const { control, handleSubmit, formState, reset } = useForm<IDraftFormValues>(
    {
      resolver: yupResolver(draftFormSchema),
      defaultValues: toFormValues(draft),
      mode: "onTouched",
    },
  );
  // Read while rendering, so react-hook-form tracks it for the issue handler.
  const { isDirty } = formState;
  const pricesIncludeIgv = useWatch({ control, name: "precios_con_igv" });
  const isNote = useWatch({ control, name: "es_nota" });

  // GET …/series is unpaged (a company has a handful): read whole for the picker.
  const { data: series } = useSeriesListQuery({
    companyId: draft.company_id ?? "",
  });
  const serieOptions = [
    { value: "", label: "Serie por defecto" },
    ...(series ?? [])
      .filter(
        (item) =>
          item.tipo_comprobante === draft.tipo_comprobante &&
          item.sunat_env === company.sunat_env &&
          (item.active || item.serie === draft.serie),
      )
      .map((item) => ({ value: item.serie ?? "", label: item.serie ?? "" })),
  ];
  if (draft.serie && !serieOptions.some((o) => o.value === draft.serie)) {
    serieOptions.push({ value: draft.serie, label: draft.serie });
  }

  const updateDraft = useDraftUpdateMutation(draftId);
  const issueDraft = useDraftIssueMutation({
    onSuccess: (document: DocumentView) =>
      addNotification({
        variant: "success",
        title:
          `Se emitió ${draftTypeLabel(draft)} ${document.serie_numero ?? ""}`.trim(),
        description: "SUNAT lo recibirá en unos segundos.",
      }),
  });
  const deleteDraft = useDraftDeleteMutation({
    onSuccess: () =>
      addNotification({ variant: "success", title: "Se eliminó el borrador" }),
    onError: (error) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error,
          fallback: "No se pudo eliminar el borrador",
        }),
      }),
  });

  const toInput = (values: IDraftFormValues) => ({
    ...toDraftInput({ draft, values }),
    version: editedVersion,
  });
  const saved = (values: IDraftFormValues, result: DraftView) => {
    setEditedVersion(result.version);
    reset(values);
  };

  const showFailure = ({
    error,
    fallback,
  }: {
    error: unknown;
    fallback: string;
  }) => {
    const conflict = getDraftConflictKind(error);
    if (conflict) {
      onConflict(conflict);
    } else {
      setSubmitError(getMutationErrorMessage({ error, fallback }));
    }
  };

  const onSave = handleSubmit((values) => {
    clearSubmitError();
    updateDraft.mutate(toInput(values), {
      onSuccess: (result) => {
        saved(values, result);
        addNotification({ variant: "success", title: "Se guardó el borrador" });
      },
      onError: (error) =>
        showFailure({ error, fallback: "No se pudo guardar el borrador" }),
    });
  });

  // Saves pending changes first, then issues: two chained requests (toastr rule).
  const issue = async (values: IDraftFormValues) => {
    setIssueTarget(null);
    clearSubmitError();
    try {
      let version = editedVersion;
      if (isDirty) {
        const result = await updateDraft.mutateAsync(toInput(values));
        saved(values, result);
        version = result.version;
      }
      await issueDraft.mutateAsync({
        draftId,
        version,
        idempotencyKey: crypto.randomUUID(),
      });
    } catch (error) {
      showFailure({ error, fallback: "No se pudo emitir el comprobante" });
    }
  };

  const isBusy = updateDraft.isPending || issueDraft.isPending;
  const isDisabled = !canWrite || isBusy;
  const notaOptions =
    draft.tipo_comprobante === "07" || draft.tipo_comprobante === "08"
      ? TIPOS_NOTA[draft.tipo_comprobante]
      : [];

  return (
    <Card>
      <CardBody>
        <Form id={formId} onSubmit={onSave}>
          <FormSubmitError error={submitError} />
          <Grid hasGutter md={4}>
            <GridItem>
              <SelectField
                control={control}
                name="serie"
                label="Serie"
                options={serieOptions}
                helperText="El número lo asigna el servicio al emitir."
                isDisabled={isDisabled}
              />
            </GridItem>
            <GridItem>
              <ControlledTextField
                control={control}
                name="fecha_emision"
                label="Fecha de emisión"
                type="date"
                helperText="Vacía: la fecha de hoy al emitir."
                isDisabled={isDisabled}
              />
            </GridItem>
            <GridItem>
              <SelectField
                control={control}
                name="moneda"
                label="Moneda"
                options={MONEDAS}
                isRequired
                isDisabled={isDisabled}
              />
            </GridItem>
          </Grid>

          <FormFieldGroupExpandable
            isExpanded
            toggleAriaLabel="Mostrar u ocultar el cliente"
            header={
              <FormFieldGroupHeader
                titleText={{ text: "Cliente", id: "cliente-title" }}
                titleDescription="A quién se emite el comprobante."
              />
            }
          >
            <Grid hasGutter md={4}>
              <GridItem>
                <SelectField
                  control={control}
                  name="cliente_tipo"
                  label="Tipo de documento"
                  options={TIPOS_DOCUMENTO_IDENTIDAD}
                  isRequired
                  isDisabled={isDisabled}
                />
              </GridItem>
              <GridItem>
                <ControlledTextField
                  control={control}
                  name="cliente_numero"
                  label="Número de documento"
                  isRequired
                  isDisabled={isDisabled}
                />
              </GridItem>
              <GridItem>
                <ControlledTextField
                  control={control}
                  name="cliente_nombre"
                  label="Nombre o razón social"
                  isRequired
                  isDisabled={isDisabled}
                />
              </GridItem>
            </Grid>
          </FormFieldGroupExpandable>

          {isNote ? (
            <FormFieldGroupExpandable
              isExpanded
              toggleAriaLabel="Mostrar u ocultar el comprobante que corrige"
              header={
                <FormFieldGroupHeader
                  titleText={{
                    text: "Comprobante que corrige",
                    id: "afectado-title",
                  }}
                  titleDescription="Desde 2027 una nota corrige un solo comprobante."
                />
              }
            >
              <Grid hasGutter md={4}>
                <GridItem>
                  <ControlledTextField
                    control={control}
                    name="afectado_serie_numero"
                    label="Serie y número"
                    placeholder="F001-25"
                    isRequired
                    isDisabled={isDisabled}
                  />
                </GridItem>
                <GridItem>
                  <SelectField
                    control={control}
                    name="tipo_nota"
                    label="Motivo"
                    options={[
                      { value: "", label: "Elige el motivo" },
                      ...notaOptions,
                    ]}
                    isRequired
                    isDisabled={isDisabled}
                  />
                </GridItem>
                <GridItem>
                  <ControlledTextField
                    control={control}
                    name="sustento"
                    label="Sustento"
                    isRequired
                    isDisabled={isDisabled}
                  />
                </GridItem>
              </Grid>
            </FormFieldGroupExpandable>
          ) : null}

          <Controller
            control={control}
            name="precios_con_igv"
            render={({ field }) => (
              <Switch
                id={`${formId}-precios-con-igv`}
                label="Los precios incluyen IGV"
                isChecked={field.value}
                onChange={(_event, checked) => field.onChange(checked)}
                isDisabled={isDisabled}
              />
            )}
          />
          <DetallesField
            control={control}
            emptyDetalle={EMPTY_DETALLE}
            pricesIncludeIgv={pricesIncludeIgv}
            isDisabled={isDisabled}
          />

          <ActionGroup>
            <Flex>
              <FlexItem>
                <GatedButton
                  variant="primary"
                  isLoading={issueDraft.isPending}
                  isDisabled={isBusy}
                  {...disabledProps("operator")}
                  onClick={handleSubmit((values) => setIssueTarget(values))}
                >
                  Emitir
                </GatedButton>
              </FlexItem>
              <FlexItem>
                <GatedButton
                  type="submit"
                  form={formId}
                  variant="secondary"
                  isLoading={updateDraft.isPending && !issueDraft.isPending}
                  isDisabled={isBusy}
                  {...disabledProps("operator")}
                >
                  Guardar borrador
                </GatedButton>
              </FlexItem>
              <FlexItem>
                <GatedButton
                  variant="link"
                  isDisabled={isBusy}
                  {...disabledProps("operator")}
                  onClick={() => setIsDeleteOpen(true)}
                >
                  Eliminar borrador
                </GatedButton>
              </FlexItem>
            </Flex>
          </ActionGroup>
        </Form>
      </CardBody>

      <ConfirmActionModal
        isOpen={issueTarget !== null}
        title="¿Emitir el comprobante?"
        body="El servicio le asignará el siguiente número de la serie, lo firmará y lo enviará a SUNAT. Un comprobante emitido no se modifica: se corrige con una nota."
        confirmLabel="Emitir"
        cancelLabel="Volver"
        confirmVariant="primary"
        isConfirming={isBusy}
        onConfirm={() => issueTarget && void issue(issueTarget)}
        onCancel={() => setIssueTarget(null)}
      />
      <ConfirmActionModal
        isOpen={isDeleteOpen}
        title="¿Eliminar el borrador?"
        body="El borrador se eliminará; no se emitió, así que no consume ningún número."
        confirmLabel="Eliminar"
        isConfirming={deleteDraft.isPending}
        onConfirm={() =>
          deleteDraft.mutate(draft, {
            onSuccess: () =>
              navigate({
                to: "/organizaciones/$tenantId/borradores",
                params: { tenantId },
              }),
          })
        }
        onCancel={() => setIsDeleteOpen(false)}
      />
    </Card>
  );
};
