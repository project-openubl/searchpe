package io.github.projectopenubl.xhandler.company;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Constraints;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineException;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.keys.SigningKey;
import io.github.projectopenubl.xhandler.secrets.CredentialService;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * A company's settings, its SUNAT environment and its RUC verification (DESIGN.md §6, §8, §26.2).
 *
 * <ul>
 *   <li>Every company starts on the SUNAT fake (sandbox). Beta is opt-in; production needs a
 *       verified RUC, a valid certificate and SOL credentials.
 *   <li>Series are per environment, so switching creates the default series of the new
 *       environment and leaves the old documents as they are (read-only history).
 *   <li>The switch locks the company row: running emits finish first, new ones wait (§8 gate).
 * </ul>
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}")
public class CompanyEnvironmentResource {

    public record Settings(
            @Size(max = 300) String nombreComercial,
            @Email @Size(max = 300) String email,
            Boolean deliveredOnEmitDefault,
            @Size(max = 500) String direccion,
            @Pattern(regexp = "a4|ticket80|ticket58") String pdfFormat,
            Map<String, Object> pdfOptions,
            Boolean emailOnAccept,
            @Size(max = 200) @Pattern(regexp = "[^\\r\\n]*", message = "debe ser una sola línea") String emailSubject,
            @Size(max = 5000) String emailBody,
            @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Enlace https al Libro de Reclamaciones virtual de la empresa, que el portal del comprador muestra; vacío lo quita.")
            @Size(max = 500) String libroReclamacionesUrl) {}

    public record EnvironmentChange(@NotBlank @Pattern(regexp = "fake|beta|production") String sunatEnv) {}

    static final int MAX_FAILURES_PER_HOUR = 5;

    static final Set<String> SOL_ERRORS = Set.of("0101", "0102", "0103", "0104", "0105", "0106", "0110", "0111", "0112", "0113", "0154");

    private final TenantAccess access;
    private final Auditor auditor;
    private final CredentialService credentials;
    private final Engine engine;
    private final io.github.projectopenubl.xhandler.storage.FileStore files;

    public CompanyEnvironmentResource(TenantAccess access, Auditor auditor, CredentialService credentials, Engine engine,
            io.github.projectopenubl.xhandler.storage.FileStore files) {
        this.files = files;
        this.access = access;
        this.auditor = auditor;
        this.credentials = credentials;
        this.engine = engine;
    }

