package io.github.projectopenubl.xhandler.tenancy;

import java.util.Locale;

/** Role of an actor in a tenant, from most to least privileged. */
public enum Role {
    /** Platform work without a person (workers, schedules): never granted to a member. */
    PLATFORM,
    OWNER,
    ADMIN,
    OPERATOR,
    VIEWER,
    /** A platform operator with an approved support session: read-only (DESIGN.md §26.1). */
    SUPPORT;

    /** True when this role may do what {@code minimum} allows. SUPPORT only reads. */
    public boolean allows(Role minimum) {
        if (this == SUPPORT) {
            return minimum == VIEWER;
        }
        return ordinal() <= minimum.ordinal();
    }

    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
