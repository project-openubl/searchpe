/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-2.0/
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
                .Builder.aContribuyenteEntity()
                .withId(new ContribuyenteId(versionId, columns[0]))
                .withTipoPersona(TipoPersona.JURIDICA)
                .withNombre(columns[1])
                .withEstado(columns[2])
                .withCondicionDomicilio(columns[3])
                .withUbigeo(columns[4])
                .withTipoVia(columns[5])
                .withNombreVia(columns[6])
                .withCodigoZona(columns[7])
                .withTipoZona(columns[8])
                .withNumero(columns[9])
                .withInterior(columns[10])
                .withLote(columns[11])
                .withDepartamento(columns[12])
                .withManzana(columns[13])
                .withKilometro(columns[14])
                .build();
        result.add(personaJuridica);

        if (personaJuridica.id.numeroDocumento.startsWith("10")) {
            ContribuyenteEntity personaNatural = ContribuyenteEntity.fullClone(personaJuridica);

            personaNatural.id.numeroDocumento = personaNatural.id.numeroDocumento.substring(2, personaNatural.id.numeroDocumento.length() - 1); // Remove first 2 characters and also last character
            personaNatural.tipoPersona = TipoPersona.NATURAL;

            result.add(personaNatural);
        }

        return Optional.of(result);
    }
}
