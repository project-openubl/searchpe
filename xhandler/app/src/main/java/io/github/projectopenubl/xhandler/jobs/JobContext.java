package io.github.projectopenubl.xhandler.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * A running job. {@code tenantId} is null for platform jobs. {@link #cancelRequested()} reads the
 * database: call it at safe points only, never after a SUNAT call has started (§10.8 rule 6).
 */
public record JobContext(
        UUID id, UUID tenantId, String name, int attempt, JsonNode args, String correlationId, BooleanSupplier cancelCheck) {

    public boolean cancelRequested() {
        return cancelCheck.getAsBoolean();
    }

    public UUID uuidArg(String field) {
        JsonNode value = args.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("job " + id + " has no " + field);
        }
        return UUID.fromString(value.asText());
    }
}
