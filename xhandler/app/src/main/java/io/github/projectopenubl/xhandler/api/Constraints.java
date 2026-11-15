package io.github.projectopenubl.xhandler.api;

import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;

/** Database constraint violations, e.g. to turn a unique index into a 409. */
public final class Constraints {

    private Constraints() {}

    /** Name of the violated constraint, if {@code e} was caused by one. */
    public static Optional<String> violated(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return Optional.of(cve.getConstraintName());
            }
        }
        return Optional.empty();
    }
}
