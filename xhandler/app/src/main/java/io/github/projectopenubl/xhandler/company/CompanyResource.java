package io.github.projectopenubl.xhandler.company;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Constraints;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.documents.Idempotency;
import io.github.projectopenubl.xhandler.documents.Numbering;
import jakarta.persistence.EntityManager;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Companies (RUCs) of a tenant and their series. */
@Path("/api/v1/tenants/{tenantId}/companies")
public class CompanyResource {


    public record CreateCompany(
            @NotBlank @Pattern(regexp = "\\d{11}", message = "debe tener 11 dígitos") String ruc,
            @NotBlank @Size(max = 300) String razonSocial,
            @Size(max = 300) String nombreComercial) {}

    public record CompanyView(
            UUID id, String ruc, String razonSocial, String nombreComercial, String sunatEnv,
            String emissionMode, Instant rucVerifiedAt, String email, boolean deliveredOnEmitDefault, boolean sendingPaused,
            String direccion, String pdfFormat, boolean emailOnAccept, boolean hasLogo, String submissionRoute,
            String libroReclamacionesUrl,
            @Schema(description = "Asunto del correo al comprador; null usa el predeterminado.") String emailSubject,
            @Schema(description = "Cuerpo del correo al comprador; null usa el predeterminado.") String emailBody) {}

    public record SeriesView(UUID id, String sunatEnv, String tipoComprobante, String serie, long nextNumber, boolean isDefault, boolean active) {}

    public record CreateSeries(
            @NotBlank @Pattern(regexp = "\\d{2}") String tipoComprobante,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{4}", message = "debe tener 4 caracteres en mayúsculas o dígitos") String serie,
            @Min(1) Long nextNumber) {}

    /**
     * {@code nextNumber} only moves forward (numbers are never reused, DESIGN.md §8); {@code active}
     * false deactivates the series, so emits refuse it.
     */
    public record UpdateSeries(@Min(1) @Max(Numbering.MAX_NUMBER) Long nextNumber, Boolean active) {}

    private final TenantAccess access;
    private final Auditor auditor;
    private final Idempotency idempotency;
    private final EntityManager em;
    /**
     * Unverified companies a tenant may have (DESIGN.md §26.8): RUCs are claimed only when verified,
     * and this keeps a free signup from registering RUCs in bulk.
     */
    private final long maxUnverifiedCompanies;

    public CompanyResource(
            TenantAccess access,
            Auditor auditor,
            Idempotency idempotency,
            EntityManager em,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.limits.max-unverified-companies", defaultValue = "3")
                    long maxUnverifiedCompanies) {
        this.access = access;
        this.auditor = auditor;
        this.idempotency = idempotency;
        this.em = em;
        this.maxUnverifiedCompanies = maxUnverifiedCompanies;
    }

    @APIResponse(responseCode = "201", description = "Empresa creada (también la respuesta repetida de una Idempotency-Key ya usada).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompanyView.class)))
    @APIResponse(responseCode = "409", description = "Máximo de empresas sin verificar, o Idempotency-Key en proceso.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createCompany")
    @POST
    public Response create(@PathParam("tenantId") UUID tenantId, @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid CreateCompany request) {
        return idempotency.create(tenantId, Role.ADMIN, idempotencyKey, idempotency.hash("company", tenantId, request), () -> {
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
            return view(company);
        }, view -> view);
    }

    @Operation(operationId = "listCompanies")
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

    @Operation(operationId = "getCompany")
    @GET
    @Path("/{companyId}")
    @Transactional
    public CompanyView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        return view(find(companyId));
    }

    @Operation(operationId = "listSeries")
    @GET
    @Path("/{companyId}/series")
    @Transactional
    public List<SeriesView> series(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        Company company = find(companyId);
        List<Series> series = Series.list("companyId = ?1 and sunatEnv = ?2 order by tipoComprobante, serie", company.id, company.sunatEnv);
        return series.stream().map(CompanyResource::view).toList();
    }

    @APIResponse(responseCode = "201", description = "Serie creada (también la respuesta repetida de una Idempotency-Key ya usada).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SeriesView.class)))
    @APIResponse(responseCode = "409", description = "La serie ya existe, o Idempotency-Key en proceso.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createSeries")
    @POST
    @Path("/{companyId}/series")
    public Response createSeries(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, @Valid CreateSeries request) {
        return idempotency.create(tenantId, Role.ADMIN, idempotencyKey, idempotency.hash("series", companyId, request), () -> {
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
            return view(series);
        }, view -> view);
    }

    /**
     * Moves a series' next number forward or (de)activates it (DESIGN.md §8, §10.8). Each change is
     * a compare-and-set on the series row, which an emit holds locked until it commits: a number is
     * never handed out twice, and once deactivation commits no emit can take the series.
     */
    @APIResponse(responseCode = "200", description = "La serie actualizada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SeriesView.class)))
    @APIResponse(responseCode = "409", description = "El siguiente número retrocedería por debajo de los ya usados.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "updateSeries", summary = "Cambia el siguiente número de una serie, o la activa o desactiva")
    @PUT
    @Path("/{companyId}/series/{seriesId}")
    @Transactional
    public SeriesView updateSeries(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @PathParam("seriesId") UUID seriesId, @Valid UpdateSeries request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        find(companyId);
        if (Series.count("id = ?1 and companyId = ?2", seriesId, companyId) == 0) {
            throw ApiException.notFound();
        }
        Map<String, Object> changes = new java.util.LinkedHashMap<>();
        if (request.nextNumber() != null) {
            // Forward only, against the counter as it is when the row lock is ours
            int moved = em.createNativeQuery("""
                            UPDATE xhandler.series SET next_number = :next, updated_at = now()
                            WHERE id = :id AND next_number <= :next""")
                    .setParameter("next", request.nextNumber()).setParameter("id", seriesId).executeUpdate();
            if (moved == 0) {
                throw ApiException.conflict("series_number_used",
                        "La serie ya usó números hasta " + (current(seriesId).nextNumber - 1) + "; el siguiente número no puede retroceder.");
            }
            changes.put("next_number", request.nextNumber());
        }
        if (request.active() != null) {
            em.createNativeQuery("UPDATE xhandler.series SET active = :active, updated_at = now() WHERE id = :id")
                    .setParameter("active", request.active()).setParameter("id", seriesId).executeUpdate();
            changes.put("active", request.active());
        }
        if (!changes.isEmpty()) {
            auditor.record(scope, request.active() != null && !request.active() ? "series.deactivated" : "series.updated", "series", seriesId,
                    changes);
        }
        return view(current(seriesId));
    }

    /** The series row as committed so far in this transaction (the native updates bypass the session). */
    private Series current(UUID seriesId) {
        Series series = Series.findById(seriesId);
        em.refresh(series);
        return series;
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
        return new CompanyView(c.id, c.ruc, c.razonSocial, c.nombreComercial, c.sunatEnv, c.emissionMode, c.rucVerifiedAt, c.email,
                c.deliveredOnEmitDefault, c.sendingPaused, c.direccion, c.pdfFormat, c.emailOnAccept, c.logoRef != null, c.submissionRoute,
                c.libroReclamacionesUrl, c.emailSubject, c.emailBody);
    }

    static SeriesView view(Series s) {
        return new SeriesView(s.id, s.sunatEnv, s.tipoComprobante, s.serie, s.nextNumber, s.isDefault, s.active);
    }
}
