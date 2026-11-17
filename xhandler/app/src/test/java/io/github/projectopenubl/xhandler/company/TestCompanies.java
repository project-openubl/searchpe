package io.github.projectopenubl.xhandler.company;

import io.github.projectopenubl.xhandler.Api;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;

/**
 * Verified companies for tests. They don't count toward the unverified-company limit, so tests can
 * create them on every run against a database that outlives the run.
 */
@ApplicationScoped
public class TestCompanies {

    private final TenantAccess access;

    public TestCompanies(TenantAccess access) {
        this.access = access;
    }

    public UUID verified(UUID tenantId, String sunatEnv) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            Company company = new Company();
            company.id = Ids.newId();
            company.tenantId = tenantId;
            company.ruc = Api.randomRuc();
            company.razonSocial = "Empresa " + company.ruc;
            company.sunatEnv = sunatEnv;
            company.rucVerifiedAt = Instant.now();
            company.persist();
            for (DefaultSeries.Entry entry : DefaultSeries.ENTRIES) {
                Series series = new Series();
                series.id = Ids.newId();
                series.tenantId = tenantId;
                series.companyId = company.id;
                series.sunatEnv = sunatEnv;
                series.tipoComprobante = entry.tipoComprobante();
                series.serie = entry.serie();
                series.isDefault = true;
                series.persist();
            }
            return company.id;
        });
    }
}
