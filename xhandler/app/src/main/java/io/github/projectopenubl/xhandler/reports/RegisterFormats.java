package io.github.projectopenubl.xhandler.reports;

import io.github.projectopenubl.xhandler.reports.RegisterRows.Row;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The formats of the registers (DESIGN.md §11.9): CSV and Excel for people, SIRE's replacement
 * layout (RVIE annex 3, RCE annex 4) and legacy PLE (14.1 sales, 8.1 purchases) for SUNAT. Plus
 * the reading of SUNAT's SIRE proposal and its reconciliation with the stored rows (§11.7).
 */
public final class RegisterFormats {

    public record File(String name, String contentType, byte[] content) {}

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final List<String> HEADERS = List.of("Fecha de emisión", "Tipo", "Serie", "Número", "Tipo doc. identidad", "Nro. doc. identidad",
            "Nombre o razón social", "Moneda", "Tipo de cambio", "Exportación", "Base gravada", "IGV", "Exonerado", "Inafecto", "ISC",
            "Base IVAP", "IVAP", "ICBPER", "Otros tributos", "Total", "Estado", "Fecha doc. modificado", "Tipo doc. modificado",
            "Serie doc. modificado", "Número doc. modificado");

    /** RVIE replacement columns (SIRE manual, annex 3). */
    static final String RVIE_HEADER = "RUC|Apellidos y Nombres o Razón social|Periodo|CAR SUNAT|Fecha de emisión|Fecha Vcto/Pago|Tipo CP/Doc.|"
            + "Serie del CDP|Nro CP o Doc. Nro Inicial (Rango)|Nro Final (Rango)|Tipo Doc Identidad|Nro Doc Identidad|"
            + "Apellidos Nombres/ Razón Social|Valor Facturado Exportación|BI Gravada|Dscto BI|IGV / IPM|Dscto IGV / IPM|Mto Exonerado|"
            + "Mto Inafecto|ISC|BI Grav IVAP|IVAP|ICBPER|Otros Tributos|Total CP|Moneda|Tipo Cambio|Fecha Emisión Doc Modificado|"
            + "Tipo CP Modificado|Serie CP Modificado|Nro CP Modificado";

    /** RCE replacement columns (annex 4). */
    static final String RCE_HEADER = "RUC|Apellidos y Nombres o Razón social|Periodo|CAR SUNAT|Fecha de emisión|Fecha Vcto/Pago|Tipo CP/Doc.|"
            + "Serie del CDP|Año|Nro CP o Doc. Nro Inicial (Rango)|Nro Final (Rango)|Tipo Doc Identidad|Nro Doc Identidad|"
            + "Apellidos Nombres/ Razón Social|BI Gravado DG|IGV / IPM DG|BI Gravado DGNG|IGV / IPM DGNG|BI Gravado DNG|IGV / IPM DNG|"
            + "Valor Adq. NG|ISC|ICBPER|Otros Trib/ Cargos|Total CP|Moneda|Tipo de Cambio";

    private RegisterFormats() {}

    static String fileBase(String register, String ruc, String period) {
        return ("sales".equals(register) ? "registro-ventas-" : "registro-compras-") + ruc + "-" + period;
    }

    private static List<String> cells(Row r) {
        List<String> c = new ArrayList<>();
        c.add(r.fecha() == null ? "" : r.fecha().toString());
        c.add(r.tipo());
        c.add(r.serie());
        c.add(String.valueOf(r.numero()));
        c.add(nz(r.partyTipo()));
        c.add(nz(r.partyNumero()));
        c.add(nz(r.partyNombre()));
        c.add(r.moneda());
        c.add(r.tipoCambio() == null ? "" : r.tipoCambio().setScale(3, RoundingMode.HALF_UP).toPlainString());
        for (String name : RegisterRows.AMOUNTS) {
            c.add(money(r.amount(name)));
        }
        c.add(money(r.total()));
        c.add(r.voided() ? "anulado" : "válido");
        c.add(r.affectedFecha() == null ? "" : r.affectedFecha().toString());
        c.add(nz(r.affectedTipo()));
        c.add(nz(r.affectedSerie()));
        c.add(r.affectedNumero() == null ? "" : String.valueOf(r.affectedNumero()));
        return c;
    }

