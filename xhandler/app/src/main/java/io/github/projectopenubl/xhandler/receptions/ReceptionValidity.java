package io.github.projectopenubl.xhandler.receptions;

import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineException;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.secrets.CredentialService;
import io.github.projectopenubl.xhandler.secrets.PlatformCredentials;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks a received document with SUNAT's validity service (validaCDPcriterios, DESIGN.md §11.5):
 * with the company's SOL user in production, or the platform's validity credential. The SUNAT call
 * runs with no transaction open (§10.8). Runs at reception and as a job for unknown states.
 */
@ApplicationScoped
public class ReceptionValidity implements JobHandler {

    private final EntityManager em;
    private final TenantAccess access;
    private final Engine engine;
    private final CredentialService credentials;
    private final PlatformCredentials platformCredentials;

    public ReceptionValidity(EntityManager em, TenantAccess access, Engine engine, CredentialService credentials,
            PlatformCredentials platformCredentials) {
        this.em = em;
        this.access = access;
        this.engine = engine;
        this.credentials = credentials;
        this.platformCredentials = platformCredentials;
    }

    @Override
    public String name() {
        return "receptions.validity";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record Due(UUID tenant, UUID reception) {}
        List<Due> due = QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, reception_id FROM xhandler.receptions_to_check(200)").getResultList();
            return rows.stream().map(r -> new Due((UUID) r[0], (UUID) r[1])).toList();
        });
        int checked = 0;
        for (Due d : due) {
            if (check(d.tenant(), d.reception())) {
                checked++;
            }
        }
        return Map.of("checked", checked, "due", due.size());
    }

    /** True when SUNAT answered definitively (valid, not found, voided, rejected). */
    public boolean check(UUID tenantId, UUID receptionId) {
        record Request(EngineModels.ValidityRequest request) {}
        Request request = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            Object[] r = (Object[]) em.createNativeQuery("""
                            SELECT company_id, supplier_ruc, tipo_comprobante, serie, numero, fecha_emision, total FROM xhandler.reception WHERE id = :id""")
                    .setParameter("id", receptionId).getSingleResult();
            Company company = Company.findById(r[0]);
            Optional<EngineModels.Credentials> sol = credentials.read(scope, company, "sol", "reception-validity:" + receptionId)
                    .map(f -> new EngineModels.Credentials(f.get("username"), f.get("password"), null, null));
            EngineModels.Credentials creds = sol.orElseGet(() -> platformCredentials.pick("validity")
                    .map(id -> platformCredentials.read(id, "validity", "reception-validity:" + receptionId))
                    .map(f -> new EngineModels.Credentials(f.get("username"), f.get("password"), null, null))
                    .orElse(null));
            if (creds == null || r[5] == null || r[6] == null) {
                return null;
            }
            return new Request(new EngineModels.ValidityRequest(EngineModels.Environment.PRODUCTION, creds,
                    new EngineModels.DocumentKey((String) r[1], (String) r[2], (String) r[3], ((Number) r[4]).longValue()), "6", company.ruc,
                    (r[5] instanceof java.time.LocalDate d ? d : ((java.sql.Date) r[5]).toLocalDate()).toString(), ((BigDecimal) r[6]).toPlainString()));
        });
        if (request == null) {
            return false;
        }
        String state;
        try {
            state = engine.validity(request.request()).state();
        } catch (EngineException e) {
            state = "unknown";
        }
        if ("not_delivered".equals(state)) {
            state = "unknown";
        }
        String finalState = state;
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            em.createNativeQuery("UPDATE xhandler.reception SET validity_state = :s, validity_checked_at = now(), updated_at = now() WHERE id = :id")
                    .setParameter("s", finalState).setParameter("id", receptionId).executeUpdate();
        });
        return !"unknown".equals(state);
    }
}
