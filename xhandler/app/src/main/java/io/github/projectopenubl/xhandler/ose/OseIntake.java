package io.github.projectopenubl.xhandler.ose;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.documents.Deadlines;
import io.github.projectopenubl.xhandler.documents.UsageEvents;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The OSE's check of a document (DESIGN.md §11.4; RS 117-2017 Annex B and C): the issuer is
 * authenticated, the ZIP hardened, blocked and unassociated issuers refused, the document validated
 * in full (no shortcut for documents the platform signed as PSE), and the answer is either a CDR-OSE
 * signed with the platform's OSE key or an inconsistency notice. Every answer is recorded in
 * {@code ose_submission}; CDRs are queued for forwarding to SUNAT within 1 hour.
 *
 * <p>{@code sendBill} answers at once; {@code sendSummary} and {@code sendPack} answer with a
 * ticket for {@code getStatus}. Validation runs before answering in every case.
 */
@ApplicationScoped
public class OseIntake {

    /** OSE files live outside any tenant's namespace. */
    static final UUID STORAGE = UUID.fromString("00000000-0000-0000-0000-00000000005e");
    static final Duration RETENTION = Duration.ofDays(31);
    static final int MAX_PACK = 500;

    private static final Pattern BILL = Pattern.compile("^(\\d{11})-(\\d{2})-([A-Z0-9]{4})-(\\d{1,8})$");
    private static final Pattern SUMMARY = Pattern.compile("^(\\d{11})-(RC|RA|RR)-(\\d{8})-(\\d{1,5})$");
    private static final List<String> TIPOS = List.of("01", "03", "07", "08", "20", "40");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** An answer that is a SOAP fault (or a REST 422): SUNAT's code and message. */
    public static final class Fault extends RuntimeException {
        public final String code;

