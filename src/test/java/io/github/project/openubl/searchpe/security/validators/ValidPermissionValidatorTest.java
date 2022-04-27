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
package io.github.project.openubl.searchpe.security.validators;

import io.github.project.openubl.searchpe.DefaultProfileManager;
import io.github.project.openubl.searchpe.security.Permission;
import io.github.project.openubl.searchpe.security.validators.ValidPermission;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
public class ValidPermissionValidatorTest {

    @Inject
    Validator validator;

    public static class MyBean {
        @Valid
        @ValidPermission
        public Set<String> permissions;
    }

    @Test
    public void allowNullValue() {
        MyBean bean = new MyBean();
        Set<ConstraintViolation<MyBean>> violations = validator.validate(bean);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void validateAllowedPermissions() {
        MyBean bean = new MyBean();
        bean.permissions = new HashSet<>(List.of(Permission.search, Permission.version_write));

        Set<ConstraintViolation<MyBean>> violations = validator.validate(bean);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void validateAllowedPermissionsAndNotAllowedOnes() {
        MyBean bean = new MyBean();
        bean.permissions = new HashSet<>(List.of(Permission.search, Permission.version_write, "someNotAllowedPermission"));

        Set<ConstraintViolation<MyBean>> violations = validator.validate(bean);
        assertFalse(violations.isEmpty());
    }
}
