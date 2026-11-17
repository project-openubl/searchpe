package io.github.projectopenubl.xhandler.conformity;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineException;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.notifications.Notifications;
import io.github.projectopenubl.xhandler.secrets.CredentialService;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.github.projectopenubl.xhandler.webhooks.WebhookEvents;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Factura negociable conformity (DESIGN.md §11.8): a credit factura accepted by SUNAT opens an
 * 8-day window for the buyer (DU 013-2020). This job polls SUNAT's conformity API with the
 * company's SIRE API credentials, records agreed, objected or silent agreement, and tells the
 * tenant (webhooks {@code document.conformity_*}, notifications), with a reminder 2 days before
 * the window closes. SUNAT is called with no transaction open (§10.8).
 */
@ApplicationScoped
public class ConformityTracker implements JobHandler {

    static final Duration WINDOW = Duration.ofDays(8);
    static final Duration REMIND_BEFORE = Duration.ofDays(2);
    private static final Logger LOG = Logger.getLogger(ConformityTracker.class);

    private final EntityManager em;
    private final TenantAccess access;
    private final Engine engine;
    private final CredentialService credentials;
    private final WebhookEvents webhooks;
    private final Notifications notifications;

    public ConformityTracker(EntityManager em, TenantAccess access, Engine engine, CredentialService credentials, WebhookEvents webhooks,
            Notifications notifications) {
        this.em = em;
        this.access = access;
        this.engine = engine;
        this.credentials = credentials;
        this.webhooks = webhooks;
        this.notifications = notifications;
    }

    /** Inside the transaction that recorded SUNAT's acceptance: no I/O, one insert. SIRE has no beta. */
    void onAccepted(@Observes DocumentStatusChanged change) {
        Document d = change.document();
        if (!"01".equals(d.tipoComprobante) || !"credito".equals(d.paymentTerms) || !Document.ACCEPTED.contains(d.status)
                || Document.ACCEPTED.contains(change.previousStatus()) || "beta".equals(d.sunatEnv)) {
            return;
        }
        Instant now = Instant.now();
        em.createNativeQuery("""
                        INSERT INTO xhandler.conformity (id, tenant_id, company_id, document_id, buyer_ruc, accepted_at, window_ends_at)
                        VALUES (:id, :t, :c, :d, :buyer, :at, :ends) ON CONFLICT (document_id) DO NOTHING""")
                .setParameter("id", Ids.newId()).setParameter("t", d.tenantId).setParameter("c", d.companyId).setParameter("d", d.id)
                .setParameter("buyer", d.customerNumero).setParameter("at", now).setParameter("ends", now.plus(WINDOW)).executeUpdate();
    }

