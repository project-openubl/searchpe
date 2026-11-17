package io.github.projectopenubl.xhandler.reports;

import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a report (DESIGN.md §11.9): rows read in one transaction, the file rendered and stored
 * outside it (§10.8), the result recorded in another.
 */
@ApplicationScoped
public class ReportJob implements JobHandler {

    public static final String NAME = "report.generate";

    private final TenantAccess access;
    private final RegisterRows registers;
    private final FileStore files;
    private final EntityManager em;

    public ReportJob(TenantAccess access, RegisterRows registers, FileStore files, EntityManager em) {
        this.access = access;
        this.registers = registers;
        this.files = files;
        this.em = em;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        UUID reportId = context.uuidArg("report_id");
        record Input(String register, String format, String period, String ruc, String razonSocial, List<RegisterRows.Row> rows) {}
        Input input = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(context.tenantId());
            Object[] r = (Object[]) em.createNativeQuery("SELECT company_id, sunat_env, register, format, period FROM xhandler.report WHERE id = :id")
                    .setParameter("id", reportId).getSingleResult();
            Company company = Company.findById(r[0]);
            String register = (String) r[2];
            List<RegisterRows.Row> rows = "sales".equals(register)
                    ? registers.sales(company.id, (String) r[1], (String) r[4])
                    : registers.purchases(company.id, (String) r[4]);
            return new Input(register, (String) r[3], (String) r[4], company.ruc, company.razonSocial, rows);
        });
        RegisterFormats.File file;
        try {
            file = switch (input.format()) {
                case "csv" -> RegisterFormats.csv(input.register(), input.ruc(), input.period(), input.rows());
                case "xlsx" -> RegisterFormats.xlsx(input.register(), input.ruc(), input.period(), input.rows());
                case "ple" -> RegisterFormats.ple(input.register(), input.ruc(), input.period(), input.rows());
                default -> RegisterFormats.sire("sales".equals(input.register()) ? "rvie" : "rce", input.ruc(), input.razonSocial(),
                        input.period(), input.rows());
            };
        } catch (RuntimeException e) {
            org.jboss.logging.Logger.getLogger(ReportJob.class).errorf(e, "report %s failed", reportId);
            finish(context.tenantId(), reportId, "failed", null, null, null, "No se pudo generar el reporte.");
            return Map.of("status", "failed");
        }
        FileStore.Stored stored = files.put(context.tenantId(), file.content());
        finish(context.tenantId(), reportId, "done", stored.key(), file.name(), input.rows().size(), null);
        return Map.of("status", "done", "rows", input.rows().size());
    }

    private void finish(UUID tenantId, UUID reportId, String status, String ref, String name, Integer rows, String detail) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            em.createNativeQuery("""
                            UPDATE xhandler.report SET status = :s, file_ref = :ref, file_name = :name, rows = :rows, detail = :detail, done_at = now()
                            WHERE id = :id""")
                    .setParameter("s", status).setParameter("ref", ref).setParameter("name", name).setParameter("rows", rows)
                    .setParameter("detail", detail).setParameter("id", reportId).executeUpdate();
        });
    }
}