    @Operation(operationId = "updateCompanySettings")
    @PUT
    @Path("/settings")
    @Transactional
    public CompanyResource.CompanyView settings(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Valid Settings settings) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.findForUpdate(companyId);
        if (settings.nombreComercial() != null) {
            company.nombreComercial = settings.nombreComercial().strip();
        }
        if (settings.email() != null) {
            company.email = settings.email().strip();
        }
        if (settings.deliveredOnEmitDefault() != null) {
            company.deliveredOnEmitDefault = settings.deliveredOnEmitDefault();
        }
        if (settings.direccion() != null) {
            company.direccion = settings.direccion().strip();
        }
        if (settings.pdfFormat() != null) {
            company.pdfFormat = settings.pdfFormat();
        }
        if (settings.pdfOptions() != null) {
            Map<String, Object> options = new java.util.HashMap<>(settings.pdfOptions());
            options.values().removeIf(java.util.Objects::isNull);
            company.pdfOptions = Map.copyOf(options);
        }
        if (settings.emailOnAccept() != null) {
            company.emailOnAccept = settings.emailOnAccept();
        }
        if (settings.emailSubject() != null) {
            company.emailSubject = settings.emailSubject().isBlank() ? null : settings.emailSubject();
        }
        if (settings.emailBody() != null) {
            company.emailBody = settings.emailBody().isBlank() ? null : settings.emailBody();
        }
        if (settings.libroReclamacionesUrl() != null) {
            company.libroReclamacionesUrl = libroReclamacionesUrl(settings.libroReclamacionesUrl());
        }
        auditor.record(scope, "company.updated", "company", companyId);
        return CompanyResource.view(company);
    }

    /** The company logo for PDFs and emails: PNG or JPEG, up to 200 KB. */
    @Operation(operationId = "uploadCompanyLogo")
    @PUT
    @Path("/logo")
    @jakarta.ws.rs.Consumes({"image/png", "image/jpeg"})
    @Transactional
    public CompanyResource.CompanyView logo(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, byte[] image) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.findForUpdate(companyId);
        company.logoRef = files.put(company.tenantId, logoImage(image)).key();
        auditor.record(scope, "company.logo_changed", "company", companyId);
        return CompanyResource.view(company);
    }

    /** A logo upload (company or partner): PNG or JPEG, up to 200 KB, re-encoded as PNG. */
    public static byte[] logoImage(byte[] image) {
        boolean png = image != null && image.length > 8 && (image[0] & 0xff) == 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G';
        boolean jpeg = image != null && image.length > 3 && (image[0] & 0xff) == 0xFF && (image[1] & 0xff) == 0xD8;
        if (!png && !jpeg) {
            throw ApiException.invalid("invalid_request", "El logo debe ser una imagen PNG o JPEG.", null);
        }
        if (image.length > 200 * 1024) {
            throw ApiException.invalid("invalid_request", "El logo no debe pasar de 200 KB.", null);
        }
        return reencode(image);
    }

    /**
     * Checks the declared size before decoding (a small file can declare a huge image) and re-encodes
     * it as PNG, so every PDF render decodes a known-small image.
     */
    static byte[] reencode(byte[] image) {
        try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(image))) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw ApiException.invalid("invalid_request", "No se pudo leer el logo.", null);
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) > 2000 || reader.getHeight(0) > 2000) {
                    throw ApiException.invalid("invalid_request", "El logo no debe pasar de 2000 × 2000 píxeles.", null);
                }
                java.awt.image.BufferedImage decoded = reader.read(0);
                var out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(decoded, "png", out);
                return out.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (java.io.IOException e) {
            throw ApiException.invalid("invalid_request", "No se pudo leer el logo.", null);
        }
    }

    @Operation(operationId = "changeCompanyEnvironment")
    @POST
    @Path("/environment")
    @Transactional
    public CompanyResource.CompanyView environment(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Valid EnvironmentChange change) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = Company.findById(companyId, LockModeType.PESSIMISTIC_WRITE);
        if (company == null || company.deletedAt != null) {
            throw ApiException.notFound();
        }
        String target = change.sunatEnv();
        if (target.equals(company.sunatEnv)) {
            return CompanyResource.view(company);
        }
        if ("production".equals(target)) {
            if (company.rucVerifiedAt == null) {
                throw ApiException.conflict("ruc_not_verified", "Verifique el RUC con su usuario SOL antes de pasar a producción.");
            }
            SigningKey key = SigningKey.active(company.id).orElseThrow(() ->
                    ApiException.conflict("no_certificate", "La empresa no tiene un certificado digital."));
            if (key.validUntil.isBefore(Instant.now())) {
                throw ApiException.conflict("certificate_expired", "El certificado digital de la empresa venció.");
            }
            if (io.github.projectopenubl.xhandler.secrets.Credential.count("companyId = ?1 and kind = 'sol'", company.id) == 0) {
                throw ApiException.conflict("no_credentials", "Registre el usuario SOL de la empresa antes de pasar a producción.");
            }
        }
        String previous = company.sunatEnv;
        company.sunatEnv = target;
        for (DefaultSeries.Entry entry : DefaultSeries.ENTRIES) {
            if (Series.count("companyId = ?1 and sunatEnv = ?2 and tipoComprobante = ?3 and serie = ?4", company.id, target,
                    entry.tipoComprobante(), entry.serie()) == 0) {
                Series series = new Series();
                series.id = Ids.newId();
                series.tenantId = company.tenantId;
                series.companyId = company.id;
                series.sunatEnv = target;
                series.tipoComprobante = entry.tipoComprobante();
                series.serie = entry.serie();
                series.isDefault = true;
                series.persist();
            }
        }
        try {
            Company.flush();
        } catch (PersistenceException e) {
            if (Constraints.violated(e).isPresent()) {
                throw ApiException.conflict("ruc_claimed", "El RUC ya está verificado por otra cuenta en ese ambiente.");
            }
            throw e;
        }
        auditor.record(scope, "company.environment_changed", "company", companyId, Map.of("from", previous, "to", target));
        return CompanyResource.view(company);
    }

    /**
     * Verifies RUC ownership with a successful SOL login (§6): the company's SOL user asks SUNAT's
     * consult service about one of the company's own numbers. Any answer other than an
     * authentication error proves the credentials work for that RUC. This claims the RUC.
     */
    @Operation(operationId = "verifyCompany")
    @POST
    @Path("/verify")
    public CompanyResource.CompanyView verify(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        record Check(String ruc, EngineModels.Credentials credentials) {}
        Check check = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.ADMIN);
            Company company = CompanyResource.find(companyId);
            // Not a password-guessing oracle against SUNAT: failures per RUC, across tenants
            Object failures = io.github.projectopenubl.xhandler.company.Company.getEntityManager()
                    .createNativeQuery("SELECT xhandler.recent_failed_verifications(:ruc, interval '1 hour')")
                    .setParameter("ruc", company.ruc)
                    .getSingleResult();
            if (((Number) failures).intValue() >= MAX_FAILURES_PER_HOUR) {
                throw new ApiException(429, "too_many_attempts", "Demasiados intentos fallidos de verificación para este RUC. Intente en una hora.");
            }
            Map<String, String> sol = credentials.read(scope, company, "sol", "ruc-verification").orElseThrow(() ->
                    ApiException.conflict("no_credentials", "Registre el usuario SOL de la empresa para verificar el RUC."));
            String username = sol.getOrDefault("username", "");
            if (!username.startsWith(company.ruc)) {
                throw ApiException.invalid("sol_user_mismatch", "El usuario SOL debe empezar con el RUC de la empresa.", null);
            }
            return new Check(company.ruc, new EngineModels.Credentials(username, sol.get("password"), null, null));
        });
        EngineModels.StatusCdrResponse answer;
        try {
            answer = engine.statusCdr(EngineModels.Environment.PRODUCTION, check.credentials(),
                    new EngineModels.DocumentKey(check.ruc(), "01", "F001", 1));
        } catch (EngineException e) {
            throw new ApiException(503, "sunat_unavailable", "No se pudo consultar a SUNAT. Intente nuevamente.");
        }
        if (answer.code() != null && SOL_ERRORS.contains(answer.code())) {
            QuarkusTransaction.requiringNew().run(() -> auditor.record(access.enter(tenantId, Role.ADMIN),
                    "company.ruc_verification_failed", "company", companyId, Map.of("ruc", check.ruc(), "code", answer.code())));
            throw ApiException.invalid("sol_invalid", "SUNAT rechazó el usuario SOL: " + answer.description(), answer.code());
        }
        if (!Set.of("accepted", "rejected", "voided", "not_found").contains(answer.status())) {
            throw new ApiException(503, "sunat_unavailable", "SUNAT no respondió. Intente nuevamente.");
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.ADMIN);
            Company company = CompanyResource.findForUpdate(companyId);
            if (company.rucVerifiedAt == null) {
                company.rucVerifiedAt = Instant.now();
                try {
                    Company.flush();
                } catch (PersistenceException e) {
                    if (Constraints.violated(e).isPresent()) {
                        throw ApiException.conflict("ruc_claimed", "El RUC ya está verificado por otra cuenta.");
                    }
                    throw e;
                }
                auditor.record(scope, "company.ruc_verified", "company", companyId, Map.of("method", "sol"));
            }
            return CompanyResource.view(company);
        });
    }

    /** An https address shown to buyers (never fetched by the service); blank removes it. */
    static String libroReclamacionesUrl(String given) {
        String url = given.strip();
        if (url.isEmpty()) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
                return uri.toString();
            }
        } catch (java.net.URISyntaxException e) {
            // answered below
        }
        throw ApiException.invalid("invalid_request", "El Libro de Reclamaciones debe ser una dirección https válida.", "libro_reclamaciones_url");
    }
}
