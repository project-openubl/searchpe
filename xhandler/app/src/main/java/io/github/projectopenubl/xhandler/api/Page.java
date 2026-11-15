package io.github.projectopenubl.xhandler.api;

import java.util.List;

/** A page of a list endpoint ({@code ?limit=&offset=}). */
public record Page<T>(List<T> items, int limit, int offset) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public static int limit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    public static int offset(Integer requested) {
        return requested == null || requested < 0 ? 0 : requested;
    }
}
