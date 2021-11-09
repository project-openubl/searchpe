package io.github.project.openubl.searchpe.idm.validators;

import io.github.project.openubl.searchpe.security.Permission;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Collection;

public class ValidPermissionValidator implements ConstraintValidator<ValidPermission, Collection<String>> {

    @Override
    public boolean isValid(Collection<String> value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        boolean isValid = value.stream().allMatch(f -> Permission.allPermissions.stream().anyMatch(f::equals));
        if (!isValid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("One or more permissions are not valid")
                    .addConstraintViolation();
        }

        return isValid;
    }

}