    @Override
    public String name() {
        return "conformity.poll";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record Due(UUID tenant, UUID id) {}
        List<Due> due = QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, conformity_id FROM xhandler.conformities_to_check(100)").getResultList();
            return rows.stream().map(r -> new Due((UUID) r[0], (UUID) r[1])).toList();
        });
        int resolved = 0;
        for (Due d : due) {
            try {
                if (check(d.tenant(), d.id())) {
                    resolved++;
                }
            } catch (RuntimeException e) {
                LOG.warnf("conformity check %s failed: %s", d.id(), e.toString());
                stamp(d.tenant(), d.id(), "Error al consultar: se reintentará.");
            }
        }
        return Map.of("checked", due.size(), "resolved", resolved);
    }

    /** True when SUNAT gave a final answer. */
    public boolean check(UUID tenantId, UUID conformityId) {
        record Call(UUID documentId, Instant windowEnds, Instant remindedAt, String sunatEnv, EngineModels.ConformityRequest request) {}
        Call call = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            Object[] c = (Object[]) em.createNativeQuery("SELECT document_id, window_ends_at, reminded_at FROM xhandler.conformity WHERE id = :id")
                    .setParameter("id", conformityId).getSingleResult();
            Document doc = Document.findById(c[0]);
            Company company = Company.findById(doc.companyId);
            EngineModels.ConformityRequest request = credentials.read(scope, company, "sire", "conformity:" + doc.id)
                    .map(f -> new EngineModels.ConformityRequest("fake".equals(doc.sunatEnv) ? EngineModels.Environment.FAKE
                            : EngineModels.Environment.PRODUCTION,
                            new EngineModels.ApiCredentials(f.get("client_id"), f.get("client_secret"), f.get("username"), f.get("password")),
                            company.ruc, doc.tipoComprobante, doc.serie, doc.numero))
                    .orElse(null);
            return new Call(doc.id, io.github.projectopenubl.xhandler.status.StatusResource.toInstant(c[1]),
                    io.github.projectopenubl.xhandler.status.StatusResource.toInstant(c[2]), doc.sunatEnv, request);
        });
        remindIfClosing(tenantId, conformityId, call.documentId(), call.windowEnds(), call.remindedAt());
        if (call.request() == null) {
            stamp(tenantId, conformityId, "Registre las credenciales de la API SIRE para consultar la conformidad.");
            return false;
        }
        EngineModels.ConformityResponse response;
        try {
            response = engine.conformity(call.request());
        } catch (EngineException e) {
            stamp(tenantId, conformityId, "El motor no respondió; se reintentará.");
            return false;
        }
        if (!"ok".equals(response.state())) {
            stamp(tenantId, conformityId, "unauthorized".equals(response.state()) ? "SUNAT rechazó las credenciales de la API SIRE."
                    : "SUNAT no respondió; se reintentará.");
            return false;
        }
        String state = switch (response.conformity()) {
            case "agreed" -> "agreed";
            case "objected" -> "objected";
            case "silent_agreement" -> "silent_agreement";
            default -> null;
        };
        if (state == null) {
            stamp(tenantId, conformityId, response.description());
            return false;
        }
        resolve(tenantId, conformityId, call.documentId(), state);
        return true;
    }

    private void resolve(UUID tenantId, UUID conformityId, UUID documentId, String state) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            int changed = em.createNativeQuery("""
                            UPDATE xhandler.conformity SET state = :s, resolved_at = now(), checked_at = now(), check_detail = NULL
                            WHERE id = :id AND state = 'pending'""").setParameter("s", state).setParameter("id", conformityId).executeUpdate();
            if (changed == 0) {
                return;
            }
            Document doc = Document.findById(documentId);
            Map<String, Object> data = new LinkedHashMap<>(WebhookEvents.documentData(doc));
            data.put("conformity", state);
            webhooks.publish(tenantId, "document.conformity_" + state, "xh:document:" + documentId, WebhookEvents.DOCUMENT_SCHEMA, data);
            String text = switch (state) {
                case "agreed" -> "El adquirente dio su conformidad a la factura " + doc.serieNumero() + ".";
                case "objected" -> "El adquirente registró su disconformidad con la factura " + doc.serieNumero()
                        + ". Emita una nota de crédito tipo 13 para corregir el monto o las fechas de pago.";
                default -> "La factura " + doc.serieNumero() + " obtuvo la conformidad presunta (pasaron 8 días sin respuesta del adquirente).";
            };
            notifications.publish(tenantId, "conformity." + state, "Factura negociable " + doc.serieNumero(), text, documentId);
        });
    }

    private void remindIfClosing(UUID tenantId, UUID conformityId, UUID documentId, Instant windowEnds, Instant remindedAt) {
        Instant now = Instant.now();
        if (remindedAt != null || windowEnds == null || now.isBefore(windowEnds.minus(REMIND_BEFORE)) || now.isAfter(windowEnds)) {
            return;
        }
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            int changed = em.createNativeQuery("UPDATE xhandler.conformity SET reminded_at = now() WHERE id = :id AND reminded_at IS NULL")
                    .setParameter("id", conformityId).executeUpdate();
            if (changed == 0) {
                return;
            }
            Document doc = Document.findById(documentId);
            Map<String, Object> data = new LinkedHashMap<>(WebhookEvents.documentData(doc));
            data.put("window_ends_at", windowEnds.toString());
            webhooks.publish(tenantId, "document.conformity_reminder", "xh:document:" + documentId, WebhookEvents.DOCUMENT_SCHEMA, data);
            notifications.publish(tenantId, "conformity.reminder", "Factura negociable " + doc.serieNumero(),
                    "El plazo de conformidad de la factura " + doc.serieNumero() + " vence el " + windowEnds + ".", documentId);
        });
    }

    private void stamp(UUID tenantId, UUID conformityId, String detail) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            em.createNativeQuery("UPDATE xhandler.conformity SET checked_at = now(), check_detail = :d WHERE id = :id")
                    .setParameter("d", detail).setParameter("id", conformityId).executeUpdate();
        });
    }
}
