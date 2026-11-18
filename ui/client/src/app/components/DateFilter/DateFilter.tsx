import type React from "react";

import dayjs from "dayjs";

import {
  DatePicker,
  ToolbarFilter,
  type ToolbarLabel,
} from "@patternfly/react-core";

import { RENDER_DATE_FORMAT } from "@app/constants";

import {
  formatFilterDate,
  parseFilterDate,
  toFilterIsoDate,
} from "./dateFilterValue";

interface IDateFilterProps {
  /** The input's accessible name and placeholder hint. */
  placeholder: string;
  // --- Identity read by DataViewFilters (must be passed on the element) ---
  filterId: string;
  title: string;
  // --- Injected by DataViewFilters via cloneElement ---
  /** An ISO date (`YYYY-MM-DD`) or "" for none, as the API's `from`/`to` take it. */
  value?: string;
  onChange?: (event: unknown, value: string) => void;
  showToolbarItem?: boolean;
}

/**
 * A one-date filter for `DataViewFilters` (`from`/`to` params): typed or picked as
 * `DD/MM/YYYY`, kept as an ISO date, shown as a chip DataView can clear.
 */
export const DateFilter: React.FC<IDateFilterProps> = ({
  placeholder,
  filterId,
  title,
  value = "",
  onChange,
  showToolbarItem,
}) => {
  const labels: ToolbarLabel[] = value
    ? [{ key: value, node: formatFilterDate(value) }]
    : [];

  return (
    <ToolbarFilter
      labels={labels}
      deleteLabel={() => onChange?.(undefined, "")}
      categoryName={title}
      showToolbarItem={showToolbarItem}
    >
      <DatePicker
        id={`${filterId}-filter`}
        aria-label={placeholder}
        value={formatFilterDate(value)}
        placeholder="DD/MM/AAAA"
        dateFormat={(date) => dayjs(date).format(RENDER_DATE_FORMAT)}
        dateParse={parseFilterDate}
        invalidFormatText="Escribe la fecha como DD/MM/AAAA"
        buttonAriaLabel={`Abrir calendario: ${title}`}
        locale="es-PE"
        weekStart={1}
        prevMonthAriaLabel="Mes anterior"
        nextMonthAriaLabel="Mes siguiente"
        yearInputAriaLabel="Año"
        onChange={(_event, text) => {
          if (!text.trim()) {
            onChange?.(undefined, "");
            return;
          }
          const iso = toFilterIsoDate(text);
          if (iso && iso !== value) onChange?.(undefined, iso);
        }}
      />
    </ToolbarFilter>
  );
};
