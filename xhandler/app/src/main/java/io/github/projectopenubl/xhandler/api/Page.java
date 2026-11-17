package io.github.projectopenubl.xhandler.api;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A page of a list endpoint ({@code ?limit=&offset=}). There is no total count: counting every
 * filtered row on each page would cost as much as the list itself on large tenants. A page with
 * fewer than {@code limit} items is the last one.
 */
@Schema(description = "Una página de resultados (`?limit=&offset=`). No incluye el total: una página con menos de"
        + " `limit` elementos es la última.")
public record Page<T>(
        @Schema(required = true, description = "Los elementos de la página, como mucho `limit`.") List<T> items,
        @Schema(required = true, description = "Tamaño de página aplicado (por defecto 50, máximo 200).") int limit,
        @Schema(required = true, description = "Elementos saltados antes de esta página.") int offset) {

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
