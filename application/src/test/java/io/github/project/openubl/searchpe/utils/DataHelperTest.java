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

import io.github.project.openubl.searchpe.DefaultProfileManager;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.services.FileService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
public class DataHelperTest {

    @Inject
    FileService fileService;

    @Inject
    Validator validator;

    @Test
    public void testBuildContribuyenteEntity() throws IOException {
        File downloadedFile = fileService.downloadFile();
        File unzippedFolder = fileService.unzip(downloadedFile);
        File txtFile = fileService.getFirstTxtFileFound(unzippedFolder.listFiles());

        try (BufferedReader br = new BufferedReader(new FileReader(txtFile))) {
            String line;
            boolean skip = true;

            while ((line = br.readLine()) != null) {
                // Skip first line
                if (skip) {
                    skip = false;
                    continue;
                }

                String[] columns = DataHelper.readLine(line, 15);
                assertNotNull(columns);
                assertEquals(15, columns.length);

                final String lineToPrint = line;

                Optional<ContribuyenteEntity> contribuyenteOptional = DataHelper.buildContribuyenteEntity(1L, columns);
                contribuyenteOptional.ifPresent(contribuyente -> {
                    Set<ConstraintViolation<ContribuyenteEntity>> violations = validator.validate(contribuyente);
                    assertTrue(violations.isEmpty(), "Line:" + lineToPrint + "\n columns:" + Arrays.toString(columns));
                });
            }
        }
    }
}
