package io.github.projectopenubl.xhandler.ose;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Untrusted ZIPs from OSE clients (DESIGN.md §11.4 step 2, §16): caps on the compressed size, the
 * number of entries, each entry's size, the total, and the compression ratio (zip bombs). Only
 * plain {@code .xml} names, no paths. Sizes are counted while reading, never trusted from headers.
 */
public final class ZipGuard {

    public static final int MAX_ZIP_BYTES = 10 * 1024 * 1024;
    public static final int MAX_ENTRY_BYTES = 5 * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    public static final int MAX_RATIO = 100;

    /** A refused ZIP: SUNAT's code and message for the issuer. */
    public static final class Refused extends RuntimeException {
        final String code;

        Refused(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private ZipGuard() {}

    /** The XML files of a ZIP by name, in order. */
    public static Map<String, byte[]> xmlFiles(byte[] zip, int maxEntries) {
        if (zip == null || zip.length == 0 || zip.length > MAX_ZIP_BYTES) {
            throw new Refused("0155", "El archivo ZIP está vacío o es demasiado grande.");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = in.getNextEntry()) != null) {
                if (++entries > maxEntries + 1) {
                    throw new Refused("0158", "El archivo ZIP contiene demasiados archivos.");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\") || name.contains("..") || !name.toLowerCase().endsWith(".xml")) {
                    throw new Refused("0157", "El archivo ZIP solo debe contener comprobantes XML: " + name.replaceAll("[^\\w.\\-]", "?"));
                }
                byte[] content = in.readNBytes(MAX_ENTRY_BYTES + 1);
                if (content.length > MAX_ENTRY_BYTES) {
                    throw new Refused("0155", "Un comprobante del ZIP es demasiado grande.");
                }
                total += content.length;
                if (total > MAX_TOTAL_BYTES || total > (long) zip.length * MAX_RATIO) {
                    throw new Refused("0155", "El archivo ZIP excede el tamaño permitido al descomprimirse.");
                }
                if (files.put(name, content) != null) {
                    throw new Refused("0157", "El archivo ZIP repite un comprobante.");
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new Refused("0156", "El archivo ZIP está corrupto.");
        }
        if (files.isEmpty()) {
            throw new Refused("0157", "El archivo ZIP no contiene comprobantes.");
        }
        if (files.size() > maxEntries) {
            throw new Refused("0158", "El archivo ZIP contiene demasiados comprobantes.");
        }
        return files;
    }
}
