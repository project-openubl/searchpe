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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataHelperReadLineTest {

    @Test
    public void readLineSplitsColumnsAndTurnsPlaceholdersIntoNull() {
        String line = "20100110513|COMPAÑIA MINERA MILPO S.A.A.|ACTIVO|HABIDO|150130|AV.|SN.BORJA NORTE|-|-|523|-|-|-|-|-|";

        String[] columns = DataHelper.readLine(line, 15);

        assertArrayEquals(new String[]{
                "20100110513", "COMPAÑIA MINERA MILPO S.A.A.", "ACTIVO", "HABIDO", "150130", "AV.", "SN.BORJA NORTE",
                null, null, "523", null, null, null, null, null
        }, columns);
    }

    @Test
    public void readLineTrimsValuesAndTreatsBlankOnesAsNull() {
        assertArrayEquals(new String[]{"a", null, null, "b c"}, DataHelper.readLine("  a ||   |  b c  ", 4));
    }

    @Test
    public void readLineFillsMissingColumnsWithNullAndIgnoresExtraOnes() {
        assertArrayEquals(new String[]{"a", "b", null}, DataHelper.readLine("a|b", 3));
        assertArrayEquals(new String[]{"a", "b"}, DataHelper.readLine("a|b|c|d", 2));
    }

    @Test
    public void readLineRestoresEnyeAndRemovesQuotes() {
        String line = "PE?A|PE\uFFFDA|\"QUOTED\" NAME|\"";

        // Quotes are removed after the null check, so a lone quote becomes "" rather than null, as it always did
        assertArrayEquals(new String[]{"PEÑA", "PEÑA", "QUOTED NAME", ""}, DataHelper.readLine(line, 4));
    }

    @Test
    public void readLineOnlyTreatsALoneDashAsNull() {
        assertArrayEquals(new String[]{null, "--", "-a"}, DataHelper.readLine(" - |--|-a", 3));
    }

    @Test
    public void readLineOfAnEmptyLine() {
        assertArrayEquals(new String[]{null, null}, DataHelper.readLine("", 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2010011051", "201001105133", "2010011051A", "\u0662\u0660\u0661\u0660\u0660\u0661\u0661\u0660\u0665\u0661\u0663"})
    public void buildContribuyenteEntityRejectsInvalidRucs(String ruc) {
        assertTrue(DataHelper.buildContribuyenteEntity(1L, DataHelper.readLine(ruc + "|NAME", 15)).isEmpty());
    }

    @Test
    public void buildContribuyenteEntityRejectsLinesWithoutName() {
        assertTrue(DataHelper.buildContribuyenteEntity(1L, DataHelper.readLine("20100110513|-", 15)).isEmpty());
    }

    @Test
    public void buildContribuyenteEntity() {
        assertTrue(DataHelper.buildContribuyenteEntity(1L, DataHelper.readLine("20100110513|NAME", 15)).isPresent());
    }
}
