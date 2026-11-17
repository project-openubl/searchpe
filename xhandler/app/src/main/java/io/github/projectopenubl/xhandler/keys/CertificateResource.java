package io.github.projectopenubl.xhandler.keys;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.company.CompanyResource;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/** A company's signing certificate (.pfx). The private key goes into the {@link KeyVault}. */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/certificate")
public class CertificateResource {

    public record CertificateView(String subject, String issuer, String serial, String ruc, Instant validFrom, Instant validUntil, boolean rucMatches) {}

    private final TenantAccess access;
    private final KeyVault vault;
    private final Auditor auditor;
    private final jakarta.transaction.TransactionSynchronizationRegistry transactions;

    public CertificateResource(
            TenantAccess access, KeyVault vault, Auditor auditor, jakarta.transaction.TransactionSynchronizationRegistry transactions) {
        this.access = access;
        this.vault = vault;
        this.auditor = auditor;
        this.transactions = transactions;
    }

    /** Uploads a .pfx (multipart: {@code file}, {@code password}), replacing the active key. */
    @Operation(operationId = "uploadCertificate")
    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public CertificateView upload(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("companyId") UUID companyId,
            @RestForm("file") FileUpload file,
            @RestForm("password") String password) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        // Serializes concurrent uploads for the same company
        Company company = CompanyResource.findForUpdate(companyId);
        if (file == null || password == null) {
            throw ApiException.invalid("invalid_request", "Debe enviar el archivo .pfx y su contraseña.", null);
        }

        byte[] pkcs12 = readFile(file);
        char[] secret = password.toCharArray();
        CertificateImporter.Imported imported;
        try {
            imported = CertificateImporter.read(pkcs12, secret, Instant.now());
        } finally {
            Arrays.fill(secret, '\0');
            Arrays.fill(pkcs12, (byte) 0);
        }

        java.util.Optional<SigningKey> previous = SigningKey.active(company.id);
        previous.ifPresent(p -> p.status = "replaced");
        // The previous key must stop being "active" before the new one is inserted (unique index)
        SigningKey.flush();

        SigningKey key = new SigningKey();
        key.id = Ids.newId();
        key.tenantId = tenantId;
        key.companyId = company.id;
        key.keyRef = vault.storePrivateKey(imported.privateKey(), imported.certificate());
        // Keep the vault in step with the database: drop the new key if the transaction rolls
        // back, and the replaced one once it commits
        String newRef = key.keyRef;
        String replacedRef = previous.map(p -> p.keyRef).orElse(null);
        transactions.registerInterposedSynchronization(new jakarta.transaction.Synchronization() {
            @Override
            public void beforeCompletion() {}

            @Override
            public void afterCompletion(int status) {
                if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                    if (replacedRef != null) {
                        vault.deletePrivateKey(replacedRef);
                    }
                } else {
                    vault.deletePrivateKey(newRef);
                }
            }
        });
        try {
            key.certificate = imported.certificate().getEncoded();
        } catch (CertificateEncodingException e) {
            throw ApiException.invalid("invalid_certificate", "El certificado digital no es válido.", e.getMessage());
        }
        key.subject = imported.certificate().getSubjectX500Principal().getName();
        key.issuer = imported.certificate().getIssuerX500Principal().getName();
        key.serial = imported.certificate().getSerialNumber().toString(16);
        key.ruc = imported.ruc().orElse(null);
        key.validFrom = imported.certificate().getNotBefore().toInstant();
        key.validUntil = imported.certificate().getNotAfter().toInstant();
        key.persist();

        // A matching RUC is informative only: anyone can make a self-signed certificate with any
        // RUC. The RUC is verified by a SUNAT SOL login (P3), or later by checking the chain against
        // the CAs SUNAT accepts.
        boolean rucMatches = company.ruc.equals(key.ruc);
        auditor.record(scope, "certificate.imported", "signing_key", key.id,
                Map.of("company_id", company.id.toString(), "serial", key.serial, "ruc_matches", rucMatches));
        return view(key, company);
    }

    @Operation(operationId = "getCertificate")
    @GET
    @Transactional
    public CertificateView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        Company company = CompanyResource.find(companyId);
        return SigningKey.active(company.id).map(k -> view(k, company)).orElseThrow(ApiException::notFound);
    }

    private static CertificateView view(SigningKey k, Company company) {
        return new CertificateView(k.subject, k.issuer, k.serial, k.ruc, k.validFrom, k.validUntil, company.ruc.equals(k.ruc));
    }

    private static byte[] readFile(FileUpload file) {
        try {
            return Files.readAllBytes(file.uploadedFile());
        } catch (IOException e) {
            throw ApiException.invalid("invalid_request", "No se pudo leer el archivo enviado.", null);
        } finally {
            try {
                Files.deleteIfExists(file.uploadedFile());
            } catch (IOException ignored) {
                // The temporary upload is cleaned up by Quarkus as well
            }
        }
    }
}
