package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Lookup by printed data (DESIGN.md §12): RUC + serie-número + date + total is low-entropy, so it
 * requires a CAPTCHA, is limited per RUC (and per IP at the gateway), and answers only the status,
 * never the files. Production documents only.
 */
@Path("/api/v1/public/lookup")
public class PublicLookupResource {

    public record LookupRequest(
            @NotBlank @Pattern(regexp = "\\d{11}") String ruc,
            @NotBlank @Pattern(regexp = "\\d{2}") String tipoComprobante,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{4}") String serie,
            @NotNull Long numero,
            @NotNull LocalDate fechaEmision,
            @NotNull BigDecimal total,
            @NotBlank String captcha) {}

    public record LookupResult(String status) {}


    private final EntityManager em;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Optional<String> verifyUrl;
    private final Optional<String> secret;
    private final boolean testMode;
    private final int perRucPerHour;

    public PublicLookupResource(EntityManager em,
            @ConfigProperty(name = "xhandler.captcha.verify-url") Optional<String> verifyUrl,
            @ConfigProperty(name = "xhandler.captcha.secret") Optional<String> secret,
            @ConfigProperty(name = "xhandler.captcha.test-mode", defaultValue = "false") boolean testMode,
            @ConfigProperty(name = "xhandler.public-lookup.per-ruc-per-hour", defaultValue = "300") int perRucPerHour) {
        this.perRucPerHour = perRucPerHour;
        this.em = em;
        this.verifyUrl = verifyUrl;
        this.secret = secret;
        this.testMode = testMode;
    }

    @POST
    public LookupResult lookup(@Valid LookupRequest request) {
        if (!captchaValid(request.captcha())) {
            throw new ApiException(403, "captcha_failed", "Verificación CAPTCHA inválida.");
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            int hits = ((Number) em.createNativeQuery("""
                            INSERT INTO xhandler.public_lookup_counter (bucket, key, hits) VALUES (date_trunc('hour', now()), :k, 1)
                            ON CONFLICT (bucket, key) DO UPDATE SET hits = public_lookup_counter.hits + 1 RETURNING hits""")
                    .setParameter("k", "ruc:" + request.ruc()).getSingleResult()).intValue();
            if (hits > perRucPerHour) {
                throw new ApiException(429, "too_many_requests", "Demasiadas consultas para este RUC. Intente más tarde.");
            }
            Object status = em.createNativeQuery("SELECT xhandler.public_document_status(:ruc, :tipo, :serie, :numero, :fecha, :total)")
                    .setParameter("ruc", request.ruc()).setParameter("tipo", request.tipoComprobante()).setParameter("serie", request.serie())
                    .setParameter("numero", request.numero()).setParameter("fecha", request.fechaEmision()).setParameter("total", request.total())
                    .getSingleResult();
            return new LookupResult(status == null ? "no_encontrado" : PublicDocumentResource.publicStatus(status.toString()));
        });
    }

    public record OseLookupRequest(
            @NotBlank @Pattern(regexp = "\\d{11}") String ruc,
            @NotBlank @Pattern(regexp = "\\d{2}") String tipoComprobante,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{4}") String serie,
            @NotNull Long numero,
            @NotBlank String captcha) {}

    /** {@code state}: con_cdr, con_inconsistencia, no_encontrado; {@code checked_at}: when the OSE checked it. */
    public record OseLookupResult(String state, String checkedAt) {}

    /**
     * The OSE's public validity query (RS 242-2018, RS 117-2017 art. 7.15): during the retention
     * period, whether a factura or nota got a CDR-OSE or an inconsistency notice, and for boletas
     * whether and when they were checked. Status only.
     */
    @POST
    @jakarta.ws.rs.Path("/ose")
    public OseLookupResult ose(@Valid OseLookupRequest request) {
        if (!captchaValid(request.captcha())) {
            throw new ApiException(403, "captcha_failed", "Verificación CAPTCHA inválida.");
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            int hits = ((Number) em.createNativeQuery("""
                            INSERT INTO xhandler.public_lookup_counter (bucket, key, hits) VALUES (date_trunc('hour', now()), :k, 1)
                            ON CONFLICT (bucket, key) DO UPDATE SET hits = public_lookup_counter.hits + 1 RETURNING hits""")
                    .setParameter("k", "ose:" + request.ruc()).getSingleResult()).intValue();
            if (hits > perRucPerHour) {
                throw new ApiException(429, "too_many_requests", "Demasiadas consultas para este RUC. Intente más tarde.");
            }
            @SuppressWarnings("unchecked")
            java.util.List<Object[]> rows = em.createNativeQuery("""
                            SELECT result, cdr_issued_at, received_at FROM xhandler.ose_submission
                            WHERE issuer_ruc = :r AND tipo = :t AND serie = :s AND numero = :n
                            ORDER BY (result = 'cdr') DESC, received_at DESC LIMIT 1""")
                    .setParameter("r", request.ruc()).setParameter("t", request.tipoComprobante()).setParameter("s", request.serie())
                    .setParameter("n", request.numero()).getResultList();
            if (rows.isEmpty()) {
                return new OseLookupResult("no_encontrado", null);
            }
            Object[] r = rows.get(0);
            boolean cdr = "cdr".equals(r[0]);
            return new OseLookupResult(cdr ? "con_cdr" : "con_inconsistencia",
                    String.valueOf(io.github.projectopenubl.xhandler.status.StatusResource.toInstant(cdr ? r[1] : r[2])));
        });
    }

    /** hCaptcha/Turnstile-style verification (POST secret + response, JSON {"success": true}). */
    private boolean captchaValid(String token) {
        if (testMode) {
            return "test-pass".equals(token);
        }
        if (verifyUrl.isEmpty() || secret.isEmpty()) {
            return false;
        }
        try {
            String form = "secret=" + URLEncoder.encode(secret.get(), StandardCharsets.UTF_8) + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(verifyUrl.get())).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().replace(" ", "").contains("\"success\":true");
        } catch (java.io.IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
