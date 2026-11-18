package io.github.projectopenubl.xhandler.status;

import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.keys.KeyVault;
import io.github.projectopenubl.xhandler.storage.FileStore;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Platform status (DESIGN.md §15), like Pulp's: versions, online workers per role, database,
 * storage and key vault health, and queue depth. Public (rate limited at the gateway), and free of
 * tenant data and pod names.
 */
@Path("/api/v1/status")
public class StatusResource {

    public record Versions(String service, String engine, String xhandlerRust) {}

    public record Workers(String role, long online) {}

    public record Queue(int priorityClass, long waiting, long unblocked, long running, Instant oldestUnblocked) {}

    /** One SUNAT web service of an environment, as its circuit breaker sees it (DESIGN.md §24). */
    public record SunatEndpoint(
            @Schema(description = "Servicio de SUNAT: invoice (comprobantes y resúmenes), despatch (guías) o perception (percepciones y retenciones).") String service,
            @Schema(description = "closed (responde), open (caído: no se le envía) o half_open (probando si volvió).") String breakerState,
            @Schema(description = "Hasta cuándo queda abierto el disyuntor; luego se prueba de nuevo.") Instant breakerUntil,
            @Schema(description = "Pausado por la plataforma: no se le envía.") boolean paused) {}

    /**
     * SUNAT availability per environment, from the breakers of its services. {@code available}: every
     * service seen so far answers (breaker closed, not paused); an environment nothing was sent to
     * yet has no services and counts as available.
     */
    public record SunatEnvironment(
            @Schema(description = "production, beta o fake.") String environment,
            boolean available,
            List<SunatEndpoint> endpoints) {}

    public record Status(
            Versions versions,
            List<Workers> workers,
            boolean database,
            boolean storage,
            boolean keyVault,
            boolean engine,
            List<Queue> queues,
            List<SunatEnvironment> sunat) {}

    static final List<String> ENVIRONMENTS = List.of("production", "beta", "fake");

    private final EntityManager em;
    private final Engine engine;
    private final FileStore files;
    private final KeyVault vault;
    private final String version;

    public StatusResource(
            EntityManager em,
            Engine engine,
            FileStore files,
            KeyVault vault,
            @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev") String version) {
        this.em = em;
        this.engine = engine;
        this.files = files;
        this.vault = vault;
        this.version = version;
    }

    @Operation(operationId = "getStatus")
    @GET
    @Transactional
    public Status status() {
        @SuppressWarnings("unchecked")
        List<Object[]> workers = em.createNativeQuery(
                        "SELECT role, count(*) FROM xhandler.worker WHERE last_heartbeat > now() - interval '30 seconds' GROUP BY role ORDER BY role")
                .getResultList();
        @SuppressWarnings("unchecked")
        List<Object[]> queues = em.createNativeQuery(
                        "SELECT priority_class, waiting, unblocked, running, oldest_unblocked FROM xhandler.queue_stats() ORDER BY priority_class")
                .getResultList();
        EngineModels.InfoResponse info = null;
        try {
            info = engine.info();
        } catch (RuntimeException e) {
            // Reported as engine: false
        }
        boolean vaultOk;
        try {
            vaultOk = vault.currentMasterKeyId() != null;
        } catch (RuntimeException e) {
            vaultOk = false;
        }
        return new Status(
                new Versions(version, info == null ? null : info.engineVersion(), info == null ? null : info.xhandlerRustRev()),
                workers.stream().map(w -> new Workers((String) w[0], ((Number) w[1]).longValue())).toList(),
                true,
                files.healthy(),
                vaultOk,
                info != null,
                queues.stream().map(q -> new Queue(((Number) q[0]).intValue(), ((Number) q[1]).longValue(), ((Number) q[2]).longValue(),
                        ((Number) q[3]).longValue(), toInstant(q[4]))).toList(),
                sunat());
    }

    /** SUNAT's endpoints only: a third-party OSE ({@code ose:…}) is no platform-wide status. */
    private List<SunatEnvironment> sunat() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, breaker_state, breaker_until, paused FROM xhandler.sunat_endpoint WHERE id NOT LIKE 'ose:%' ORDER BY id""")
                .getResultList();
        return ENVIRONMENTS.stream().map(env -> {
            List<SunatEndpoint> endpoints = rows.stream()
                    .filter(r -> ((String) r[0]).startsWith(env + ":"))
                    .map(r -> new SunatEndpoint(((String) r[0]).substring(env.length() + 1), (String) r[1], toInstant(r[2]), (Boolean) r[3]))
                    .toList();
            boolean available = endpoints.stream().allMatch(e -> "closed".equals(e.breakerState()) && !e.paused());
            return new SunatEnvironment(env, available, endpoints);
        }).toList();
    }

    public static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.time.OffsetDateTime o) {
            return o.toInstant();
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        throw new IllegalArgumentException("not a timestamp: " + value.getClass());
    }
}
