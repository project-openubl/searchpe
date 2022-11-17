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

import java.util.Optional;

public class DataHelper {

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
        if (ruc.startsWith("10")) {
            dni = ruc.substring(2, ruc.length() - 1);
        }
        return Optional.ofNullable(dni);
    }
}
