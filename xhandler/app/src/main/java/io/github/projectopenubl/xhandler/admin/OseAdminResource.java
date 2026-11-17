package io.github.projectopenubl.xhandler.admin;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.ose.OseClients;
import io.github.projectopenubl.xhandler.secrets.PlatformCredentials;
import io.github.projectopenubl.xhandler.status.StatusResource;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Running the OSE role (DESIGN.md §11.4): its users (issuers or their PSEs), SUNAT's association
 * and blocked lists, the CDR-OSE key and the OSE's SUNAT credentials, and the forwarding queue.
 */
@Path("/api/admin/v1/ose")
public class OseAdminResource {

    public record NewClient(@NotBlank @Pattern(regexp = "\\d{11}") String issuerRuc, @Pattern(regexp = "[A-Za-z0-9._-]{4,60}") String username,
            UUID tenantId) {}

    public record Association(@NotBlank @Pattern(regexp = "\\d{11}") String ruc, @NotNull LocalDate startsOn, LocalDate endsOn) {}

    public record Blocked(@NotBlank @Pattern(regexp = "\\d{11}") String ruc, LocalDate listedOn) {}

    private final PlatformAdmin admin;
    private final EntityManager em;
    private final PlatformKeys keys;
    private final PlatformCredentials credentials;
    private final java.util.Optional<String> oseRuc;

    public OseAdminResource(PlatformAdmin admin, EntityManager em, PlatformKeys keys, PlatformCredentials credentials,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.ose.ruc") java.util.Optional<String> oseRuc) {
        this.oseRuc = oseRuc;
        this.admin = admin;
        this.em = em;
        this.keys = keys;
        this.credentials = credentials;
    }

    /** A new OSE user; the password is shown once. */
    @POST
    @Path("/clients")
    @Transactional
    public Map<String, Object> createClient(@Valid NewClient request) {
        String who = admin.require();
        UUID id = Ids.newId();
        String username = request.username() != null ? request.username() : request.issuerRuc() + "OSE" + id.toString().substring(0, 4).toUpperCase();
        String password = OseClients.newPassword();
        try {
            em.createNativeQuery("""
                            INSERT INTO xhandler.ose_client (id, issuer_ruc, username, password_hash, tenant_id, created_by)
                            VALUES (:id, :ruc, :u, :h, :t, :by)""")
                    .setParameter("id", id).setParameter("ruc", request.issuerRuc()).setParameter("u", username)
                    .setParameter("h", OseClients.hash(password)).setParameter("t", request.tenantId()).setParameter("by", who).executeUpdate();
            em.flush();
        } catch (jakarta.persistence.PersistenceException e) {
            String constraint = io.github.projectopenubl.xhandler.api.Constraints.violated(e).orElse("");
            if (constraint.contains("tenant")) {
                throw ApiException.invalid("invalid_request", "La organización indicada no existe.", null);
            }
            if (!constraint.isEmpty()) {
                throw ApiException.conflict("username_taken", "Ese usuario ya existe.");
            }
            throw e;
        }
        AdminResource.PLATFORM_AUDIT.infof("admin %s created OSE user %s for %s", who, username, request.issuerRuc());
        return Map.of("id", id, "issuer_ruc", request.issuerRuc(), "username", username, "password", password);
    }

