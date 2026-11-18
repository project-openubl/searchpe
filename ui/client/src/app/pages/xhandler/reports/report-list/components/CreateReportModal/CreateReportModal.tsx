import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

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
  TextInput,
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  REPORT_FORMATS,
  REPORT_REGISTERS,
  useReportCreateMutation,
} from "@app/queries/xhandler/reports";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  formatPeriod,
  REPORT_FORMAT_LABELS,
  REPORT_REGISTER_LABELS,
} from "../../reportPresentation";
import {
  createReportSchema,
  type ICreateReportValues,
  toReportPeriod,
} from "./createReportSchema";

interface ICreateReportModalProps {
  companyId: string;
  onClose: () => void;
}

/** Requests a report (DESIGN.md §11.9): `202`, a job generates it. */
export const CreateReportModal: React.FC<ICreateReportModalProps> = ({
  companyId,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  // One key per opening of the dialog: a retry of the same submission reuses it.
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ICreateReportValues>({
    resolver: yupResolver(createReportSchema),
    defaultValues: { register: "sales", format: "xlsx", month: "" },
  });

  const createReport = useReportCreateMutation({
    onSuccess: (report) =>
      addNotification({
        variant: "info",
        title: `Se solicitó el reporte del periodo ${formatPeriod(report.period)}`,
        description:
          "Un trabajo lo genera; podrás descargarlo cuando esté listo.",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo solicitar el reporte",
        }),
      ),
  });

  const onSubmit = (values: ICreateReportValues) => {
    clearSubmitError();
    createReport.mutate(
      {
        companyId,
        idempotencyKey,
        body: {
          register: values.register,
          format: values.format,
          period: toReportPeriod(values.month),
        },
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader title="Nuevo reporte" labelId={titleId} />
      <ModalBody>
        <Form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="register"
            render={({ field }) => (
              <FormGroup
                label="Registro"
                isRequired
                fieldId={`${formId}-register`}
              >
                <FormSelect
                  id={`${formId}-register`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {REPORT_REGISTERS.map((register) => (
                    <FormSelectOption
                      key={register}
                      value={register}
                      label={REPORT_REGISTER_LABELS[register]}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="format"
            render={({ field }) => (
              <FormGroup
                label="Formato"
                isRequired
                fieldId={`${formId}-format`}
              >
                <FormSelect
                  id={`${formId}-format`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {REPORT_FORMATS.map((format) => (
                    <FormSelectOption
                      key={format}
                      value={format}
                      label={REPORT_FORMAT_LABELS[format]}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="month"
            render={({ field }) => (
              <FormGroup label="Periodo" isRequired fieldId={`${formId}-month`}>
                <TextInput
                  id={`${formId}-month`}
                  type="month"
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.month ? "error" : "default"}
                />
                {errors.month ? (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant="error">
                        {errors.month.message}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                ) : null}
              </FormGroup>
            )}
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={createReport.isPending}
          isDisabled={createReport.isPending}
        >
          Solicitar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