    /** UTF-8 with a BOM, so spreadsheet programs read the accents. */
    public static File csv(String register, String ruc, String period, List<Row> rows) {
        StringBuilder out = new StringBuilder("﻿");
        out.append(String.join(",", HEADERS.stream().map(RegisterFormats::quote).toList())).append("\r\n");
        for (Row r : rows) {
            out.append(String.join(",", cells(r).stream().map(RegisterFormats::quote).toList())).append("\r\n");
        }
        return new File(fileBase(register, ruc, period) + ".csv", "text/csv; charset=utf-8", out.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String quote(String value) {
        String v = value == null ? "" : value;
        // Spreadsheet formula injection: a leading =, +, - or @ in text is neutralized
        if (!v.isEmpty() && "=+@\t\r".indexOf(v.charAt(0)) >= 0 || v.startsWith("-") && !v.matches("-?\\d+(\\.\\d+)?")) {
            v = "'" + v;
        }
        return v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("'") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /** A minimal Office Open XML workbook: one sheet, inline strings, numeric amounts. */
    public static File xlsx(String register, String ruc, String period, List<Row> rows) {
        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        appendRow(sheet, 1, HEADERS, List.of());
        int n = 2;
        for (Row r : rows) {
            appendRow(sheet, n++, cells(r), List.of(3, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19));
        }
        sheet.append("</sheetData></worksheet>");
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>");
        parts.put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");
        parts.put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + ("sales".equals(register) ? "Ventas" : "Compras") + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "</Relationships>");
        parts.put("xl/worksheets/sheet1.xml", sheet.toString());
        return new File(fileBase(register, ruc, period) + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", zip(parts));
    }

    private static void appendRow(StringBuilder sheet, int index, List<String> values, List<Integer> numeric) {
        sheet.append("<row r=\"").append(index).append("\">");
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            if (numeric.contains(i) && v.matches("-?\\d+(\\.\\d+)?")) {
                sheet.append("<c t=\"n\"><v>").append(v).append("</v></c>");
            } else {
                sheet.append("<c t=\"inlineStr\"><is><t>").append(xml(v)).append("</t></is></c>");
            }
        }
        sheet.append("</row>");
    }

    /**
     * Legacy PLE (only to adjust periods before SIRE, §3.3): 14.1 Registro de Ventas or 8.1 Registro
     * de Compras. File name: LE + RUC + YYYYMM00 + book + 00 + content indicator + 1 + 1 (PEN) + 1.
     */
    public static File ple(String register, String ruc, String period, List<Row> rows) {
        boolean sales = "sales".equals(register);
        String book = sales ? "140100" : "080100";
        // LE + RUC + YYYYMM00 + book + oportunidad (00) + operaciones (1) + contenido (1/0) + moneda (1 PEN) + PLE (1)
        String name = "LE" + ruc + period + "00" + book + "00" + "1" + (rows.isEmpty() ? "0" : "1") + "11.txt";
        StringBuilder out = new StringBuilder();
        int correlative = 1;
        for (Row original : rows) {
            // PLE amounts are in soles; moneda and tipo de cambio stay as information
            Row r = inSoles(original);
            List<String> f = new ArrayList<>();
            f.add(period + "00");
            f.add(r.serie() + "-" + r.numero());
            f.add("M" + correlative++);
            f.add(dmy(r.fecha()));
            f.add("");
            f.add(r.tipo());
            f.add(r.serie());
            if (sales) {
                f.add(String.valueOf(r.numero()));
                f.add("");
                f.add(nz(r.partyTipo()));
                f.add(nz(r.partyNumero()));
                f.add(nz(r.partyNombre()));
                f.add(money(r.amount("exportacion")));
                f.add(money(r.amount("gravada")));
                f.add("0.00");
                f.add(money(r.amount("igv")));
                f.add("0.00");
                f.add(money(r.amount("exonerada")));
                f.add(money(r.amount("inafecta")));
                f.add(money(r.amount("isc")));
                f.add(money(r.amount("ivap_base")));
                f.add(money(r.amount("ivap")));
                f.add(money(r.amount("icbper")));
                f.add(money(r.amount("otros")));
                f.add(money(r.total()));
                f.add(r.moneda());
                f.add(r.tipoCambio() == null ? "1.000" : r.tipoCambio().setScale(3, RoundingMode.HALF_UP).toPlainString());
                f.add(dmy(r.affectedFecha()));
                f.add(nz(r.affectedTipo()));
                f.add(nz(r.affectedSerie()));
                f.add(r.affectedNumero() == null ? "" : String.valueOf(r.affectedNumero()));
                f.add("");
                f.add("");
                f.add("");
                f.add(r.voided() ? "2" : "1");
            } else {
                f.add("");
                f.add(String.valueOf(r.numero()));
                f.add("");
                f.add(nz(r.partyTipo()));
                f.add(nz(r.partyNumero()));
                f.add(nz(r.partyNombre()));
                f.add(money(r.amount("gravada")));
                f.add(money(r.amount("igv")));
                f.add("0.00");
                f.add("0.00");
                f.add("0.00");
                f.add("0.00");
                f.add(money(r.amount("exonerada").add(r.amount("inafecta"))));
                f.add(money(r.amount("isc")));
                f.add(money(r.amount("icbper")));
                f.add(money(r.amount("otros")));
                f.add(money(r.total()));
                f.add(r.moneda());
                f.add(r.tipoCambio() == null ? "1.000" : r.tipoCambio().setScale(3, RoundingMode.HALF_UP).toPlainString());
                f.add(dmy(r.affectedFecha()));
                f.add(nz(r.affectedTipo()));
                f.add(nz(r.affectedSerie()));
                f.add("");
                f.add(r.affectedNumero() == null ? "" : String.valueOf(r.affectedNumero()));
                for (int i = 0; i < 10; i++) {
                    f.add("");
                }
                f.add("1");
            }
            out.append(String.join("|", f)).append("|\r\n");
        }
        return new File(name, "text/plain; charset=utf-8", out.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Row inSoles(Row r) {
        if ("PEN".equals(r.moneda())) {
            return r;
        }
        if (r.tipoCambio() == null) {
            throw new IllegalStateException("no tipo de cambio for " + r.key() + " in " + r.moneda());
        }
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        r.amounts().forEach((k, v) -> amounts.put(k, v.multiply(r.tipoCambio()).setScale(2, RoundingMode.HALF_UP)));
        return new Row(r.fecha(), r.tipo(), r.serie(), r.numero(), r.partyTipo(), r.partyNumero(), r.partyNombre(), r.moneda(), r.tipoCambio(),
                amounts, r.total().multiply(r.tipoCambio()).setScale(2, RoundingMode.HALF_UP), r.voided(), r.affectedFecha(), r.affectedTipo(),
                r.affectedSerie(), r.affectedNumero(), r.amountsMissing());
    }

    /** SIRE's replacement of the proposal: the TXT in SUNAT's layout, zipped. */
    public static File sire(String book, String ruc, String razonSocial, String period, List<Row> rows) {
        boolean rvie = "rvie".equals(book);
        String base = "LE" + ruc + period + "00" + (rvie ? "140400" : "080400") + "02" + (rvie ? "1112" : "2112");
        StringBuilder out = new StringBuilder(rvie ? RVIE_HEADER : RCE_HEADER);
        for (Row r : rows) {
            List<String> f = new ArrayList<>(List.of(ruc, clean(razonSocial), period, "", dmy(r.fecha()), "", r.tipo(), r.serie()));
            if (rvie) {
                f.addAll(List.of(String.valueOf(r.numero()), "", nz(r.partyTipo()), nz(r.partyNumero()), clean(r.partyNombre()),
                        money(r.amount("exportacion")), money(r.amount("gravada")), "0.00", money(r.amount("igv")), "0.00",
                        money(r.amount("exonerada")), money(r.amount("inafecta")), money(r.amount("isc")), money(r.amount("ivap_base")),
                        money(r.amount("ivap")), money(r.amount("icbper")), money(r.amount("otros")), money(r.total()), r.moneda(),
                        r.tipoCambio() == null ? "" : r.tipoCambio().setScale(3, RoundingMode.HALF_UP).toPlainString(),
                        dmy(r.affectedFecha()), nz(r.affectedTipo()), nz(r.affectedSerie()),
                        r.affectedNumero() == null ? "" : String.valueOf(r.affectedNumero())));
            } else {
                f.addAll(List.of("", String.valueOf(r.numero()), "", nz(r.partyTipo()), nz(r.partyNumero()), clean(r.partyNombre()),
                        money(r.amount("gravada")), money(r.amount("igv")), "0.00", "0.00", "0.00", "0.00",
                        money(r.amount("exonerada").add(r.amount("inafecta"))), money(r.amount("isc")), money(r.amount("icbper")),
                        money(r.amount("otros")), money(r.total()), r.moneda(),
                        r.tipoCambio() == null ? "" : r.tipoCambio().setScale(3, RoundingMode.HALF_UP).toPlainString()));
            }
            out.append('\n').append(String.join("|", f));
        }
        return new File(base + ".zip", "application/zip", zip(Map.of(base + ".txt", out.toString())));
    }

    /** One entry of SUNAT's proposal: the document's key and total. */
    public record ProposalEntry(String key, BigDecimal total) {}

    /** Reads SUNAT's proposal (a ZIP with one TXT in the layouts above, header first). */
    public static List<ProposalEntry> readProposal(String book, byte[] zipBytes) {
        String text = unzipFirst(zipBytes);
        boolean rvie = "rvie".equals(book);
        List<ProposalEntry> entries = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] f = lines[i].split("\\|", -1);
            int minimum = rvie ? 26 : 25;
            if (f.length < minimum) {
                continue;
            }
            String numero = (rvie ? f[8] : f[9]).replaceFirst("^0+(?=\\d)", "");
            String key = rvie ? f[6] + "-" + f[7].toUpperCase() + "-" + numero : f[12] + ":" + f[6] + "-" + f[7].toUpperCase() + "-" + numero;
            String total = rvie ? f[25] : f[24];
            entries.add(new ProposalEntry(key, total.isBlank() ? BigDecimal.ZERO : new BigDecimal(total.strip())));
        }
        return entries;
    }

    /**
     * Proposal vs stored rows: what SUNAT lacks, what it has that isn't stored, and different totals
     * (lists capped at 200 entries each).
     */
    public static Map<String, Object> reconcile(String book, List<ProposalEntry> proposal, List<Row> rows) {
        boolean rvie = "rvie".equals(book);
        Map<String, BigDecimal> sunat = new LinkedHashMap<>();
        for (ProposalEntry e : proposal) {
            sunat.merge(e.key(), e.total(), BigDecimal::add);
        }
        Map<String, BigDecimal> local = new LinkedHashMap<>();
        for (Row r : rows) {
            local.merge(rvie ? r.key() : r.partyNumero() + ":" + r.key(), r.total(), BigDecimal::add);
        }
        List<String> missingInSunat = new ArrayList<>();
        List<Map<String, Object>> differing = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : local.entrySet()) {
            BigDecimal theirs = sunat.get(e.getKey());
            if (theirs == null) {
                add(missingInSunat, e.getKey());
            } else if (theirs.subtract(e.getValue()).abs().compareTo(new BigDecimal("0.01")) > 0 && differing.size() < 200) {
                differing.add(Map.of("key", e.getKey(), "sunat_total", theirs.toPlainString(), "local_total", e.getValue().toPlainString()));
            }
        }
        List<String> missingLocally = new ArrayList<>();
        for (String key : sunat.keySet()) {
            if (!local.containsKey(key)) {
                add(missingLocally, key);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sunat_count", sunat.size());
        result.put("local_count", local.size());
        result.put("matches", missingInSunat.isEmpty() && missingLocally.isEmpty() && differing.isEmpty());
        result.put("missing_in_sunat", missingInSunat);
        result.put("missing_locally", missingLocally);
        result.put("differing", differing);
        return result;
    }

    private static void add(List<String> list, String value) {
        if (list.size() < 200) {
            list.add(value);
        }
    }

    static byte[] zip(Map<String, String> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    static String unzipFirst(byte[] zipBytes) {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    // A proposal is at most a few MB; refuse anything larger rather than read part of it
                    int limit = 32 * 1024 * 1024;
                    byte[] content = zip.readNBytes(limit + 1);
                    if (content.length > limit) {
                        throw new IllegalStateException("the proposal is larger than " + limit + " bytes");
                    }
                    return new String(content, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("unreadable proposal", e);
        }
        return "";
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String dmy(LocalDate date) {
        return date == null ? "" : date.format(DMY);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** Pipes and line breaks would break SUNAT's layouts. */
    private static String clean(String value) {
        return value == null ? "" : value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
