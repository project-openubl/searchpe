import type React from "react";
import { type Control, useFieldArray, useFormState } from "react-hook-form";

import {
  Button,
  Flex,
  FlexItem,
  FormFieldGroup,
  FormFieldGroupHeader,
  FormHelperText,
  HelperText,
  HelperTextItem,
} from "@patternfly/react-core";
import MinusCircleIcon from "@patternfly/react-icons/dist/esm/icons/minus-circle-icon";
import PlusCircleIcon from "@patternfly/react-icons/dist/esm/icons/plus-circle-icon";

import { ControlledTextField } from "@app/components/ControlledTextField";

import type { IDetalleValues, IDraftFormValues } from "../../draftFormSchema";

interface IDetallesFieldProps {
  control: Control<IDraftFormValues>;
  /** The line a new "Agregar detalle" starts with. */
  emptyDetalle: IDetalleValues;
  pricesIncludeIgv: boolean;
  isDisabled?: boolean;
}

/** The document's lines (Detalle): description, quantity and unit price; at least one. */
export const DetallesField: React.FC<IDetallesFieldProps> = ({
  control,
  emptyDetalle,
  pricesIncludeIgv,
  isDisabled,
}) => {
  const { fields, append, remove } = useFieldArray({
    control,
    name: "detalles",
  });
  const { errors } = useFormState({ control, name: "detalles" });
  const listError = errors.detalles?.root?.message ?? errors.detalles?.message;

  return (
    <FormFieldGroup
      header={
        <FormFieldGroupHeader
          titleText={{ text: "Detalle", id: "detalles-title" }}
          titleDescription="Los bienes o servicios del comprobante."
          actions={
            <Button
              variant="secondary"
              icon={<PlusCircleIcon />}
              isDisabled={isDisabled}
              onClick={() => append({ ...emptyDetalle })}
            >
              Agregar detalle
            </Button>
          }
        />
      }
    >
      {fields.map((field, index) => (
        <Flex
          key={field.id}
          alignItems={{ default: "alignItemsFlexEnd" }}
          role="group"
          aria-label={`Detalle ${index + 1}`}
        >
          <FlexItem grow={{ default: "grow" }}>
            <ControlledTextField
              control={control}
              name={`detalles.${index}.descripcion`}
              label="Descripción"
              isRequired
              isDisabled={isDisabled}
            />
          </FlexItem>
          <FlexItem>
            <ControlledTextField
              control={control}
              name={`detalles.${index}.cantidad`}
              label="Cantidad"
              isRequired
              inputMode="decimal"
              isDisabled={isDisabled}
            />
          </FlexItem>
          <FlexItem>
            <ControlledTextField
              control={control}
              name={`detalles.${index}.precio`}
              label={
                pricesIncludeIgv
                  ? "Precio unitario (con IGV)"
                  : "Valor unitario (sin IGV)"
              }
              isRequired
              inputMode="decimal"
              isDisabled={isDisabled}
            />
          </FlexItem>
          <FlexItem>
            <Button
              variant="plain"
              aria-label={`Quitar detalle ${index + 1}`}
              icon={<MinusCircleIcon />}
              isDisabled={isDisabled || fields.length === 1}
              onClick={() => remove(index)}
            />
          </FlexItem>
        </Flex>
      ))}
      {listError ? (
        <FormHelperText>
          <HelperText>
            <HelperTextItem variant="error">{listError}</HelperTextItem>
          </HelperText>
        </FormHelperText>
      ) : null}
    </FormFieldGroup>
  );
};
