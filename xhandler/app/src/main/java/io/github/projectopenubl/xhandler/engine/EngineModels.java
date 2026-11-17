package io.github.projectopenubl.xhandler.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;

/** Request and response bodies of the engine API. JSON is snake_case (application.properties). */
public final class EngineModels {

    private EngineModels() {}

    /** SUNAT environment. {@code FAKE} is the SUNAT fake: the sandbox and CI (DESIGN.md §15.2, §26.2). */
    public enum Environment {
        BETA,
        PRODUCTION,
        FAKE;

        @JsonValue
        public String json() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Environment of(String sunatEnv) {
            return valueOf(sunatEnv.toUpperCase(Locale.ROOT));
        }

        /** SUNAT's consult and validity services exist in production only (and in the fake). */
        public boolean hasConsultServices() {
            return this != BETA;
        }
    }

    public record BuildRequest(JsonNode document) {}

    public record BuildResponse(String xml, JsonNode enrichedJson) {}

    /** One-step signing, only with SUNAT's beta demo key (the engine requires it to be explicit). */
    public record SignRequest(String xml, boolean demoKey) {}

    public record SignPrepareRequest(String xml) {}

    /** {@code dataToSign}: base64 bytes to sign with SHA256withRSA. */
    public record SignPrepareResponse(String dataToSign, String digestValue) {}

    /** {@code signatureValue} and {@code certificate} (DER) in base64. */
    public record SignFinalizeRequest(String xml, String signatureValue, String certificate) {}

    public record SignResponse(String xml, String digestValue) {}

    /** SOL user (and the GRE API client). Omitted in beta and the fake: SUNAT's public user. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Credentials(String username, String password, String clientId, String clientSecret) {}

    /** Overrides the environment's URLs, e.g. for a third-party OSE (DESIGN.md §3.2). */
    public record Urls(String invoice, String perceptionRetention, String despatch) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SendRequest(String xml, Environment environment, Credentials credentials, Urls urls) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerifyTicketRequest(String xml, String ticket, Environment environment, Credentials credentials, Urls urls) {}

    /**
     * SUNAT's answer. {@code status}: accepted, rejected, exception, in_progress, unknown (SUNAT may
     * have the document: ask before resending) or not_delivered (safe to resend). {@code code} is
     * SUNAT's numeric code and {@code codeClass} its DESIGN.md §9.2.3 class (codes 99–1999 only).
     */
    public record SendResponse(
            String status,
            Integer code,
            String codeClass,
            String description,
            List<String> notes,
            String cdrZip,
            String ticket) {}

    /** A document already sent, for SUNAT's consult and validity services. */
    public record DocumentKey(String ruc, String tipoComprobante, String serie, long numero) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatusCdrRequest(Environment environment, Credentials credentials, DocumentKey document) {}

    /** {@code status}: accepted, rejected, voided, not_found, unknown, not_delivered. */
    public record StatusCdrResponse(String status, String code, String description, String cdrZip) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValidityFileRequest(String xml, Environment environment, Credentials credentials) {}

    /** {@code state}: matches, differs, not_found, unknown, not_delivered. */
    public record ValidityResponse(String state, String code, String description) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValidityRequest(
            Environment environment,
            Credentials credentials,
            DocumentKey document,
            String receptorTipo,
            String receptorNumero,
            String fechaEmision,
            String importeTotal) {}

    public record ValidateRequest(String xml) {}

    public record Finding(String code, String message) {}

    public record ValidateResponse(boolean valid, List<Finding> errors, List<Finding> observations) {}

    public record InspectRequest(String xml) {}

    public record InspectResponse(
            String documentType,
            String tipoComprobante,
            String ruc,
            String serie,
            Long numero,
            String serieNumero,
            String fechaEmision,
            String moneda,
            String total,
            String customerTipo,
            String customerNumero,
            String digestValue,
            boolean signed,
            String sha256,
            Amounts amounts,
            String affectedSerieNumero,
            String affectedTipo) {}

    /** Document-level amounts by tax (catalog 5), decimal strings; null when absent. */
    public record Amounts(
            String gravada, String igv, String exonerada, String inafecta, String exportacion, String gratuita, String ivapBase,
            String ivap, String isc, String icbper, String otros) {}

    // --- SIRE and conformity (§11.7, §11.8) ---

    /** The tenant's SUNAT API client plus its SOL user (RUC + user). */
    public record ApiCredentials(String clientId, String clientSecret, String username, String password) {}

    public record SireProposalRequest(Environment environment, ApiCredentials credentials, String book, String period) {}

    /** {@code state}: ok, unauthorized (fix the credentials), unavailable (retry later). */
    public record SireTicketResponse(String state, String ticket, String description) {}

    public record SireTicketRequest(Environment environment, ApiCredentials credentials, String book, String period, String ticket) {}

    public record SireFile(String name, String contentBase64) {}

    /** {@code ticketState}: pending, done, error. */
    public record SireTicketStatus(String state, String ticketState, String description, List<SireFile> files) {}

    public record SireReplaceRequest(
            Environment environment, ApiCredentials credentials, String book, String period, String fileName, String zipBase64) {}

    public record ConformityRequest(
            Environment environment, ApiCredentials credentials, String ruc, String tipoComprobante, String serie, long numero) {}

    /** {@code conformity}: pending, agreed, objected, silent_agreement, not_found, unknown. */
    public record ConformityResponse(String state, String conformity, String description) {}

    public record CdrNote(String code, String message) {}

    public record CdrRequest(
            String xml, String oseRuc, String responseCode, String description, List<CdrNote> notes, String issuedAt) {}

    public record XmlResponse(String xml) {}

    public record InfoResponse(String engineVersion, String xhandlerRustRev) {}

    /** Error body of the engine. {@code kind}: invalid_document, invalid_request, other. */
    public record ErrorBody(String kind, String message) {}
}
