package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.secrets.CredentialService;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Optional;

/**
 * Where and with which credentials a document is sent (DESIGN.md §3.2, §11.2). The endpoint id
 * groups documents for the reconciler's breakers and caps: {@code <env>:<service>}.
 */
@ApplicationScoped
public class Routes {

    /** Where to send, and as whom. Null credentials: the environment's shared user (beta, fake). */
    public record Target(EngineModels.Environment environment, EngineModels.Credentials credentials, EngineModels.Urls urls) {}

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
     * The credential to send with, decided when the document is created (credential slots key on
     * it). Production requires one; beta and the fake fall back to SUNAT's public user.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<java.util.UUID> credentialFor(Company company, String tipoComprobante) {
        String kind = "despatch".equals(service(tipoComprobante)) ? "gre" : "sol";
        return io.github.projectopenubl.xhandler.secrets.Credential.<io.github.projectopenubl.xhandler.secrets.Credential>find(
                        "companyId = ?1 and kind = ?2", company.id, kind)
                .firstResultOptional()
                .map(c -> c.id);
    }

    /** Reads the secret (audited) and builds the target. Runs with the company's tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Target target(TenantScope scope, Company company, Document document) {
        EngineModels.Environment environment = EngineModels.Environment.of(document.sunatEnv);
        EngineModels.Credentials sol = null;
        if (document.credentialId != null) {
            String kind = document.isGuia() ? "gre" : "sol";
            Map<String, String> fields = credentials.read(scope, company, kind, "send:" + document.id)
                    .orElseThrow(() -> new MissingCredentials(kind));
            sol = new EngineModels.Credentials(fields.get("username"), fields.get("password"), fields.get("client_id"),
                    fields.get("client_secret"));
        } else if (environment == EngineModels.Environment.PRODUCTION) {
            throw new MissingCredentials(document.isGuia() ? "gre" : "sol");
        }
        return new Target(environment, sol, null);
    }

    /** The company has no credential to send this document with. */
    public static class MissingCredentials extends RuntimeException {
        public MissingCredentials(String kind) {
            super("La empresa no tiene credenciales " + kind.toUpperCase(java.util.Locale.ROOT) + " para enviar a SUNAT.");
        }
    }
}
