import dayjs from "dayjs";

import { RENDER_DATE_FORMAT } from "@app/constants";

/** A date the way the filter shows it (`DD/MM/YYYY`) from the ISO date the API takes. */
export const formatFilterDate = (iso: string): string =>
  iso ? dayjs(iso).format(RENDER_DATE_FORMAT) : "";

/** Reads `DD/MM/YYYY`; anything else is an invalid date. */
export const parseFilterDate = (text: string): Date => {
  const match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(text.trim());
  if (!match) return new Date(Number.NaN);
  const [day, month, year] = [match[1], match[2], match[3]].map(Number);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day
    ? date
    : new Date(Number.NaN);
};

/** The ISO date (`YYYY-MM-DD`) the API filters by, or undefined while the text isn't a date. */
export const toFilterIsoDate = (text: string): string | undefined => {
  const date = parseFilterDate(text);
  return Number.isNaN(date.getTime())
    ? undefined
    : dayjs(date).format("YYYY-MM-DD");
};
