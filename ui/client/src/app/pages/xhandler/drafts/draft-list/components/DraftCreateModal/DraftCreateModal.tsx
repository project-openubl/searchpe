import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { useNavigate } from "@tanstack/react-router";
import { object, string } from "yup";

import {
  Button,
  Form,
  FormGroup,
  FormHelperText,
  FormSelect,
  FormSelectOption,
  HelperText,
  HelperTextItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { DROPDOWN_PAGE_SIZE } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useCompaniesListQuery } from "@app/queries/xhandler/companies";
import { useDraftCreateMutation } from "@app/queries/xhandler/drafts";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { DRAFT_TYPES, isDraftTipo } from "@app/utils/draftTypes";

const schema = object({
  companyId: string().required("Elige la empresa"),
  tipo: string()
    .required("Elige el tipo de comprobante")
    .test("tipo", "Elige el tipo de comprobante", (value) =>
      isDraftTipo(value),
    ),
});

interface IDraftCreateModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/** Starts a draft: the company and the document type; the rest is filled in its own page. */
export const DraftCreateModal: React.FC<IDraftCreateModalProps> = ({
  isOpen,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="draft-create-title"
  >
    {/* Mounted only while open: each opening is a new intent with its own Idempotency-Key. */}
    {isOpen ? <DraftCreateForm onClose={onClose} /> : null}
  </Modal>
);

interface IDraftCreateFormProps {
  onClose: () => void;
}

const DraftCreateForm: React.FC<IDraftCreateFormProps> = ({ onClose }) => {
  const formId = useId();
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const [idempotencyKey] = useState(() => crypto.randomUUID());

  // Few companies per tenant: the first page fills the picker (no name filter in the API).
  const { data: companies, isLoading } = useCompaniesListQuery({
    params: { limit: DROPDOWN_PAGE_SIZE, offset: 0 },
  });
  const firstCompanyId = companies?.items?.[0]?.id ?? "";

  const { control, handleSubmit } = useForm({
    resolver: yupResolver(schema),
    values: { companyId: firstCompanyId, tipo: "01" },
    resetOptions: { keepDirtyValues: true },
  });

  const createDraft = useDraftCreateMutation({
    onSuccess: () =>
      addNotification({ variant: "success", title: "Se creó el borrador" }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo crear el borrador",
        }),
      ),
  });

  const onSubmit = handleSubmit(({ companyId, tipo }) => {
    clearSubmitError();
    const type = DRAFT_TYPES.find((item) => item.tipo === tipo);
    if (!type) return;
    createDraft.mutate(
      {
        companyId,
        idempotencyKey,
        body: {
          kind: type.kind,
          // A boleta is an Invoice with tipo_comprobante 03 (xbuilder).
          spec:
            type.kind === "Invoice"
              ? { tipo_comprobante: type.tipo, moneda: "PEN" }
              : { moneda: "PEN" },
        },
      },
      {
        onSuccess: (draft) => {
          onClose();
          if (draft.id) {
            void navigate({
              to: "/organizaciones/$tenantId/borradores/$draftId",
              params: { tenantId, draftId: draft.id },
            });
          }
        },
      },
    );
  });

  return (
    <>
      <ModalHeader title="Nuevo borrador" labelId="draft-create-title" />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="companyId"
            render={({ field, fieldState }) => (
              <FormGroup
                label="Empresa"
                isRequired
                fieldId={`${formId}-company`}
              >
                <FormSelect
                  id={`${formId}-company`}
                  value={field.value}
                  onChange={(_event, next) => field.onChange(next)}
                  onBlur={field.onBlur}
                  isDisabled={isLoading}
                  validated={fieldState.error ? "error" : "default"}
                >
                  {(companies?.items ?? []).length === 0 ? (
                    <FormSelectOption
                      value=""
                      label={
                        isLoading ? "Cargando empresas" : "No hay empresas"
                      }
                      isPlaceholder
                    />
                  ) : null}
                  {(companies?.items ?? []).map((company) => (
                    <FormSelectOption
                      key={company.id}
                      value={company.id}
                      label={`${company.razon_social ?? ""} (RUC ${company.ruc ?? ""})`}
                    />
                  ))}
                </FormSelect>
                {fieldState.error ? (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant="error">
                        {fieldState.error.message}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                ) : null}
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="tipo"
            render={({ field }) => (
              <FormGroup
                label="Tipo de comprobante"
                isRequired
                fieldId={`${formId}-tipo`}
              >
                <FormSelect
                  id={`${formId}-tipo`}
                  value={field.value}
                  onChange={(_event, next) => field.onChange(next)}
                  onBlur={field.onBlur}
                >
                  {DRAFT_TYPES.map((type) => (
                    <FormSelectOption
                      key={type.tipo}
                      value={type.tipo}
                      label={type.label}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={createDraft.isPending}
          isDisabled={createDraft.isPending}
        >
          Crear borrador
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
