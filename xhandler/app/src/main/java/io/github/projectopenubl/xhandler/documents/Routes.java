package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.company.OseEndpoint;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.secrets.Credential;
import io.github.projectopenubl.xhandler.secrets.CredentialService;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where and with which credentials a document is sent (DESIGN.md §3.2, §11.2), decided when the
 * document is created. The endpoint id groups documents for the reconciler's breakers and caps:
 * {@code <env>:<service>} for SUNAT, {@code ose:<id>} for a third-party OSE. GRE always goes to
 * SUNAT. The submission route is a regulatory status: an OSE-obligated company can't send directly.
 */
@ApplicationScoped
public class Routes {

    /** Where to send, and as whom. Null credentials: the environment's shared user (beta, fake). */
    public record Target(EngineModels.Environment environment, EngineModels.Credentials credentials, EngineModels.Urls urls) {}

    /** The endpoint and credential a new document is sent with. */
    public record Assignment(String endpoint, UUID credentialId) {}

    private final CredentialService credentials;

    public Routes(CredentialService credentials) {
        this.credentials = credentials;
    }

    /** SUNAT service of a document type: billService (invoice), otroscpe (perception) or GRE (despatch). */
    public static String service(String tipoComprobante) {
        return switch (tipoComprobante) {
            case "20", "40", "RR" -> "perception";
            case "09", "31" -> "despatch";
            default -> "invoice";
        };
    }

    public static String endpoint(String sunatEnv, String tipoComprobante) {
        return sunatEnv + ":" + service(tipoComprobante);
    }

    /**
     * The route of a new document. Production requires a credential; beta and the fake fall back to
     * SUNAT's public user. Refuses routes the company may not use.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public Assignment assign(Company company, String sunatEnv, String tipoComprobante) {
        String service = service(tipoComprobante);
        boolean viaOse = "ose".equals(company.submissionRoute) && !"despatch".equals(service);
        if (!viaOse && company.oseObligated && "production".equals(sunatEnv) && !"despatch".equals(service)) {
            throw ApiException.invalid("invalid_route",
                    "La empresa está obligada a emitir a través de un OSE: configure su OSE antes de emitir.", null);
        }
        if (viaOse) {
            OseEndpoint ose = OseEndpoint.of(company.id).orElseThrow(() ->
                    ApiException.invalid("invalid_route", "La empresa usa un OSE pero no tiene uno configurado.", null));
            if ("perception".equals(service) && ose.perceptionUrl == null) {
                throw ApiException.invalid("invalid_route", "El OSE de la empresa no tiene un servicio para percepciones y retenciones.", null);
            }
            UUID credential = credentialId(company, "ose").orElseThrow(() ->
                    ApiException.invalid("no_credentials", "Registre las credenciales del OSE de la empresa.", null));
            return new Assignment("ose:" + ose.id + ":" + service, credential);
        }
        // A company's real SOL/GRE credentials go to SUNAT production only, never to beta or the fake
        Optional<UUID> credential = "production".equals(sunatEnv)
                ? credentialId(company, "despatch".equals(service) ? "gre" : "sol")
                : Optional.empty();
        if ("production".equals(sunatEnv) && credential.isEmpty()) {
            throw ApiException.invalid("no_credentials", "despatch".equals(service)
                    ? "La empresa no tiene credenciales GRE (usuario SOL y cliente API) para enviar guías a SUNAT."
                    : "La empresa no tiene credenciales SOL para enviar a SUNAT.", null);
        }
        return new Assignment(endpoint(sunatEnv, tipoComprobante), credential.orElse(null));
    }

    private static Optional<UUID> credentialId(Company company, String kind) {
        return Credential.<Credential>find("companyId = ?1 and kind = ?2", company.id, kind).firstResultOptional().map(c -> c.id);
    }

    /** Reads the secret (audited) and builds the target. Runs with the company's tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Target target(TenantScope scope, Company company, Document document) {
        EngineModels.Environment environment = EngineModels.Environment.of(document.sunatEnv);
        EngineModels.Urls urls = null;
        String kind = document.isGuia() ? "gre" : "sol";
        if (document.endpoint.startsWith("ose:")) {
            kind = "ose";
            UUID oseId = UUID.fromString(document.endpoint.split(":")[1]);
            OseEndpoint ose = OseEndpoint.findById(oseId);
            if (ose == null) {
                throw new MissingCredentials("ose");
            }
            urls = new EngineModels.Urls(ose.invoiceUrl, ose.perceptionUrl, null);
        }
        EngineModels.Credentials secret = null;
        if (document.credentialId != null) {
            String purpose = "send:" + document.id;
            Map<String, String> fields = credentials.read(scope, company, kind, purpose).orElseThrow(() -> new MissingCredentials(
                    document.endpoint.startsWith("ose:") ? "ose" : document.isGuia() ? "gre" : "sol"));
            secret = new EngineModels.Credentials(fields.get("username"), fields.get("password"), fields.get("client_id"),
                    fields.get("client_secret"));
        } else if (environment == EngineModels.Environment.PRODUCTION) {
            throw new MissingCredentials(kind);
        }
        return new Target(environment, secret, urls);
    }

    /** The company has no credential to send this document with. */
    public static class MissingCredentials extends RuntimeException {
        public MissingCredentials(String kind) {
            super("La empresa no tiene credenciales " + kind.toUpperCase(java.util.Locale.ROOT) + " para enviar.");
        }
    }
}
