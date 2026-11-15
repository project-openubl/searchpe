package io.github.projectopenubl.xhandler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Request and response bodies of the engine API. JSON is snake_case (application.properties). */
public final class EngineModels {

    private EngineModels() {}

    /** SUNAT environment. */
    public enum Environment {
        BETA,
        PRODUCTION;

        @com.fasterxml.jackson.annotation.JsonValue
        public String json() {
            return name().toLowerCase();
        }
    }

    public record BuildRequest(JsonNode document) {}

    public record BuildResponse(String xml, JsonNode enrichedJson) {}

    /** Without a PKCS#12 keystore the engine signs with SUNAT's beta demo key (P0 only). */
    public record SignRequest(String xml) {}

    public record SignResponse(String xml, String digestValue) {}

    public record SendRequest(String xml, Environment environment) {}

    /**
     * SUNAT's answer. {@code status} is accepted, rejected, exception, in_progress, unknown or
     * not_delivered (DESIGN.md §9.2 decides what each one means).
     */
    public record SendResponse(
            String status,
            String code,
            String description,
            List<String> notes,
            String cdrZip,
            String ticket) {}

    /** Error body of the engine. {@code kind}: invalid_document, invalid_certificate, wrong_password, other. */
    public record ErrorBody(String kind, String message) {}
}
