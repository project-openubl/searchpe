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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataHelperRucTest {

    /**
     * RUCs from SUNAT's padrón reducido. They cover an ordinary check digit and the two cases where
     * {@code 11 - sum mod 11} is 10 or 11, which SUNAT maps to check digits 0 and 1.
     */
    @ParameterizedTest
    @CsvSource({
            "45215942, 10452159428",
            "48273611, 10482736110",
            "48351117, 10483511171",
    })
    public void convertBetweenDniAndRuc(String dni, String ruc) {
        assertEquals(Optional.of(ruc), DataHelper.getRucFromDni(dni));
        assertEquals(Optional.of(dni), DataHelper.getDniFromRuc(ruc));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "123456789", "1234567a", " 12345678"})
    public void getRucFromDniRejectsInvalidDni(String dni) {
        assertEquals(Optional.empty(), DataHelper.getRucFromDni(dni));
    }
}
