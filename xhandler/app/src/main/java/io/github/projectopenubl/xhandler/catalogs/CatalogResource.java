package io.github.projectopenubl.xhandler.catalogs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Constraints;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.company.CompanyResource;
import io.github.projectopenubl.xhandler.status.StatusResource;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional catalogs of a company (DESIGN.md §17): customers, products, vehicles, drivers and
 * addresses. Documents may reference them or carry everything inline. One table typed by kind; each
 * kind has its required fields and natural key (a customer by its document, a product by its code,
 * a vehicle by its plate). Soft-deleted, sync-ready (§7.2).
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/catalogs/{kind}")
public class CatalogResource {

    public record Entry(UUID id, String kind, String key, JsonNode data, Instant createdAt, Instant updatedAt) {}

    /** Required fields and the fields that make the key, per kind. */
    static final Map<String, List<String>> REQUIRED = Map.of(
            "customer", List.of("tipo_documento_identidad", "numero_documento_identidad", "nombre"),
            "product", List.of("codigo", "descripcion"),
            "vehicle", List.of("placa"),
            "driver", List.of("tipo_documento_identidad", "numero_documento_identidad", "nombre", "licencia"),
            "address", List.of("direccion"));

    static final Map<String, List<String>> KEY = Map.of(
            "customer", List.of("tipo_documento_identidad", "numero_documento_identidad"),
            "product", List.of("codigo"),
            "vehicle", List.of("placa"),
            "driver", List.of("tipo_documento_identidad", "numero_documento_identidad"),
            "address", List.of());

    private final TenantAccess access;
    private final EntityManager em;
    private final ObjectMapper json;
    private final io.github.projectopenubl.xhandler.audit.Auditor auditor;

    public CatalogResource(TenantAccess access, EntityManager em, ObjectMapper json, io.github.projectopenubl.xhandler.audit.Auditor auditor) {
        this.auditor = auditor;
        this.access = access;
        this.em = em;
        this.json = json;
    }

