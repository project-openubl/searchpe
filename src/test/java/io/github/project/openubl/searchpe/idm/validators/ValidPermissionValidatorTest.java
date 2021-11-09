package io.github.project.openubl.searchpe.idm.validators;

import io.github.project.openubl.searchpe.StandaloneProfileManager;
import io.github.project.openubl.searchpe.security.Permission;
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
@TestProfile(StandaloneProfileManager.class)
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
