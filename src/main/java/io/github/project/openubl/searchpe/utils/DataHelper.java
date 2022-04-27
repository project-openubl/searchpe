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

import io.github.project.openubl.searchpe.models.TipoPersona;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteId;

import java.util.ArrayList;
import java.util.List;
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
                            .replaceAll("\\?", "Ñ");
                }
            } else {
                result[i] = null;
            }
        }

        return result;
    }

    public static Optional<List<ContribuyenteEntity>> buildContribuyenteEntity(Long versionId, String[] columns) {
        if (columns[0] == null || columns[1] == null) {
            return Optional.empty();
        }

        List<ContribuyenteEntity> result = new ArrayList<>();

        ContribuyenteEntity personaJuridica = ContribuyenteEntity
                .builder()
                .id(new ContribuyenteId(versionId, columns[0]))
                .tipoPersona(TipoPersona.JURIDICA)
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
        result.add(personaJuridica);

        if (personaJuridica.id.numeroDocumento.startsWith("10")) {
            ContribuyenteEntity personaNatural = personaJuridica.toBuilder()
                    .id(ContribuyenteId.builder()
                            .versionId(versionId)
                            .numeroDocumento(personaJuridica.id.numeroDocumento.substring(2, personaJuridica.id.numeroDocumento.length() - 1)) // Remove first 2 characters and also last character
                            .build()
                    )
                    .tipoPersona(TipoPersona.NATURAL)
                    .build();

            result.add(personaNatural);
        }

        return Optional.of(result);
    }
}
