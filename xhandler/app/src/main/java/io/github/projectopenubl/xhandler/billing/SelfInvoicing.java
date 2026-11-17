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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

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
        private final TenantAccess access;
        private final Entitlements entitlements;

        public Webhook(@ConfigProperty(name = "xhandler.lago.webhook-secret") Optional<String> secret, JobStore jobs, EntityManager em,
                ObjectMapper json, TenantAccess access, Entitlements entitlements) {
            this.access = access;
            this.entitlements = entitlements;
            this.secret = secret;
            this.jobs = jobs;
            this.em = em;
            this.json = json;
        }

        @APIResponse(responseCode = "204", description = "Evento recibido.")
        @Operation(operationId = "receiveLagoWebhook")
        @POST
        public Response receive(@HeaderParam("X-Lago-Signature") String signature, byte[] body) throws Exception {
            if (secret.isEmpty() || signature == null || !validSignature(secret.get(), body, signature)) {
                throw new ApiException(401, "invalid_signature", "Firma inválida.");
            }
            JsonNode event = json.readTree(body);
            String type = event.path("webhook_type").asText();
            if (type.startsWith("subscription.")) {
                // Plans follow Lago's subscriptions as they change; the periodic sync is the safety net
                JsonNode subscription = event.path("subscription");
                UUID tenant = tenantId(subscription.path("external_customer_id").asText());
                String plan = "terminated".equals(subscription.path("status").asText()) ? "free" : subscription.path("plan_code").asText();
                QuarkusTransaction.requiringNew().run(() -> {
                    access.enterAsPlatform(tenant);
                    if (entitlements.known(plan)) {
                        entitlements.setPlan(tenant, plan, "lago");
                    }
                });
                return Response.noContent().build();
            }
            if (!"invoice.created".equals(type)) {
                return Response.noContent().build();
            }
            JsonNode invoice = event.path("invoice");
            String lagoId = invoice.path("lago_id").asText();
            UUID tenant = tenantId(invoice.path("customer").path("external_id").asText());
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

        static UUID tenantId(String value) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                throw ApiException.invalid("invalid_request", "external_id no es un tenant.", null);
            }
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
                if (tenant == null) {
                    throw new IllegalStateException("tenant " + customer + " of Lago invoice " + lagoId + " doesn't exist");
                }
                return new Buyer(tenant.billingRuc, tenant.billingName != null ? tenant.billingName : tenant.name, tenant.billingEmail);
            });
            // Already issued (a retry after the idempotency window): link it, never issue twice
            Optional<UUID> existing = QuarkusTransaction.requiringNew().call(() -> {
                access.enterAsPlatform(issuer.tenant());
                return io.github.projectopenubl.xhandler.documents.Document.<io.github.projectopenubl.xhandler.documents.Document>find(
                                "companyId = ?1 and function('jsonb_extract_path_text', labels, 'lago_invoice') = ?2", issuer.company(), lagoId)
                        .firstResultOptional().map(d -> d.id);
            });
            if (existing.isPresent()) {
                QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                                "UPDATE xhandler.lago_invoice SET document_id = :d WHERE lago_invoice_id = :id")
                        .setParameter("d", existing.get()).setParameter("id", lagoId).executeUpdate());
                return Map.of("document_id", existing.get().toString(), "already_issued", true);
            }
            Map<String, Object> spec = new HashMap<>();
            boolean factura = buyer.ruc() != null;
            spec.put("tipo_comprobante", factura ? "01" : "03");
            spec.put("moneda", invoice.path("currency").asText("PEN"));
            spec.put("cliente", factura
                    ? Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", buyer.ruc(), "nombre", buyer.name())
                    : Map.of("tipo_documento_identidad", "0", "numero_documento_identidad", "-", "nombre", buyer.name()));
            // Lago amounts are before IGV; the comprobante adds 18%. Coupons go as one net line.
            List<Map<String, Object>> detalles = new ArrayList<>();
            long feesCents = 0;
            for (JsonNode fee : invoice.path("fees")) {
                feesCents += fee.path("amount_cents").asLong();
            }
            long couponsCents = invoice.path("coupons_amount_cents").asLong(0);
            long netCents = feesCents - couponsCents;
            if (netCents <= 0) {
                return Map.of("skipped", "nothing to invoice");
            }
            if (couponsCents == 0) {
                for (JsonNode fee : invoice.path("fees")) {
                    long cents = fee.path("amount_cents").asLong();
                    if (cents > 0) {
                        detalles.add(Map.of("descripcion", fee.path("item").path("name").asText("Suscripción xhandler"), "cantidad", 1,
                                "precio", BigDecimal.valueOf(cents).movePointLeft(2)));
                    }
                }
            } else {
                detalles.add(Map.of("descripcion", "Suscripción xhandler (con descuento)", "cantidad", 1,
                        "precio", BigDecimal.valueOf(netCents).movePointLeft(2)));
            }
            BigDecimal expected = BigDecimal.valueOf(netCents).movePointLeft(2).multiply(new BigDecimal("1.18")).setScale(2, RoundingMode.HALF_UP);
            if (invoice.has("total_amount_cents")) {
                BigDecimal charged = BigDecimal.valueOf(invoice.path("total_amount_cents").asLong()).movePointLeft(2);
                if (expected.subtract(charged).abs().compareTo(new BigDecimal("0.05")) > 0) {
                    // A person must look: the comprobante must match what was charged
                    throw new IllegalStateException("Lago invoice " + lagoId + " charged " + charged + " but its comprobante would total "
                            + expected + " (is the 18% IGV tax set up in Lago?)");
                }
            }
            if (!factura && expected.compareTo(new BigDecimal("700")) > 0) {
                throw new IllegalStateException("tenant " + customer + " has no billing RUC and invoice " + lagoId
                        + " is above S/ 700: a boleta must identify the buyer");
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
