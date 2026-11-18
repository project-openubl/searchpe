import { type InferType, object, string } from "yup";

import {
  isReportFormat,
  isReportRegister,
} from "@app/queries/xhandler/reports";

/** The form's `period` is an `<input type="month">` value (`YYYY-MM`); the API takes `YYYYMM`. */
export const toReportPeriod = (month: string): string => month.replace("-", "");

export const createReportSchema = object({
  register: string()
    .required("Elige el registro.")
    .test("register", "Elige el registro.", (value) => isReportRegister(value)),
  format: string()
    .required("Elige el formato.")
    .test("format", "Elige el formato.", (value) => isReportFormat(value)),
  month: string()
    .required("Elige el periodo.")
    .matches(/^\d{4}-(0[1-9]|1[0-2])$/, "Elige un periodo válido (mes y año)."),
});

export type ICreateReportValues = InferType<typeof createReportSchema>;
