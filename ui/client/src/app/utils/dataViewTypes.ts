// The `DataViewTh` *type* is not re-exported usably from the package barrel: its
// name collides with the `DataViewTh` component (a value), so the barrel symbol
// carries the value meaning only. Re-export the type from its source module.
export type { DataViewTh } from "@patternfly/react-data-view/dist/esm/DataViewTable/DataViewTable";