    @GET
    @Transactional
    public Page<Entry> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            @QueryParam("q") String q, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        var scope = access.enter(tenantId, Role.VIEWER);
        check(kind);
        CompanyResource.find(companyId);
        auditPersonalData(scope, kind, companyId, "list");
        int size = Page.limit(limit);
        int from = Page.offset(offset);
        // Searches values people look for (not JSON keys): key, name, description, document, plate
        String filter = q == null || q.isBlank() ? "" : " AND (key ILIKE :q OR data ->> 'nombre' ILIKE :q OR data ->> 'descripcion' ILIKE :q"
                + " OR data ->> 'numero_documento_identidad' ILIKE :q OR data ->> 'placa' ILIKE :q OR data ->> 'direccion' ILIKE :q)";
        var query = em.createNativeQuery("SELECT id, kind, key, CAST(data AS text), created_at, updated_at FROM xhandler.catalog_entry "
                        + "WHERE company_id = :c AND kind = :k AND deleted_at IS NULL" + filter + " ORDER BY key LIMIT :l OFFSET :o")
                .setParameter("c", companyId).setParameter("k", kind).setParameter("l", size).setParameter("o", from);
        if (!filter.isEmpty()) {
            query.setParameter("q", "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return new Page<>(rows.stream().map(this::entry).toList(), size, from);
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Entry get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            @PathParam("id") UUID id) {
        var scope = access.enter(tenantId, Role.VIEWER);
        check(kind);
        Entry entry = find(companyId, kind, id);
        auditPersonalData(scope, kind, companyId, id.toString());
        return entry;
    }

    @POST
    @Transactional
    public Response create(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            ObjectNode data) {
        access.enter(tenantId, Role.OPERATOR);
        check(kind);
        var company = CompanyResource.find(companyId);
        return Response.status(201).entity(upsert(company.tenantId, companyId, kind, data, null)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Entry update(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            @PathParam("id") UUID id, ObjectNode data) {
        access.enter(tenantId, Role.OPERATOR);
        check(kind);
        find(companyId, kind, id);
        return upsert(tenantId, companyId, kind, data, id);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            @PathParam("id") UUID id) {
        access.enter(tenantId, Role.OPERATOR);
        check(kind);
        find(companyId, kind, id);
        em.createNativeQuery("UPDATE xhandler.catalog_entry SET deleted_at = now(), updated_at = now() WHERE id = :id")
                .setParameter("id", id).executeUpdate();
        return Response.noContent().build();
    }

    /** Creates or updates an entry, with the tenant open. Used by the API and the RUC/DNI lookup cache. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Entry upsert(UUID tenantId, UUID companyId, String kind, ObjectNode data, UUID id) {
        if (data == null) {
            throw ApiException.invalid("invalid_request", "Envíe los datos del registro.", null);
        }
        for (String field : REQUIRED.get(kind)) {
            if (!data.hasNonNull(field) || data.get(field).asText().isBlank()) {
                throw ApiException.invalid("invalid_request", "Falta el campo " + field + ".", null);
            }
        }
        String key = KEY.get(kind).isEmpty()
                ? (id != null ? id.toString() : Ids.newId().toString())
                : String.join(":", KEY.get(kind).stream().map(f -> data.get(f).asText().strip().toUpperCase(java.util.Locale.ROOT)).toList());
        UUID entryId = id != null ? id : Ids.newId();
        try {
            if (id == null) {
                em.createNativeQuery("""
                                INSERT INTO xhandler.catalog_entry (id, tenant_id, company_id, kind, key, data, device_id)
                                VALUES (:id, :t, :c, :k, :key, CAST(:data AS jsonb), :device)""")
                        .setParameter("id", entryId).setParameter("t", tenantId).setParameter("c", companyId).setParameter("k", kind)
                        .setParameter("key", key).setParameter("data", data.toString())
                        .setParameter("device", data.hasNonNull("device_id") ? data.get("device_id").asText() : null)
                        .executeUpdate();
            } else {
                em.createNativeQuery("UPDATE xhandler.catalog_entry SET key = :key, data = CAST(:data AS jsonb), updated_at = now() WHERE id = :id")
                        .setParameter("key", key).setParameter("data", data.toString()).setParameter("id", id).executeUpdate();
            }
            em.flush();
        } catch (PersistenceException e) {
            if (Constraints.violated(e).isPresent()) {
                throw ApiException.conflict("catalog_entry_exists", "Ya existe un registro con esa clave: " + key + ".");
            }
            throw e;
        }
        return find(companyId, kind, entryId);
    }

    /** An entry by its natural key, if present (with the tenant open). */
    @Transactional(Transactional.TxType.MANDATORY)
    public java.util.Optional<Entry> byKey(UUID companyId, String kind, String key) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, kind, key, CAST(data AS text), created_at, updated_at FROM xhandler.catalog_entry "
                        + "WHERE company_id = :c AND kind = :k AND key = :key AND deleted_at IS NULL")
                .setParameter("c", companyId).setParameter("k", kind).setParameter("key", key).getResultList();
        return rows.stream().findFirst().map(this::entry);
    }

    private Entry find(UUID companyId, String kind, UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, kind, key, CAST(data AS text), created_at, updated_at FROM xhandler.catalog_entry "
                        + "WHERE id = :id AND company_id = :c AND kind = :k AND deleted_at IS NULL")
                .setParameter("id", id).setParameter("c", companyId).setParameter("k", kind).getResultList();
        return rows.stream().findFirst().map(this::entry).orElseThrow(ApiException::notFound);
    }

    private Entry entry(Object[] r) {
        try {
            return new Entry((UUID) r[0], (String) r[1], (String) r[2], json.readTree((String) r[3]), StatusResource.toInstant(r[4]),
                    StatusResource.toInstant(r[5]));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Customers and drivers are people's data: reads are audited (§3.4). */
    private void auditPersonalData(io.github.projectopenubl.xhandler.tenancy.TenantScope scope, String kind, UUID companyId, String what) {
        if ("customer".equals(kind) || "driver".equals(kind)) {
            auditor.record(scope, "catalog.read", kind, companyId, Map.of("entry", what));
        }
    }

    private static void check(String kind) {
        if (!REQUIRED.containsKey(kind)) {
            throw ApiException.notFound();
        }
    }
}