        public Fault(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    /** The CDR-OSE of one document. */
    public record Cdr(String name, String cdrXml) {
        public byte[] zip() {
            return OseIntake.zip(Map.of("R-" + name + ".xml", cdrXml.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /** {@code statusCode}: 0 done, 98 in process, 99 with inconsistencies (SUNAT's getStatus codes). */
    public record Status(String statusCode, byte[] content) {}

    private final EntityManager em;
    private final Engine engine;
    private final PlatformKeys platformKeys;
    private final FileStore files;
    private final UsageEvents usage;
    private final TenantAccess access;
    private final MeterRegistry metrics;
    private final Optional<String> oseRuc;
    private final String environment;

    public OseIntake(EntityManager em, Engine engine, PlatformKeys platformKeys, FileStore files, UsageEvents usage, TenantAccess access,
            MeterRegistry metrics, @ConfigProperty(name = "xhandler.ose.ruc") Optional<String> oseRuc,
            @ConfigProperty(name = "xhandler.ose.environment", defaultValue = "fake") String environment) {
        this.em = em;
        this.engine = engine;
        this.platformKeys = platformKeys;
        this.files = files;
        this.usage = usage;
        this.access = access;
        this.metrics = metrics;
        this.oseRuc = oseRuc;
        this.environment = environment;
    }

    record Name(String full, String ruc, String tipo, String serie, long numero, boolean summary) {}

    private static Name name(String fileName, boolean summary) {
        String base = fileName == null ? "" : fileName.replaceFirst("(?i)\\.(zip|xml)$", "");
        Matcher m = (summary ? SUMMARY : BILL).matcher(base);
        if (!m.matches()) {
            throw new Fault("0151", "El nombre del archivo ZIP es incorrecto.");
        }
        return new Name(base, m.group(1), m.group(2), m.group(3), Long.parseLong(m.group(4)), summary);
    }

    private String ruc() {
        return oseRuc.orElseThrow(() -> new Fault("0109", "El sistema no puede responder su solicitud. (Servicio no disponible)"));
    }

    /** The issuer must have chosen us as its OSE in SOL; the relation counts until the 7th day of the month after it ends. */
    private void checkIssuer(OseClients.Client client, Name name) {
        if (!client.issuerRuc().equals(name.ruc())) {
            throw new Fault("0111", "No tiene el perfil para enviar comprobantes electrónicos de este emisor.");
        }
        QuarkusTransaction.requiringNew().run(() -> {
            LocalDate today = Deadlines.todayInLima();
            boolean associated = !em.createNativeQuery("""
                            SELECT 1 FROM xhandler.ose_association WHERE ruc = :r AND starts_on <= :today
                              AND (ends_on IS NULL OR :today <= (date_trunc('month', ends_on) + interval '1 month' + interval '6 days')::date)""")
                    .setParameter("r", name.ruc()).setParameter("today", today).getResultList().isEmpty();
            if (!associated) {
                throw new Fault("0154", "El emisor no ha autorizado a este OSE en SUNAT Operaciones en Línea.");
            }
            if (!em.createNativeQuery("SELECT 1 FROM xhandler.blocked_issuer WHERE ruc = :r").setParameter("r", name.ruc()).getResultList()
                    .isEmpty()) {
                throw new Fault("2010", "El emisor está impedido de emitir comprobantes electrónicos (listas de SUNAT).");
            }
        });
    }

    /** One comprobante: the CDR-OSE at once, or a fault. */
    public Cdr sendBill(OseClients.Client client, String channel, String fileName, byte[] zip) {
        Name name = name(fileName, false);
        ruc();
        checkIssuer(client, name);
        Map<String, byte[]> xmls = ZipGuard.xmlFiles(zip, 1);
        byte[] xml = xmls.get(name.full() + ".xml");
        if (xml == null) {
            throw new Fault("0151", "El nombre del XML no corresponde al del ZIP.");
        }
        Checked checked = check(client, channel, name, xml, null);
        if (checked.cdr() == null) {
            throw new Fault(checked.code(), checked.message());
        }
        return checked.cdr();
    }

    /** A resumen diario or comunicación de baja: checked now, answered through the ticket. */
    public String sendSummary(OseClients.Client client, String channel, String fileName, byte[] zip) {
        Name name = name(fileName, true);
        ruc();
        checkIssuer(client, name);
        Map<String, byte[]> xmls = ZipGuard.xmlFiles(zip, 1);
        byte[] xml = xmls.get(name.full() + ".xml");
        if (xml == null) {
            throw new Fault("0151", "El nombre del XML no corresponde al del ZIP.");
        }
        String ticket = ticket();
        check(client, channel, name, xml, ticket);
        return ticket;
    }

    /** A batch of comprobantes of one issuer: one CDR-OSE per XML, through the ticket. */
    public String sendPack(OseClients.Client client, String channel, String fileName, byte[] zip) {
        ruc();
        Map<String, byte[]> xmls = ZipGuard.xmlFiles(zip, MAX_PACK);
        String ticket = ticket();
        boolean any = false;
        for (Map.Entry<String, byte[]> xml : xmls.entrySet()) {
            Name name = name(xml.getKey(), false);
            checkIssuer(client, name);
            check(client, channel, name, xml.getValue(), ticket);
            any = true;
        }
        if (!any) {
            throw new Fault("0157", "El archivo ZIP no contiene comprobantes.");
        }
        return ticket;
    }

    private static String ticket() {
        return System.currentTimeMillis() + String.format("%03d", RANDOM.nextInt(1000));
    }

    record Checked(Cdr cdr, String code, String message) {}

    /** Validates one document and records the answer. */
    private Checked check(OseClients.Client client, String channel, Name name, byte[] xmlBytes, String ticket) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        String sha256 = FileStore.sha256(xmlBytes);
        Instant received = Instant.now();
        // A resend of an accepted document: the same bytes get the same CDR; other bytes collide
        Optional<Object[]> previous = QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                            SELECT sha256, cdr_ref FROM xhandler.ose_submission
                            WHERE issuer_ruc = :r AND tipo = :t AND serie = :s AND numero = :n AND result = 'cdr'""")
                    .setParameter("r", name.ruc()).setParameter("t", name.tipo()).setParameter("s", name.serie()).setParameter("n", name.numero())
                    .getResultList();
            return rows.stream().findFirst();
        });
        if (previous.isPresent()) {
            if (sha256.equals(previous.get()[0])) {
                return new Checked(new Cdr(name.full(), new String(files.get((String) previous.get()[1]), StandardCharsets.UTF_8)), null, null);
            }
            return record(client, channel, name, ticket, received, sha256, null, "1033",
                    "El comprobante fue registrado previamente con otros datos.");
        }
        if (!name.summary() && !TIPOS.contains(name.tipo())) {
            String message = "09".equals(name.tipo()) || "31".equals(name.tipo())
                    ? "Las guías de remisión se envían directamente a SUNAT." : "Tipo de comprobante no admitido por el OSE.";
            return record(client, channel, name, ticket, received, sha256, null, "1003", message);
        }
        EngineModels.InspectResponse identity;
        try {
            identity = engine.inspect(xml);
        } catch (io.github.projectopenubl.xhandler.engine.EngineException e) {
            return record(client, channel, name, ticket, received, sha256, null, "0306", "No se puede leer (parsear) el archivo XML.");
        }
        if (!name.ruc().equals(identity.ruc()) || !name.summary() && (!name.serie().equals(identity.serie())
                || identity.numero() == null || identity.numero() != name.numero() || !name.tipo().equals(identity.tipoComprobante()))) {
            return record(client, channel, name, ticket, received, sha256, null, "0151",
                    "El nombre del archivo no corresponde al contenido del comprobante.");
        }
        if (!identity.signed()) {
            return record(client, channel, name, ticket, received, sha256, null, "0306", "El comprobante no tiene firma digital.");
        }
        EngineModels.ValidateResponse validation = engine.validate(xml);
        if (!validation.valid()) {
            EngineModels.Finding first = validation.errors().get(0);
            String code = first.code() != null && first.code().matches("\\d{4}") ? first.code() : "0306";
            return record(client, channel, name, ticket, received, sha256, null, code, truncate(first.message()));
        }
        List<EngineModels.CdrNote> notes = validation.observations() == null ? List.of()
                : validation.observations().stream().map(o -> new EngineModels.CdrNote(o.code(), truncate(o.message()))).toList();
        String description = (name.summary() ? "El resumen " : "El comprobante ") + identity.serieNumero() + " ha sido aceptado";
        String cdrXml = signCdr(engine.cdr(xml, ruc(), "0", description, notes, Instant.now()));
        FileStore.Stored storedXml = files.put(STORAGE, xmlBytes);
        FileStore.Stored storedCdr = files.put(STORAGE, cdrXml.getBytes(StandardCharsets.UTF_8));
        return record(client, channel, name, ticket, received, sha256, new String[] {storedXml.key(), storedCdr.key(), cdrXml}, "0", description);
    }

    /** CDR-OSE signed with the platform's OSE key; the demo key outside production when there's none. */
    private String signCdr(String unsigned) {
        Optional<PlatformKeys.Key> key = platformKeys.active("ose_cdr");
        if (key.isEmpty()) {
            if ("production".equals(environment)) {
                throw new Fault("0109", "El sistema no puede responder su solicitud. (Servicio no disponible)");
            }
            return engine.signWithDemoKey(unsigned).xml();
        }
        EngineModels.SignPrepareResponse prepared = engine.signPrepare(unsigned);
        byte[] signature = platformKeys.sign(key.get(), Base64.getDecoder().decode(prepared.dataToSign()));
        return engine.signFinalize(unsigned, signature, key.get().certificate()).xml();
    }

    private Checked record(OseClients.Client client, String channel, Name name, String ticket, Instant received, String sha256, String[] cdr,
            String code, String description) {
        boolean accepted = cdr != null;
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                Instant now = Instant.now();
                em.createNativeQuery("""
                                INSERT INTO xhandler.ose_submission (id, ose_client_id, issuer_ruc, tipo, serie, numero, name, channel, ticket,
                                    received_at, result, response_code, description, sha256, xml_ref, cdr_ref, cdr_issued_at, forward_status,
                                    next_attempt_at, purge_after)
                                VALUES (:id, :client, :ruc, :tipo, :serie, :numero, :name, :channel, :ticket, :received, :result, :code, :description,
                                    :sha, :xml, :cdr, :issued, :forward, :next, :purge)""")
                        .setParameter("id", Ids.newId()).setParameter("client", client.id()).setParameter("ruc", name.ruc())
                        .setParameter("tipo", name.tipo()).setParameter("serie", name.serie()).setParameter("numero", name.numero())
                        .setParameter("name", name.full()).setParameter("channel", channel).setParameter("ticket", ticket)
                        .setParameter("received", received).setParameter("result", accepted ? "cdr" : "inconsistency").setParameter("code", code)
                        .setParameter("description", description).setParameter("sha", sha256)
                        .setParameter("xml", accepted ? cdr[0] : null).setParameter("cdr", accepted ? cdr[1] : null)
                        .setParameter("issued", accepted ? now : null).setParameter("forward", accepted ? "pending" : null)
                        .setParameter("next", accepted ? now : null).setParameter("purge", now.plus(RETENTION))
                        .executeUpdate();
                em.createNativeQuery("""
                                INSERT INTO xhandler.ose_usage (ose_client_id, day, validations) VALUES (:c, :d, 1)
                                ON CONFLICT (ose_client_id, day) DO UPDATE SET validations = ose_usage.validations + 1""")
                        .setParameter("c", client.id()).setParameter("d", Deadlines.todayInLima()).executeUpdate();
                if (client.tenantId() != null) {
                    // Its own billing line (RS 242-2018 art. 7.16)
                    access.enterAsPlatform(client.tenantId());
                    usage.record(client.tenantId(), "ose_validation", Map.of("issuer_ruc", name.ruc(), "result", accepted ? "cdr" : "inconsistency"));
                }
            });
        } catch (RuntimeException e) {
            if (accepted && io.github.projectopenubl.xhandler.api.Constraints.violated(e).isPresent()) {
                // Two sends of one number at once: the first one's CDR stands
                throw new Fault("1033", "El comprobante fue registrado previamente con otros datos.");
            }
            throw e;
        }
        metrics.counter("xhandler.ose.submissions", "result", accepted ? "cdr" : "inconsistency").increment();
        return new Checked(accepted ? new Cdr(name.full(), cdr[2]) : null, code, description);
    }

    /** The answer behind a ticket of this client. */
    public Status getStatus(OseClients.Client client, String ticket) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                        SELECT name, result, response_code, description, cdr_ref FROM xhandler.ose_submission
                        WHERE ticket = :t AND ose_client_id = :c ORDER BY name""").setParameter("t", ticket).setParameter("c", client.id())
                .getResultList());
        if (rows.isEmpty()) {
            throw new Fault("0127", "El ticket no existe.");
        }
        Map<String, byte[]> content = new LinkedHashMap<>();
        StringBuilder errors = new StringBuilder();
        for (Object[] r : rows) {
            if ("cdr".equals(r[1])) {
                content.put("R-" + r[0] + ".xml", files.get((String) r[4]));
            } else {
                errors.append(r[0]).append('|').append(r[2]).append('|').append(r[3]).append('\n');
            }
        }
        if (!errors.isEmpty()) {
            content.put("errores.txt", errors.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new Status(errors.isEmpty() ? "0" : "99", zip(content));
    }

    /** The CDR-OSE of a document this client sent, if accepted. */
    public Optional<Cdr> cdrOf(OseClients.Client client, String ruc, String tipo, String serie, long numero) {
        if (!client.issuerRuc().equals(ruc)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                        SELECT name, cdr_ref FROM xhandler.ose_submission
                        WHERE issuer_ruc = :r AND tipo = :t AND serie = :s AND numero = :n AND result = 'cdr' AND cdr_ref IS NOT NULL""")
                .setParameter("r", ruc).setParameter("t", tipo).setParameter("s", serie).setParameter("n", numero).getResultList());
        return rows.stream().findFirst().map(r -> new Cdr((String) r[0], new String(files.get((String) r[1]), StandardCharsets.UTF_8)));
    }

    static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static String truncate(String text) {
        return text == null ? "" : text.length() > 250 ? text.substring(0, 250) : text;
    }

    static List<String> names(Map<String, byte[]> files) {
        return new ArrayList<>(files.keySet());
    }
}
