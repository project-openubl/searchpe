/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.utils;

import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteId;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

public class DataHelper {

    /**
     * Encoding of SUNAT's padrón reducido file. The import also uses it for its intermediate CSV chunks, so whoever
     * writes or reads those chunks must use it too.
     */
    public static final Charset PADRON_CHARSET = StandardCharsets.ISO_8859_1;

    private static final char COLUMN_SEPARATOR = '|';
    private static final char NULL_PLACEHOLDER = '-';
    private static final char ENYE = 'Ñ';
    private static final int RUC_LENGTH = 11;

    private static final String PERSONA_NATURAL_RUC_PREFIX = "10";
    public static final String PERSONA_JURIDICA_RUC_PREFIX = "20";
    private static final Pattern DNI_PATTERN = Pattern.compile("\\d{8}");
    private static final int[] RUC_CHECK_DIGIT_WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    /**
     * Splits a line of the padrón into {@code size} columns. Values are trimmed, {@code -} and empty values become
     * {@code null}, characters SUNAT uses in place of Ñ ({@code ?} and U+FFFD) become Ñ, and double quotes are removed.
     * <p>
     * It runs once per line of a file with millions of them, so it scans the line once instead of using regular
     * expressions.
     */
    public static String[] readLine(String line, int size) {
        String[] result = new String[size];

        int column = 0;
        int columnStart = 0;
        for (int i = 0; i <= line.length() && column < size; i++) {
            // i == line.length() closes the last column
            if (i == line.length() || line.charAt(i) == COLUMN_SEPARATOR) {
                result[column++] = cleanValue(line, columnStart, i);
                columnStart = i + 1;
            }
        }

        return result;
    }

    private static String cleanValue(String line, int start, int end) {
        // Same whitespace rule as String.trim()
        while (start < end && line.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && line.charAt(end - 1) <= ' ') {
            end--;
        }
        if (start == end || (end - start == 1 && line.charAt(start) == NULL_PLACEHOLDER)) {
            return null;
        }

        // Only copy the value when it has characters to fix, which few values do
        StringBuilder fixedValue = null;
        for (int i = start; i < end; i++) {
            char c = line.charAt(i);
            if (isEnyePlaceholder(c) || c == '"') {
                if (fixedValue == null) {
                    fixedValue = new StringBuilder(end - start).append(line, start, i);
                }
                if (isEnyePlaceholder(c)) {
                    fixedValue.append(ENYE);
                }
            } else if (fixedValue != null) {
                fixedValue.append(c);
            }
        }
        return fixedValue != null ? fixedValue.toString() : line.substring(start, end);
    }

    public static Optional<ContribuyenteEntity> buildContribuyenteEntity(Long versionId, String[] columns) {
        if (columns[1] == null || !isRuc(columns[0])) {
            return Optional.empty();
        }

        ContribuyenteEntity result = ContribuyenteEntity
                .builder()
                .id(ContribuyenteId.builder()
                        .versionId(versionId)
                        .ruc(columns[0])
                        .build()
                )
                .dni(getDniFromRuc(columns[0]).orElse(null))
                .nombre(columns[1])
                .estado(columns[2])
                .condicionDomicilio(columns[3])
                .ubigeo(columns[4])
                .tipoVia(columns[5])
                .nombreVia(columns[6])
                .codigoZona(columns[7])
                .tipoZona(columns[8])
                .numero(columns[9])
                .interior(columns[10])
                .lote(columns[11])
                .departamento(columns[12])
                .manzana(columns[13])
                .kilometro(columns[14])
                .build();

        return Optional.of(result);
    }

    public static Optional<String> getDniFromRuc(String ruc) {
        String dni = null;
        if (ruc.startsWith(PERSONA_NATURAL_RUC_PREFIX)) {
            dni = ruc.substring(PERSONA_NATURAL_RUC_PREFIX.length(), ruc.length() - 1);
        }
        return Optional.ofNullable(dni);
    }

    /**
     * Builds the RUC that SUNAT assigns to the natural person holding the given DNI: the {@code 10} prefix, the DNI
     * and a modulo-11 check digit.
     *
     * @param dni an 8-digit DNI
     * @return the RUC, or empty if {@code dni} is not made of exactly 8 digits
     */
    public static Optional<String> getRucFromDni(String dni) {
        if (dni == null || !DNI_PATTERN.matcher(dni).matches()) {
            return Optional.empty();
        }
        String rucWithoutCheckDigit = PERSONA_NATURAL_RUC_PREFIX + dni;
        return Optional.of(rucWithoutCheckDigit + getRucCheckDigit(rucWithoutCheckDigit));
    }

    /**
     * @param rucWithoutCheckDigit the first 10 digits of a RUC, already validated to be ASCII digits
     */
    private static int getRucCheckDigit(String rucWithoutCheckDigit) {
        int sum = 0;
        for (int i = 0; i < RUC_CHECK_DIGIT_WEIGHTS.length; i++) {
            sum += (rucWithoutCheckDigit.charAt(i) - '0') * RUC_CHECK_DIGIT_WEIGHTS[i];
        }
        int checkDigit = 11 - sum % 11;
        return switch (checkDigit) {
            case 10 -> 0;
            case 11 -> 1;
            default -> checkDigit;
        };
    }

    /**
     * U+FFFD can't come from {@link #PADRON_CHARSET}, which maps every byte to a character. It is still handled in case
     * the charset ever changes.
     */
    private static boolean isEnyePlaceholder(char c) {
        return c == '?' || c == '\uFFFD';
    }

    private static boolean isRuc(String value) {
        if (value == null || value.length() != RUC_LENGTH) {
            return false;
        }
        for (int i = 0; i < RUC_LENGTH; i++) {
            if (!isAsciiDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
