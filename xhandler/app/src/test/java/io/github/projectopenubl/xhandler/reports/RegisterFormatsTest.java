package io.github.projectopenubl.xhandler.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.reports.RegisterRows.Row;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The register layouts (DESIGN.md §11.9) without a database. */
class RegisterFormatsTest {

    static Row row(String tipo, String moneda, BigDecimal tc, String total, boolean voided) {
        BigDecimal sign = "07".equals(tipo) ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        Map<String, BigDecimal> amounts = voided ? Map.of()
                : Map.of("gravada", new BigDecimal("100.00").multiply(sign), "igv", new BigDecimal("18.00").multiply(sign));
        return new Row(LocalDate.of(2027, 1, 15), tipo, "F001", 7, "6", "20111111111", "=HYPERLINK(\"x\")", moneda, tc, amounts,
                voided ? BigDecimal.ZERO : new BigDecimal(total).multiply(sign), voided, null, null, null, null, false);
    }

    @Test
    void ple_names_amounts_in_soles_and_states() {
        RegisterFormats.File file = RegisterFormats.ple("sales", "20123456789", "202701",
                List.of(row("01", "USD", new BigDecimal("3.750"), "118.00", false), row("07", "PEN", null, "118.00", false),
                        row("01", "PEN", null, "118.00", true)));
        assertEquals("LE2012345678920270100140100001111.txt", file.name());
        String[] lines = new String(file.content(), StandardCharsets.UTF_8).split("\r\n");
        // USD in soles: 100 × 3.75; the currency and rate stay as information
        assertTrue(lines[0].contains("|375.00|") && lines[0].contains("|442.50|USD|3.750|"), lines[0]);
        assertTrue(lines[1].contains("|-100.00|") && lines[1].contains("|-118.00|"), lines[1]);
        assertTrue(lines[2].endsWith("|2|"), "voided is estado 2: " + lines[2]);
        assertTrue(lines[0].endsWith("|1|"));
        // An empty book: operaciones 1, contenido 0
        assertEquals("LE2012345678920270100140100001011.txt", RegisterFormats.ple("sales", "20123456789", "202701", List.of()).name());
        assertThrows(IllegalStateException.class,
                () -> RegisterFormats.ple("sales", "20123456789", "202701", List.of(row("01", "USD", null, "118.00", false))));
    }

    @Test
    void csv_neutralizes_formulas() {
        String csv = new String(RegisterFormats.csv("sales", "20123456789", "202701", List.of(row("01", "PEN", null, "118.00", false)))
                .content(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""), csv);
    }

    @Test
    void reconciliation_and_oversized_proposals() {
        byte[] zip = RegisterFormats.zip(Map.of("LE.txt", RegisterFormats.RVIE_HEADER + "\n"
                + "20123456789|X|202701||15/01/2027||01|F001|00000007||6|20111111111|C|0.00|100.00|0.00|18.00|0.00|0.00|0.00|0.00|0.00|0.00|0.00|0.00|118.00|PEN|||||"));
        List<RegisterFormats.ProposalEntry> proposal = RegisterFormats.readProposal("rvie", zip);
        assertEquals("01-F001-7", proposal.get(0).key());
        Map<String, Object> result = RegisterFormats.reconcile("rvie", proposal, List.of(row("01", "PEN", null, "118.00", false)));
        assertEquals(true, result.get("matches"));
        assertThrows(IllegalStateException.class, () -> RegisterFormats.unzipFirst(RegisterFormats.zip(Map.of("big.txt", "x".repeat(33 * 1024 * 1024)))));
    }
}
