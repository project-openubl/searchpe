package io.github.projectopenubl.xhandler.notifications;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.documents.DeadlineAlerts;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.tenancy.Actor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.Tenant;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant notifications (DESIGN.md §26.6), separate from webhooks: in-app, and emailed to the
 * tenant's notification address, per the tenant's preferences per kind. Written in the transaction
 * of the event; emails go out through a job.
 */
@ApplicationScoped
public class Notifications {

    public static final String DOCUMENT_EXCEPTION = "document.exception";
    public static final String DOCUMENT_AT_RISK = "document.at_risk";
    public static final String REJECTED_AFTER_DELIVERY = "document.rejected_after_delivery";
    public static final String CERTIFICATE_EXPIRING = "certificate.expiring";
    public static final String SUNAT_OUTAGE = "sunat.outage";
    public static final String QUOTA = "quota";
    public static final String PSE_AUTHORIZATION = "pse.authorization_revoked";
    public static final String CONFORMITY_OBJECTED = "conformity.objected";
    public static final String SIRE_DEADLINE = "sire.deadline";

    private final EntityManager em;
    private final Jobs jobs;

    public Notifications(EntityManager em, Jobs jobs) {
        this.em = em;
        this.jobs = jobs;
    }

    /** With the tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void publish(UUID tenantId, String kind, String title, String body, UUID documentId) {
        List<?> preference = em.createNativeQuery("SELECT email, in_app FROM xhandler.notification_preference WHERE kind = :k")
                .setParameter("k", kind).getResultList();
        // Legal notices (data-subject requests) can't be muted
        boolean mandatory = kind.startsWith("privacy.");
        boolean email = mandatory || preference.isEmpty() || Boolean.TRUE.equals(((Object[]) preference.get(0))[0]);
        boolean inApp = mandatory || preference.isEmpty() || Boolean.TRUE.equals(((Object[]) preference.get(0))[1]);
        if (inApp) {
            em.createNativeQuery("""
                            INSERT INTO xhandler.notification (id, tenant_id, kind, title, body, document_id)
                            VALUES (:id, :tenant, :kind, :title, :body, :document)""")
                    .setParameter("id", Ids.newId()).setParameter("tenant", tenantId).setParameter("kind", kind)
                    .setParameter("title", title).setParameter("body", body).setParameter("document", documentId)
                    .executeUpdate();
        }
        Tenant tenant = Tenant.findById(tenantId);
        if (email && tenant != null && tenant.notificationEmail != null) {
            jobs.enqueue(new TenantScope(tenantId, Actor.PLATFORM, Role.PLATFORM), NotificationEmailJob.NAME, 3,
                    Map.of("to", tenant.notificationEmail, "title", title, "body", body), List.of(), null);
        }
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        Document d = change.document();
        if (d.isSummary()) {
            return;
        }
        if ("exception".equals(d.status)) {
            publish(d.tenantId, DOCUMENT_EXCEPTION, "Documento con error: " + d.serieNumero(),
                    "SUNAT no aceptó procesar el documento " + d.serieNumero() + ": " + nz(d.sunatDescription)
                            + ". Corrija la causa y reenvíelo.", d.id);
        } else if ("rejected".equals(d.status) && d.deliveredAt != null) {
            publish(d.tenantId, REJECTED_AFTER_DELIVERY, "Documento rechazado ya entregado: " + d.serieNumero(),
                    "SUNAT rechazó el documento " + d.serieNumero() + ", que ya fue entregado al comprador: " + nz(d.sunatDescription)
                            + ". Avise al comprador.", d.id);
        }
    }

    void onAtRisk(@Observes DeadlineAlerts.DocumentAtRisk risk) {
        Document d = risk.document();
        publish(d.tenantId, DOCUMENT_AT_RISK, "Documento cerca de su plazo: " + d.serieNumero(),
                "El documento " + d.serieNumero() + " aún no tiene respuesta de SUNAT y su plazo legal vence el " + d.deadlineAt
                        + ". Se sigue reintentando.", d.id);
    }

    private static String nz(String value) {
        return value == null ? "sin detalle" : value;
    }
}