    @GET
    @Path("/clients")
    @Transactional
    public List<Map<String, Object>> clients() {
        admin.require();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, issuer_ruc, username, tenant_id, status, locked_until, last_login_at FROM xhandler.ose_client ORDER BY created_at""")
                .getResultList();
        return rows.stream().map(r -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", r[0]);
            view.put("issuer_ruc", r[1]);
            view.put("username", r[2]);
            view.put("tenant_id", r[3]);
            view.put("status", r[4]);
            view.put("locked_until", r[5] == null ? null : String.valueOf(StatusResource.toInstant(r[5])));
            view.put("last_login_at", r[6] == null ? null : String.valueOf(StatusResource.toInstant(r[6])));
            return view;
        }).toList();
    }

    @POST
    @Path("/clients/{id}/{action:suspend|activate|reset-password}")
    @Transactional
    public Map<String, Object> clientAction(@PathParam("id") UUID id, @PathParam("action") String action) {
        String who = admin.require();
        String password = "reset-password".equals(action) ? OseClients.newPassword() : null;
        int changed = switch (action) {
            case "suspend" -> em.createNativeQuery("UPDATE xhandler.ose_client SET status = 'suspended' WHERE id = :id").setParameter("id", id)
                    .executeUpdate();
            case "activate" -> em.createNativeQuery(
                            "UPDATE xhandler.ose_client SET status = 'active', failed_attempts = 0, locked_until = NULL WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
            default -> em.createNativeQuery(
                            "UPDATE xhandler.ose_client SET password_hash = :h, failed_attempts = 0, locked_until = NULL WHERE id = :id")
                    .setParameter("h", OseClients.hash(password)).setParameter("id", id).executeUpdate();
        };
        if (changed == 0) {
            throw ApiException.notFound();
        }
        AdminResource.PLATFORM_AUDIT.infof("admin %s: %s on OSE user %s", who, action, id);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("id", id);
        answer.put("action", action);
        if (password != null) {
            answer.put("password", password);
        }
        return answer;
    }

    /** SUNAT's list of issuers associated with the platform as OSE: replaces the stored one. */
    @PUT
    @Path("/association")
    @Transactional
    public Map<String, Object> association(@Valid List<@Valid Association> entries,
            @jakarta.ws.rs.QueryParam("confirm_empty") boolean confirmEmpty) {
        String who = admin.require();
        if (entries == null || entries.isEmpty() && !confirmEmpty) {
            // An empty list refuses every issuer: only on purpose
            throw ApiException.invalid("invalid_request", "Envíe la lista de contribuyentes asociados (o confirme vaciarla).", null);
        }
        em.createNativeQuery("DELETE FROM xhandler.ose_association").executeUpdate();
        for (Association a : entries) {
            em.createNativeQuery("""
                            INSERT INTO xhandler.ose_association (ruc, starts_on, ends_on) VALUES (:r, :s, :e)
                            ON CONFLICT (ruc) DO UPDATE SET starts_on = excluded.starts_on, ends_on = excluded.ends_on, imported_at = now()""")
                    .setParameter("r", a.ruc()).setParameter("s", a.startsOn()).setParameter("e", a.endsOn()).executeUpdate();
        }
        AdminResource.PLATFORM_AUDIT.infof("admin %s imported the OSE association list (%d issuers)", who, entries.size());
        return Map.of("imported", entries.size());
    }

    /** One of SUNAT's blocked-issuer lists (DL 1532): replaces that list. */
    @PUT
    @Path("/blocked/{list}")
    @Transactional
    public Map<String, Object> blocked(@PathParam("list") String list, @Valid List<@Valid Blocked> entries) {
        String who = admin.require();
        if (entries == null || !list.matches("[a-z0-9_-]{2,40}")) {
            throw ApiException.invalid("invalid_request", "Envíe la lista y un nombre de lista válido.", null);
        }
        em.createNativeQuery("DELETE FROM xhandler.blocked_issuer WHERE list = :l").setParameter("l", list).executeUpdate();
        for (Blocked b : entries) {
            em.createNativeQuery("""
                            INSERT INTO xhandler.blocked_issuer (ruc, list, listed_on) VALUES (:r, :l, :d) ON CONFLICT (ruc, list) DO NOTHING""")
                    .setParameter("r", b.ruc()).setParameter("l", list).setParameter("d", b.listedOn()).executeUpdate();
        }
        AdminResource.PLATFORM_AUDIT.infof("admin %s imported blocked list %s (%d issuers)", who, list, entries.size());
        return Map.of("list", list, "imported", entries.size());
    }

    /** The certificate used only to sign CDR-OSE (RS 117-2017 art. 4 h), registered in SOL for the OSE's RUC. */
    @POST
    @Path("/cdr-certificate")
    public Map<String, Object> cdrCertificate(@RestForm("file") FileUpload file, @RestForm("password") String password) throws IOException {
        String who = admin.require();
        if (file == null || password == null) {
            throw ApiException.invalid("invalid_request", "Envíe el archivo .pfx y su contraseña.", null);
        }
        byte[] pkcs12 = Files.readAllBytes(file.uploadedFile());
        // The CDR-OSE certificate must be the OSE's own (SUNAT checks 2824) and in force
        java.security.KeyStore store;
        try {
            store = java.security.KeyStore.getInstance("PKCS12");
            store.load(new java.io.ByteArrayInputStream(pkcs12), password.toCharArray());
        } catch (java.security.GeneralSecurityException | IOException e) {
            throw ApiException.invalid("invalid_certificate", "No se pudo leer el certificado o la contraseña es incorrecta.", null);
        }
        java.security.cert.X509Certificate certificate;
        try {
            String alias = java.util.Collections.list(store.aliases()).stream().filter(a -> {
                try {
                    return store.isKeyEntry(a);
                } catch (java.security.KeyStoreException e) {
                    return false;
                }
            }).findFirst().orElseThrow();
            certificate = (java.security.cert.X509Certificate) store.getCertificate(alias);
            certificate.checkValidity();
        } catch (java.util.NoSuchElementException | java.security.GeneralSecurityException e) {
            throw ApiException.invalid("invalid_certificate", "El certificado no tiene clave privada o no está vigente.", null);
        }
        if (oseRuc.isEmpty() || !certificate.getSubjectX500Principal().getName().contains(oseRuc.get())) {
            throw ApiException.invalid("invalid_certificate", "El certificado no corresponde al RUC del OSE.", null);
        }
        PlatformKeys.Key key = keys.importKey("ose_cdr", pkcs12, password.toCharArray());
        AdminResource.PLATFORM_AUDIT.infof("admin %s imported the OSE CDR certificate %s", who, key.subject());
        return Map.of("subject", key.subject(), "ruc", String.valueOf(key.ruc()), "valid_until", key.validUntil().toString());
    }

    /** The OSE's own SUNAT user for forwarding. */
    @POST
    @Path("/sunat-credentials")
    @Transactional
    public PlatformCredentials.Entry sunatCredentials(Map<String, String> body) {
        String who = admin.require();
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        if (username == null || password == null) {
            throw ApiException.invalid("invalid_request", "Indique username y password.", null);
        }
        for (PlatformCredentials.Entry existing : credentials.list("ose_sunat")) {
            credentials.setActive(existing.id(), false);
        }
        PlatformCredentials.Entry entry = credentials.add("ose_sunat", username, Map.of("username", username, "password", password));
        AdminResource.PLATFORM_AUDIT.infof("admin %s set the OSE SUNAT user %s", who, username);
        return entry;
    }

    /** The forwarding queue against the 1-hour limit. */
    @GET
    @Path("/forwarding")
    @Transactional
    public Map<String, Object> forwarding() {
        admin.require();
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT count(*) FILTER (WHERE forward_status IN ('pending', 'blocked')),
                               extract(epoch FROM now() - min(cdr_issued_at) FILTER (WHERE forward_status = 'pending')),
                               count(*) FILTER (WHERE forward_status = 'rejected'),
                               count(*) FILTER (WHERE forward_status = 'forwarded' AND forwarded_at > cdr_issued_at + interval '1 hour'),
                               count(*) FILTER (WHERE forward_status = 'forwarded')
                        FROM xhandler.ose_submission""").getSingleResult();
        @SuppressWarnings("unchecked")
        List<Object[]> rejected = em.createNativeQuery("""
                        SELECT id, name, last_error, cdr_issued_at FROM xhandler.ose_submission WHERE forward_status = 'rejected'
                        ORDER BY cdr_issued_at DESC LIMIT 50""").getResultList();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("pending", ((Number) r[0]).longValue());
        view.put("oldest_pending_seconds", r[1] == null ? 0 : ((Number) r[1]).doubleValue());
        view.put("rejected", ((Number) r[2]).longValue());
        view.put("blocked", ((Number) em.createNativeQuery("SELECT count(*) FROM xhandler.ose_submission WHERE forward_status = 'blocked'")
                .getSingleResult()).longValue());
        view.put("late", ((Number) r[3]).longValue());
        view.put("forwarded", ((Number) r[4]).longValue());
        view.put("rejected_items", rejected.stream().map(x -> Map.of("id", x[0], "name", x[1], "error", String.valueOf(x[2]),
                "cdr_issued_at", String.valueOf(StatusResource.toInstant(x[3])))).toList());
        return view;
    }

    /** Validations per OSE user and month: the separate billing line (RS 242-2018 art. 7.16), also for issuers that aren't tenants. */
    @GET
    @Path("/usage")
    @Transactional
    public List<Map<String, Object>> usage(@jakarta.ws.rs.QueryParam("month") String month) {
        admin.require();
        java.time.YearMonth m = month == null ? java.time.YearMonth.now() : java.time.YearMonth.parse(month);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT c.id, c.issuer_ruc, c.username, c.tenant_id, coalesce(sum(u.validations), 0)
                        FROM xhandler.ose_client c LEFT JOIN xhandler.ose_usage u ON u.ose_client_id = c.id AND u.day BETWEEN :from AND :to
                        GROUP BY c.id ORDER BY c.issuer_ruc""")
                .setParameter("from", m.atDay(1)).setParameter("to", m.atEndOfMonth()).getResultList();
        return rows.stream().map(x -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("ose_client_id", x[0]);
            view.put("issuer_ruc", x[1]);
            view.put("username", x[2]);
            view.put("tenant_id", x[3]);
            view.put("validations", ((Number) x[4]).longValue());
            return view;
        }).toList();
    }

    /** Sends a rejected forward again (after fixing what SUNAT refused). */
    @POST
    @Path("/submissions/{id}/reforward")
    @Transactional
    public Map<String, Object> reforward(@PathParam("id") UUID id) {
        String who = admin.require();
        int changed = em.createNativeQuery("""
                        UPDATE xhandler.ose_submission SET forward_status = 'pending', next_attempt_at = now()
                        WHERE id = :id AND result = 'cdr' AND forward_status = 'rejected'""").setParameter("id", id).executeUpdate();
        if (changed == 0) {
            throw ApiException.notFound();
        }
        AdminResource.PLATFORM_AUDIT.infof("admin %s re-forwarded OSE submission %s", who, id);
        return Map.of("id", id, "forward_status", "pending");
    }
}
