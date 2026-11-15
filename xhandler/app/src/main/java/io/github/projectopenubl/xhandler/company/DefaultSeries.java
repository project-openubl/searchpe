package io.github.projectopenubl.xhandler.company;

import java.util.List;

/** Series created with every company (DESIGN.md §8). */
final class DefaultSeries {

    record Entry(String tipoComprobante, String serie) {}

    static final List<Entry> ENTRIES = List.of(
            new Entry("01", "F001"), // factura
            new Entry("03", "B001"), // boleta
            new Entry("07", "FC01"), // nota de crédito de factura
            new Entry("07", "BC01"), // nota de crédito de boleta
            new Entry("08", "FD01"), // nota de débito de factura
            new Entry("08", "BD01"), // nota de débito de boleta
            new Entry("09", "T001"), // guía de remisión remitente
            new Entry("31", "V001"), // guía de remisión transportista
            new Entry("40", "P001"), // percepción
            new Entry("20", "R001")); // retención

    private DefaultSeries() {}
}
