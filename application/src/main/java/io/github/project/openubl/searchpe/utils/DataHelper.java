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

    private static final String PERSONA_NATURAL_RUC_PREFIX = "10";
    public static final String PERSONA_JURIDICA_RUC_PREFIX = "20";
    private static final Pattern DNI_PATTERN = Pattern.compile("\\d{8}");
    private static final int[] RUC_CHECK_DIGIT_WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    public static String[] readLine(String line, int size) {
        String[] result = new String[size];

        String[] split = line.split("\\|");
        for (int i = 0; i < result.length; i++) {
            if (i < split.length) {
                String value = split[i].trim();
                if (value.equals("-") || value.isEmpty()) {
                    result[i] = null;
                } else {
                    result[i] = value
                            .replaceAll("�", "Ñ")
                            .replaceAll("\\?", "Ñ")
                            .replaceAll("\"", "");
                }
            } else {
                result[i] = null;
            }
        }

        return result;
    }

    public static Optional<ContribuyenteEntity> buildContribuyenteEntity(Long versionId, String[] columns) {
        if (columns[0] == null || columns[1] == null || !columns[0].matches("^[0-9]{11}")) {
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
}
