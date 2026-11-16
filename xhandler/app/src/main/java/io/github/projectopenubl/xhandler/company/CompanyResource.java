package io.github.projectopenubl.xhandler.company;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Constraints;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Companies (RUCs) of a tenant and their series. */
@Path("/api/v1/tenants/{tenantId}/companies")
public class CompanyResource {


    public record CreateCompany(
            @NotBlank @Pattern(regexp = "\\d{11}", message = "debe tener 11 dígitos") String ruc,
            @NotBlank @Size(max = 300) String razonSocial,
            @Size(max = 300) String nombreComercial) {}

    public record CompanyView(
            UUID id, String ruc, String razonSocial, String nombreComercial, String sunatEnv,
            String emissionMode, Instant rucVerifiedAt) {}

    public record SeriesView(UUID id, String sunatEnv, String tipoComprobante, String serie, long nextNumber, boolean isDefault, boolean active) {}

    public record CreateSeries(
            @NotBlank @Pattern(regexp = "\\d{2}") String tipoComprobante,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{4}", message = "debe tener 4 caracteres en mayúsculas o dígitos") String serie,
            @Min(1) Long nextNumber) {}

    private final TenantAccess access;
    private final Auditor auditor;
    /**
     * Unverified companies a tenant may have (DESIGN.md §26.8): RUCs are claimed only when verified,
     * and this keeps a free signup from registering RUCs in bulk.
     */
    private final long maxUnverifiedCompanies;

    public CompanyResource(
            TenantAccess access,
            Auditor auditor,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.limits.max-unverified-companies", defaultValue = "3")
                    long maxUnverifiedCompanies) {
        this.access = access;
        this.auditor = auditor;
        this.maxUnverifiedCompanies = maxUnverifiedCompanies;
    }

    @POST
    @Transactional
    public Response create(@PathParam("tenantId") UUID tenantId, @Valid CreateCompany request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        if (Company.count("rucVerifiedAt is null and deletedAt is null") >= maxUnverifiedCompanies) {
            throw ApiException.conflict("limit_reached", "Alcanzó el máximo de empresas sin verificar.");
        }
        Company company = new Company();
        company.id = Ids.newId();
        company.tenantId = tenantId;
        company.ruc = request.ruc();
        company.razonSocial = request.razonSocial().strip();
        company.nombreComercial = request.nombreComercial() == null ? null : request.nombreComercial().strip();
        // Unverified duplicates of a RUC are allowed: the RUC is claimed at verification (P3)
        company.persist();
        for (DefaultSeries.Entry entry : DefaultSeries.ENTRIES) {
            Series series = new Series();
            series.id = Ids.newId();
            series.tenantId = tenantId;
            series.companyId = company.id;
            series.sunatEnv = company.sunatEnv;
            series.tipoComprobante = entry.tipoComprobante();
            series.serie = entry.serie();
            series.isDefault = true;
            series.persist();
        }
        auditor.record(scope, "company.created", "company", company.id, Map.of("ruc", company.ruc));
        return Response.status(201).entity(view(company)).build();
    }

    @GET
    @Transactional
    public Page<CompanyView> list(
            @PathParam("tenantId") UUID tenantId, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        int size = Page.limit(limit);
        int from = Page.offset(offset);
        List<Company> companies = Company.find("deletedAt is null order by createdAt").range(from, from + size - 1).list();
        return new Page<>(companies.stream().map(CompanyResource::view).toList(), size, from);
    }

    @GET
    @Path("/{companyId}")
    @Transactional
    public CompanyView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        return view(find(companyId));
    }

    @GET
    @Path("/{companyId}/series")
    @Transactional
    public List<SeriesView> series(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        Company company = find(companyId);
        List<Series> series = Series.list("companyId = ?1 and sunatEnv = ?2 order by tipoComprobante, serie", company.id, company.sunatEnv);
        return series.stream().map(CompanyResource::view).toList();
    }

    @POST
    @Path("/{companyId}/series")
    @Transactional
    public Response createSeries(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid CreateSeries request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = find(companyId);
        Series series = new Series();
        series.id = Ids.newId();
        series.tenantId = tenantId;
        series.companyId = company.id;
        series.sunatEnv = company.sunatEnv;
        series.tipoComprobante = request.tipoComprobante();
        series.serie = request.serie();
        // Onboarding from another provider: continue its numbering (DESIGN.md §9.2.1, 1033)
        series.nextNumber = request.nextNumber() == null ? 1 : request.nextNumber();
        try {
            series.persistAndFlush();
        } catch (PersistenceException e) {
            if (Constraints.violated(e).isPresent()) {
                throw ApiException.conflict("series_exists", "La serie ya existe.");
            }
            throw e;
        }
        auditor.record(scope, "series.created", "series", series.id,
                Map.of("tipo_comprobante", series.tipoComprobante, "serie", series.serie, "next_number", series.nextNumber));
        return Response.status(201).entity(view(series)).build();
    }

    /** A company of the open tenant (row-level security already limits the query to it). */
    public static Company find(UUID companyId) {
        return check(Company.findById(companyId));
    }

    /** Same as {@link #find}, locking the row until the transaction ends. */
    public static Company findForUpdate(UUID companyId) {
        return check(Company.findById(companyId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
    }

    private static Company check(Company company) {
        if (company == null || company.deletedAt != null) {
            throw ApiException.notFound();
        }
        return company;
    }

    static CompanyView view(Company c) {
        return new CompanyView(c.id, c.ruc, c.razonSocial, c.nombreComercial, c.sunatEnv, c.emissionMode, c.rucVerifiedAt);
    }

    static SeriesView view(Series s) {
        return new SeriesView(s.id, s.sunatEnv, s.tipoComprobante, s.serie, s.nextNumber, s.isDefault, s.active);
    }
}
