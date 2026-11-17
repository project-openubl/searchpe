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
package io.github.project.openubl.searchpe.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersionServiceMinimumRecordsTest {

    @ParameterizedTest
    @CsvSource({
            // activeVersionRecords, minRecordsRatio, expectedMinimum
            "1000, 0.5, 500",
            "1001, 0.5, 501",
            "1000, 0.424, 424",
            // No active version: still at least one record
            "0, 0.5, 1",
            // A ratio of 0 disables the comparison, but never accepts an empty padrón
            "1000, 0, 1",
    })
    public void minimumRecords(int activeVersionRecords, double minRecordsRatio, int expectedMinimum) {
        assertEquals(expectedMinimum, VersionService.minimumRecords(activeVersionRecords, minRecordsRatio));
    }
}
