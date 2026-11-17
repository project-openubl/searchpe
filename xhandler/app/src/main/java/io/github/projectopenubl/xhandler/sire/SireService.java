package io.github.projectopenubl.xhandler.sire;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineException;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.notifications.Notifications;
import io.github.projectopenubl.xhandler.reports.RegisterFormats;
import io.github.projectopenubl.xhandler.reports.RegisterRows;
import io.github.projectopenubl.xhandler.secrets.CredentialService;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SIRE for one company register and period (DESIGN.md §11.7): request SUNAT's proposal, reconcile
 * it with the stored documents and receptions, submit the replacement, follow the tickets. SUNAT is
 * called with no transaction open (§10.8); every state change is a compare-and-set on the row, so
 * the polling job and a user's request can't step on each other.
 *
 * <p>States: requested (waiting for the proposal's ticket) → proposal_ready (reconciled) →
 * submitting → submitted (waiting for the replacement's ticket) → accepted; error and credentials
 * (SUNAT refused the API credentials) on the way.
 */
@ApplicationScoped
public class SireService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EntityManager em;
    private final TenantAccess access;
    private final Engine engine;
    private final CredentialService credentials;
    private final FileStore files;
    private final RegisterRows registers;
    private final Notifications notifications;

    public SireService(EntityManager em, TenantAccess access, Engine engine, CredentialService credentials, FileStore files,
            RegisterRows registers, Notifications notifications) {
        this.em = em;
        this.access = access;
        this.engine = engine;
        this.credentials = credentials;
        this.files = files;
        this.registers = registers;
        this.notifications = notifications;
    }

    /** Production, or the fake; SIRE has no beta. */
    static EngineModels.Environment environment(String sunatEnv) {
        return "fake".equals(sunatEnv) ? EngineModels.Environment.FAKE : EngineModels.Environment.PRODUCTION;
    }

    record Period(UUID id, UUID companyId, String sunatEnv, String book, String period, String status, String proposalTicket,
            String replacementTicket, String proposalRef) {}

    private Period load(UUID id) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT id, company_id, sunat_env, book, period, status, proposal_ticket, replacement_ticket, proposal_ref
                        FROM xhandler.sire_period WHERE id = :id""").setParameter("id", id).getSingleResult();
        return new Period((UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], (String) r[6], (String) r[7],
                (String) r[8]);
    }

    private Optional<EngineModels.ApiCredentials> apiCredentials(TenantScope scope, Company company, String purpose) {
        return credentials.read(scope, company, "sire", purpose)
                .map(f -> new EngineModels.ApiCredentials(f.get("client_id"), f.get("client_secret"), f.get("username"), f.get("password")));
    }

    /** Moves a period from one state to another if it is still in the first; false if someone else moved it. */
    private boolean transition(UUID tenantId, UUID id, String from, String to, Map<String, Object> columns) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            StringBuilder sql = new StringBuilder("UPDATE xhandler.sire_period SET status = :to, updated_at = now()");
            for (String column : columns.keySet()) {
                sql.append(", ").append(column).append(" = ")
                        .append("reconciliation".equals(column) ? "cast(:" + column + " AS jsonb)" : ":" + column);
            }
            sql.append(" WHERE id = :id AND status = :from");
            var query = em.createNativeQuery(sql.toString()).setParameter("to", to).setParameter("id", id).setParameter("from", from);
            columns.forEach(query::setParameter);
            return query.executeUpdate() == 1;
        });
    }

    private void notify(UUID tenantId, String kind, String title, String body) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            notifications.publish(tenantId, kind, title, body, null);
        });
    }

    /** Asks SUNAT for the proposal of a period in {@code requested} without a ticket. */
    public void requestProposal(UUID tenantId, UUID id) {
        record Call(Period period, Company company, EngineModels.ApiCredentials creds) {}
        Call call = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            Period p = load(id);
            Company company = Company.findById(p.companyId());
            return new Call(p, company, apiCredentials(scope, company, "sire:" + id).orElse(null));
        });
        Period p = call.period();
        if (!"requested".equals(p.status()) || p.proposalTicket() != null) {
            return;
        }
        if (call.creds() == null) {
            transition(tenantId, id, "requested", "credentials", Map.of("detail", "Registre las credenciales de la API SIRE de la empresa."));
            return;
        }
        EngineModels.SireTicketResponse response;
        try {
            response = engine.sireProposal(new EngineModels.SireProposalRequest(environment(p.sunatEnv()), call.creds(), p.book(), p.period()));
        } catch (EngineException e) {
            transition(tenantId, id, "requested", "requested", Map.of("detail", "El motor no respondió; se reintentará."));
            return;
        }
        switch (response.state()) {
            case "ok" -> transition(tenantId, id, "requested", "requested", Map.of("proposal_ticket", response.ticket(), "detail", ""));
            case "unauthorized" -> {
                transition(tenantId, id, "requested", "credentials", Map.of("detail", "SUNAT rechazó las credenciales de la API SIRE."));
                notify(tenantId, "sire.credentials", "SIRE: credenciales rechazadas",
                        "SUNAT rechazó las credenciales de la API SIRE de " + call.company().razonSocial + ". Actualícelas para continuar.");
            }
            default -> transition(tenantId, id, "requested", "requested",
                    Map.of("detail", "SUNAT no respondió; se reintentará. " + nz(response.description())));
        }
    }

    /** Follows the ticket of a requested proposal or a submitted replacement. */
    public void poll(UUID tenantId, UUID id) {
        record Call(Period period, Company company, EngineModels.ApiCredentials creds) {}
        Call call = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            Period p = load(id);
            Company company = Company.findById(p.companyId());
            return new Call(p, company, apiCredentials(scope, company, "sire:" + id).orElse(null));
        });
        Period p = call.period();
        if ("requested".equals(p.status()) && p.proposalTicket() == null) {
            requestProposal(tenantId, id);
            return;
        }
        String ticket = "requested".equals(p.status()) ? p.proposalTicket() : "submitted".equals(p.status()) ? p.replacementTicket() : null;
        if (ticket == null || call.creds() == null) {
            return;
        }
        EngineModels.SireTicketStatus status;
        try {
            status = engine.sireTicket(new EngineModels.SireTicketRequest(environment(p.sunatEnv()), call.creds(), p.book(), p.period(), ticket));
        } catch (EngineException e) {
            return;
        }
        if ("unauthorized".equals(status.state())) {
            transition(tenantId, id, p.status(), "credentials", Map.of("detail", "SUNAT rechazó las credenciales de la API SIRE."));
            return;
        }
        if (!"ok".equals(status.state()) || "pending".equals(status.ticketState())) {
            transition(tenantId, id, p.status(), p.status(), Map.of());
            return;
        }
        if ("error".equals(status.ticketState())) {
            transition(tenantId, id, p.status(), "error", Map.of("detail", "SUNAT: " + nz(status.description())));
            notify(tenantId, "sire.error", "SIRE: SUNAT observó el registro",
                    "El registro " + p.book().toUpperCase() + " " + p.period() + " de " + call.company().razonSocial + " tiene observaciones: "
                            + nz(status.description()));
            return;
        }
        if ("requested".equals(p.status())) {
            proposalReady(tenantId, p, status);
        } else {
            if (transition(tenantId, id, "submitted", "accepted", Map.of("detail", ""))) {
                notify(tenantId, "sire.accepted", "SIRE: registro aceptado",
                        "SUNAT aceptó el registro " + p.book().toUpperCase() + " " + p.period() + " de " + call.company().razonSocial + ".");
            }
        }
    }

    private void proposalReady(UUID tenantId, Period p, EngineModels.SireTicketStatus status) {
        if (status.files() == null || status.files().isEmpty()) {
            transition(tenantId, p.id(), "requested", "error", Map.of("detail", "SUNAT no devolvió el archivo de la propuesta."));
            return;
        }
        byte[] zip = Base64.getDecoder().decode(status.files().get(0).contentBase64());
        FileStore.Stored stored = files.put(tenantId, zip);
        List<RegisterFormats.ProposalEntry> proposal = RegisterFormats.readProposal(p.book(), zip);
        String reconciliation = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            List<RegisterRows.Row> rows = "rvie".equals(p.book())
                    ? registers.sales(p.companyId(), p.sunatEnv(), p.period())
                    : registers.purchases(p.companyId(), p.period());
            return JSON.writeValueAsString(RegisterFormats.reconcile(p.book(), proposal, rows));
        });
        transition(tenantId, p.id(), "requested", "proposal_ready",
                Map.of("proposal_ref", stored.key(), "reconciliation", reconciliation, "detail", ""));
    }

    /** Builds the replacement from the stored rows and submits it (a period in {@code submitting}). */
    public void replace(UUID tenantId, UUID id) {
        record Call(Period period, Company company, EngineModels.ApiCredentials creds, List<RegisterRows.Row> rows) {}
        Call call = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            Period p = load(id);
            Company company = Company.findById(p.companyId());
            List<RegisterRows.Row> rows = "rvie".equals(p.book())
                    ? registers.sales(p.companyId(), p.sunatEnv(), p.period())
                    : registers.purchases(p.companyId(), p.period());
            return new Call(p, company, apiCredentials(scope, company, "sire:" + id).orElse(null), rows);
        });
        Period p = call.period();
        if (!"submitting".equals(p.status())) {
            return;
        }
        if (call.creds() == null) {
            transition(tenantId, id, "submitting", "credentials", Map.of("detail", "Registre las credenciales de la API SIRE de la empresa."));
            return;
        }
        RegisterFormats.File file = RegisterFormats.sire(p.book(), call.company().ruc, call.company().razonSocial, p.period(), call.rows());
        FileStore.Stored stored = files.put(tenantId, file.content());
        EngineModels.SireTicketResponse response;
        try {
            response = engine.sireReplace(new EngineModels.SireReplaceRequest(environment(p.sunatEnv()), call.creds(), p.book(), p.period(),
                    file.name(), Base64.getEncoder().encodeToString(file.content())));
        } catch (EngineException e) {
            transition(tenantId, id, "submitting", "proposal_ready", Map.of("detail", "El motor no respondió. Intente enviar de nuevo."));
            return;
        }
        switch (response.state()) {
            case "ok" -> transition(tenantId, id, "submitting", "submitted",
                    Map.of("replacement_ticket", response.ticket(), "replacement_ref", stored.key(), "detail", ""));
            case "unauthorized" -> transition(tenantId, id, "submitting", "credentials",
                    Map.of("detail", "SUNAT rechazó las credenciales de la API SIRE."));
            default -> transition(tenantId, id, "submitting", "proposal_ready",
                    Map.of("detail", "SUNAT no recibió el reemplazo; intente de nuevo. " + nz(response.description())));
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
