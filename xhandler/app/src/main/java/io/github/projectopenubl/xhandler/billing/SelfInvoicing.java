package io.github.projectopenubl.xhandler.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.documents.EmitService;
import io.github.projectopenubl.xhandler.email.DocumentEmailJob;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.jobs.JobStore;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.tenancy.Actor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.Tenant;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The platform's own subscription invoices are real comprobantes issued through the service
 * (DESIGN.md §14): Lago's invoices are not. Lago's {@code invoice.created} webhook (HMAC-signed)
 * becomes a factura (or a boleta, without a billing RUC) from the platform's issuing company to the
 * tenant, emailed to its billing address. Idempotent per Lago invoice.
 */
public final class SelfInvoicing {

    public static final String ISSUER_TENANT = "billing.issuer_tenant_id";
    public static final String ISSUER_COMPANY = "billing.issuer_company_id";

    private SelfInvoicing() {}

    /** Lago's webhook endpoint; public (Lago calls it), authenticated by its HMAC signature. */
    @Path("/api/v1/public/lago/webhooks")
    public static class Webhook {

        private final Optional<String> secret;
        private final JobStore jobs;
        private final EntityManager em;
        private final ObjectMapper json;

        public Webhook(@ConfigProperty(name = "xhandler.lago.webhook-secret") Optional<String> secret, JobStore jobs, EntityManager em,
                ObjectMapper json) {
            this.secret = secret;
            this.jobs = jobs;
            this.em = em;
            this.json = json;
        }

        @POST
        public Response receive(@HeaderParam("X-Lago-Signature") String signature, byte[] body) throws Exception {
            if (secret.isEmpty() || signature == null || !validSignature(secret.get(), body, signature)) {
                throw new ApiException(401, "invalid_signature", "Firma inválida.");
            }
            JsonNode event = json.readTree(body);
            if (!"invoice.created".equals(event.path("webhook_type").asText())) {
                return Response.noContent().build();
            }
            JsonNode invoice = event.path("invoice");
            String lagoId = invoice.path("lago_id").asText();
            UUID tenant = UUID.fromString(invoice.path("customer").path("external_id").asText());
            QuarkusTransaction.requiringNew().run(() -> {
                int inserted = em.createNativeQuery(
                                "INSERT INTO xhandler.lago_invoice (lago_invoice_id, tenant_id) VALUES (:id, :t) ON CONFLICT DO NOTHING")
                        .setParameter("id", lagoId).setParameter("t", tenant).executeUpdate();
                if (inserted == 1) {
                    jobs.enqueuePlatform(Job.NAME, 4, Map.of("lago_invoice_id", lagoId, "tenant_id", tenant.toString(),
                            "invoice", invoice.toString()), List.of("lago-invoice:" + lagoId));
                }
            });
            return Response.noContent().build();
        }

        /** Lago's HMAC mode: base64(HMAC-SHA256(body, secret)). */
        static boolean validSignature(String secret, byte[] body, String signature) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = Base64.getEncoder().encode(mac.doFinal(body));
            return MessageDigest.isEqual(expected, signature.strip().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Issues the comprobante for one Lago invoice. */
    @ApplicationScoped
    public static class Job implements JobHandler {

        public static final String NAME = "billing.self-invoice";

        private final EntityManager em;
        private final TenantAccess access;
        private final EmitService emit;
        private final Jobs jobs;
        private final ObjectMapper json;

        public Job(EntityManager em, TenantAccess access, EmitService emit, Jobs jobs, ObjectMapper json) {
            this.em = em;
            this.access = access;
            this.emit = emit;
            this.jobs = jobs;
            this.json = json;
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public Map<String, Object> run(JobContext context) throws Exception {
            String lagoId = context.args().path("lago_invoice_id").asText();
            UUID customer = UUID.fromString(context.args().path("tenant_id").asText());
            JsonNode invoice = json.readTree(context.args().path("invoice").asText());
            record Issuer(UUID tenant, UUID company) {}
            Issuer issuer = QuarkusTransaction.requiringNew().call(() -> {
                Map<String, String> settings = new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("SELECT key, value FROM xhandler.platform_setting WHERE key IN (:a, :b)")
                        .setParameter("a", ISSUER_TENANT).setParameter("b", ISSUER_COMPANY).getResultList();
                rows.forEach(r -> settings.put((String) r[0], (String) r[1]));
                if (!settings.containsKey(ISSUER_TENANT) || !settings.containsKey(ISSUER_COMPANY)) {
                    throw new IllegalStateException("the platform's issuing company is not configured (admin API billing/issuer)");
                }
                return new Issuer(UUID.fromString(settings.get(ISSUER_TENANT)), UUID.fromString(settings.get(ISSUER_COMPANY)));
            });
            record Buyer(String ruc, String name, String email) {}
            Buyer buyer = QuarkusTransaction.requiringNew().call(() -> {
                access.enterAsPlatform(customer);
                Tenant tenant = Tenant.findById(customer);
                return new Buyer(tenant.billingRuc, tenant.billingName != null ? tenant.billingName : tenant.name, tenant.billingEmail);
            });
            Map<String, Object> spec = new HashMap<>();
            boolean factura = buyer.ruc() != null;
            spec.put("tipo_comprobante", factura ? "01" : "03");
            spec.put("moneda", invoice.path("currency").asText("PEN"));
            spec.put("cliente", factura
                    ? Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", buyer.ruc(), "nombre", buyer.name())
                    : Map.of("tipo_documento_identidad", "0", "numero_documento_identidad", "-", "nombre", buyer.name()));
            List<Map<String, Object>> detalles = new ArrayList<>();
            for (JsonNode fee : invoice.path("fees")) {
                BigDecimal amount = BigDecimal.valueOf(fee.path("amount_cents").asLong()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
                if (amount.signum() <= 0) {
                    continue;
                }
                detalles.add(Map.of("descripcion", fee.path("item").path("name").asText("Suscripción xhandler"), "cantidad", 1,
                        "precio", amount));
            }
            if (detalles.isEmpty()) {
                return Map.of("skipped", "nothing to invoice");
            }
            spec.put("detalles", detalles);
            spec.put("observaciones", "Factura Lago " + invoice.path("number").asText(lagoId));
            EmitService.Emitted emitted = emit.emitAsPlatform(issuer.tenant(), issuer.company(), "lago:" + lagoId,
                    new EmitService.EmitRequest(json.valueToTree(Map.of("kind", "Invoice", "spec", spec)), null, false,
                            Map.of("lago_invoice", lagoId, "tenant_id", customer.toString())),
                    context.correlationId());
            UUID documentId = UUID.fromString(emitted.body().path("id").asText());
            QuarkusTransaction.requiringNew().run(() -> {
                em.createNativeQuery("UPDATE xhandler.lago_invoice SET document_id = :d WHERE lago_invoice_id = :id")
                        .setParameter("d", documentId).setParameter("id", lagoId).executeUpdate();
                if (buyer.email() != null) {
                    TenantScope scope = new TenantScope(issuer.tenant(), Actor.PLATFORM, Role.PLATFORM);
                    access.enterAsPlatform(issuer.tenant());
                    jobs.enqueue(scope, DocumentEmailJob.NAME, 3, Map.of("document_id", documentId.toString(), "to", List.of(buyer.email()),
                            "cc", List.of(), "include_cdr", false), List.of(), context.correlationId());
                }
            });
            return Map.of("document_id", documentId.toString(), "serie_numero", emitted.body().path("serie_numero").asText());
        }
    }
}
