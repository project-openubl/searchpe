package io.github.projectopenubl.xhandler.engine;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;

/** Request and response bodies of the engine API. JSON is snake_case (application.properties). */
public final class EngineModels {

    private EngineModels() {}

    /** SUNAT environment. */
    public enum Environment {
        BETA,
        PRODUCTION;

        @JsonValue
        public String json() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record BuildRequest(JsonNode document) {}

    public record BuildResponse(String xml, JsonNode enrichedJson) {}

    /** P0 signs only with SUNAT's beta demo key, which the engine requires to be explicit. */
    public record SignRequest(String xml, boolean demoKey) {}

    public record SignResponse(String xml, String digestValue) {}

    public record SendRequest(String xml, Environment environment) {}

    /**
     * SUNAT's answer. {@code status}: accepted, rejected, exception, in_progress, unknown (SUNAT may
     * have the document: ask before resending) or not_delivered (safe to resend). DESIGN.md §9.2
     * decides what each one means. {@code code} is SUNAT's numeric code.
     */
    public record SendResponse(
            String status,
            Integer code,
            String description,
            List<String> notes,
            String cdrZip,
            String ticket) {}

    /**
     * Error body of the engine. {@code kind}: invalid_document, invalid_certificate,
     * wrong_password, invalid_request, other.
     */
    public record ErrorBody(String kind, String message) {}
}
