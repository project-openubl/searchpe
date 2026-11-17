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
package io.github.project.openubl.searchpe.models.jpa.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EstadoContribuyenteTest {

    @ParameterizedTest
    @CsvSource({
            "ACTIVO, ACTIVO",
            "activo, ACTIVO",
            "SUSPENSION TEMPORAL, SUSPENSION_TEMPORAL",
            "BAJA DE OFICIO, BAJA_DE_OFICIO",
            "BAJA DEFINITIVA, BAJA_DEFINITIVA",
    })
    public void fromString(String value, EstadoContribuyente expected) {
        assertEquals(Optional.of(expected), EstadoContribuyente.fromString(value));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "UNKNOWN", "BAJA  DEFINITIVA"})
    public void fromStringUnknownValue(String value) {
        assertEquals(Optional.empty(), EstadoContribuyente.fromString(value));
    }
}
