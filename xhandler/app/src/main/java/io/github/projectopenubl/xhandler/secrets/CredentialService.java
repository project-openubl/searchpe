package io.github.projectopenubl.xhandler.secrets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/** Stores and reads company secrets. Every read of a secret is audited. */
@ApplicationScoped
public class CredentialService {

    private final SecretBox box;
    private final Auditor auditor;
    private final ObjectMapper json;

    public CredentialService(SecretBox box, Auditor auditor, ObjectMapper json) {
        this.box = box;
        this.auditor = auditor;
        this.json = json;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public Credential store(TenantScope scope, Company company, String kind, Map<String, String> fields, String label) {
        Credential credential = Credential.<Credential>find("companyId = ?1 and kind = ?2", company.id, kind)
                .firstResultOptional()
                .orElseGet(() -> {
                    Credential c = new Credential();
                    c.id = Ids.newId();
                    c.tenantId = company.tenantId;
                    c.companyId = company.id;
                    c.kind = kind;
                    return c;
                });
        credential.label = label;
        credential.seal(box.seal(toJson(fields)));
        credential.persist();
        auditor.record(scope, "credential.stored", "credential", credential.id, Map.of("kind", kind, "company_id", company.id.toString()));
        return credential;
    }

    /** The decrypted fields of a secret, for sending to SUNAT etc. Audited. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Map<String, String>> read(TenantScope scope, Company company, String kind, String purpose) {
        return Credential.<Credential>find("companyId = ?1 and kind = ?2", company.id, kind)
                .firstResultOptional()
                .map(credential -> {
                    auditor.record(scope, "credential.read", "credential", credential.id, Map.of("kind", kind, "purpose", purpose));
                    return fromJson(box.open(credential.sealed()));
                });
    }

    private byte[] toJson(Map<String, String> fields) {
        try {
            return json.writeValueAsBytes(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> fromJson(byte[] bytes) {
        try {
            return json.readValue(new String(bytes, StandardCharsets.UTF_8), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new ApiException(500, "corrupt_secret", "Error interno al leer una credencial.");
        }
    }
}
