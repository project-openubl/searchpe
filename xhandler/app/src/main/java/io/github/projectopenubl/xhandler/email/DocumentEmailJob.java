package io.github.projectopenubl.xhandler.email;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.documents.AccessLinks;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentEvent;
import io.github.projectopenubl.xhandler.documents.DocumentFile;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.pdf.DocumentPdf;
import io.github.projectopenubl.xhandler.secrets.CredentialService;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emails a document to its buyer (DESIGN.md §11.6): PDF, XML and optionally the CDR, with the
 * buyer's capability link. Sent through the tenant's SMTP when it has one, else the platform
 * sender. Suppressed addresses (hard bounces, complaints) are skipped. Every delivery is logged,
 * and the first successful one marks the document delivered.
 */
@ApplicationScoped
public class DocumentEmailJob implements JobHandler {

    public static final String NAME = "document.email";

    private final TenantAccess access;
    private final DocumentPdf pdf;
    private final FileStore files;
    private final AccessLinks links;
    private final CredentialService credentials;
    private final EmailSender sender;
    private final EntityManager em;
    private final io.github.projectopenubl.xhandler.webhooks.Egress egress;
    private final io.github.projectopenubl.xhandler.billing.Entitlements entitlements;

    public DocumentEmailJob(TenantAccess access, DocumentPdf pdf, FileStore files, AccessLinks links, CredentialService credentials,
            EmailSender sender, EntityManager em, io.github.projectopenubl.xhandler.webhooks.Egress egress,
            io.github.projectopenubl.xhandler.billing.Entitlements entitlements) {
        this.entitlements = entitlements;
        this.egress = egress;
        this.access = access;
        this.pdf = pdf;
        this.files = files;
        this.links = links;
        this.credentials = credentials;
        this.sender = sender;
        this.em = em;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        UUID documentId = context.uuidArg("document_id");
        List<String> givenTo = strings(context.args().path("to"));
        List<String> cc = strings(context.args().path("cc"));
        boolean toCustomer = context.args().path("to_customer").asBoolean(false);
        boolean includeCdr = context.args().path("include_cdr").asBoolean(false);
        record Prepared(EmailSender.Message message, EmailSender.Smtp smtp, List<String> skipped) {}
        Prepared prepared = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(context.tenantId());
            Document doc = Document.findById(documentId);
            Company company = Company.findById(doc.companyId);
            List<String> skipped = new ArrayList<>();
            List<String> recipients = new ArrayList<>();
            List<String> to = new ArrayList<>(givenTo);
            if (toCustomer) {
                customerEmail(doc).ifPresent(to::add);
            }
            List<String> ccKept = cc.stream().filter(a -> !EmailSuppressions.suppressed(em, a)).toList();
            for (String address : to) {
                if (EmailSuppressions.suppressed(em, address)) {
                    skipped.add(address);
                } else {
                    recipients.add(address);
                }
            }
            if (recipients.isEmpty()) {
                return new Prepared(null, null, skipped);
            }
            String link = links.url(links.create(doc));
            List<EmailSender.Attachment> attachments = new ArrayList<>();
            String base = doc.tipoComprobante + "-" + doc.serieNumero();
            attachments.add(new EmailSender.Attachment(base + ".pdf", "application/pdf", pdf.render(doc.id, link)));
            DocumentFile.of(doc.id, "xml_signed").ifPresent(f ->
                    attachments.add(new EmailSender.Attachment(base + ".xml", "application/xml", files.get(f.storageKey))));
            if (includeCdr) {
                DocumentFile.of(doc.id, "cdr_zip").ifPresent(f ->
                        attachments.add(new EmailSender.Attachment("R-" + base + ".zip", "application/zip", files.get(f.storageKey))));
            }
            String subject = subject(company.emailSubject, doc, company);
            String body = HtmlSanitizer.sanitize(render(company.emailBody, DEFAULT_BODY, doc, company, link));
            // The plan may have changed since the SMTP server was saved (downgrade, stale entitlement)
            EmailSender.Smtp smtp = entitlements.usage(doc.tenantId).plan().customSmtp()
                    ? credentials.read(scope, company, "smtp", "email:" + doc.id).map(EmailSender::smtp).orElse(null)
                    : null;
            return new Prepared(new EmailSender.Message(sender(company), company.email, recipients, ccKept, subject, body, attachments),
                    smtp, skipped);
        });
        if (prepared.message() == null) {
            return Map.of("sent", 0, "skipped", prepared.skipped());
        }
        String result = "sent";
        String detail = null;
        try {
            if (prepared.smtp() != null) {
                // Re-checked at send time: the host may resolve elsewhere since it was saved (§16)
                if (!java.util.Set.of(25, 465, 587, 2525).contains(prepared.smtp().port())) {
                    throw new SmtpRefused("Puerto SMTP no permitido.");
                }
                try {
                    egress.checkHost(prepared.smtp().host());
                } catch (io.github.projectopenubl.xhandler.api.ApiException e) {
                    throw new SmtpRefused("El servidor SMTP apunta a una dirección no permitida.");
                }
                sender.sendTenantSmtp(prepared.smtp(), prepared.message());
            } else {
                sender.sendPlatform(prepared.message());
            }
        } catch (RuntimeException e) {
            // Logged here; the tenant sees a generic reason (no oracle on its SMTP target)
            org.jboss.logging.Logger.getLogger(DocumentEmailJob.class).warnf("email for document %s failed: %s", documentId, e.getMessage());
            result = "failed";
            detail = e instanceof SmtpRefused refused ? refused.getMessage() : "No se pudo enviar el correo.";
        }
        String finalResult = result;
        String finalDetail = detail;
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(context.tenantId());
            Document doc = Document.findById(documentId, LockModeType.PESSIMISTIC_WRITE);
            for (String recipient : prepared.message().to()) {
                em.createNativeQuery("""
                                INSERT INTO xhandler.delivery (id, tenant_id, document_id, channel, recipient, result, detail)
                                VALUES (:id, :tenant, :document, 'email', :recipient, :result, :detail)""")
                        .setParameter("id", Ids.newId())
                        .setParameter("tenant", doc.tenantId)
                        .setParameter("document", doc.id)
                        .setParameter("recipient", recipient)
                        .setParameter("result", finalResult)
                        .setParameter("detail", finalDetail == null ? null : finalDetail.substring(0, Math.min(500, finalDetail.length())))
                        .executeUpdate();
            }
            if ("sent".equals(finalResult) && doc.deliveredAt == null) {
                doc.deliveredAt = Instant.now();
                doc.deliveredSource = "service";
                doc.updatedAt = doc.deliveredAt;
                DocumentEvent.record(doc, null, "delivered (email)", "platform:worker", context.correlationId());
            }
        });
        if (!"sent".equals(result)) {
            throw new IllegalStateException("email not sent: " + detail);
        }
        return Map.of("sent", prepared.message().to().size(), "skipped", prepared.skipped());
    }

    static final String DEFAULT_BODY = """
            <p>Estimado cliente:</p>
            <p>{razon_social} le envía su {tipo_nombre} <b>{serie_numero}</b> del {fecha}, por {moneda} {total}.</p>
            <p>Adjuntamos la representación impresa (PDF) y el archivo XML. También puede consultarlo en:<br/>
            <a href="{link}">{link}</a></p>
            <p>Atentamente,<br/>{razon_social}</p>""";

    static final Map<String, String> TIPOS = Map.of("01", "factura electrónica", "03", "boleta de venta electrónica",
            "07", "nota de crédito electrónica", "08", "nota de débito electrónica", "09", "guía de remisión electrónica",
            "31", "guía de remisión electrónica", "20", "comprobante de retención", "40", "comprobante de percepción");

    /** {placeholders} in the company's template; values are HTML-escaped. */
    static String render(String template, String fallback, Document doc, Company company, String link) {
        String text = template == null || template.isBlank() ? fallback : template;
        Map<String, String> values = Map.of(
                "{tipo}", doc.tipoComprobante, "{tipo_nombre}", TIPOS.getOrDefault(doc.tipoComprobante, "comprobante electrónico"),
                "{serie_numero}", doc.serieNumero(), "{razon_social}", company.razonSocial, "{fecha}", doc.fechaEmision.toString(),
                "{moneda}", doc.moneda == null ? "" : doc.moneda, "{total}", doc.total == null ? "" : doc.total.toPlainString(),
                "{link}", link);
        for (Map.Entry<String, String> e : values.entrySet()) {
            text = text.replace(e.getKey(), escape(e.getValue()));
        }
        return text;
    }

    /** The subject: plain text, one line (no header injection). */
    static String subject(String template, Document doc, Company company) {
        String text = template == null || template.isBlank() ? "{tipo} {serie_numero} - {razon_social}" : template;
        Map<String, String> values = Map.of("{tipo}", TIPOS.getOrDefault(doc.tipoComprobante, doc.tipoComprobante),
                "{serie_numero}", doc.serieNumero(), "{razon_social}", company.razonSocial, "{fecha}", doc.fechaEmision.toString());
        for (Map.Entry<String, String> e : values.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }
        return text.replaceAll("[\\r\\n\\t]+", " ").strip();
    }

    /** The buyer's email as the client gave it ({@code cliente.contacto.email} in the input). */
    private java.util.Optional<String> customerEmail(Document doc) {
        return DocumentFile.of(doc.id, "input").map(f -> {
            try {
                JsonNode email = new com.fasterxml.jackson.databind.ObjectMapper().readTree(files.get(f.storageKey))
                        .path("spec").path("cliente").path("contacto").path("email");
                return email.isMissingNode() || email.isNull() ? null : email.asText();
            } catch (java.io.IOException e) {
                return null;
            }
        }).filter(a -> a.matches("[^@\\s<>\"]+@[^@\\s<>\"]+\\.[A-Za-z]{2,}"));
    }

    /** As the company's own address when the tenant verified its domain, else the platform's sender. */
    private String sender(Company company) {
        if (company.email == null || !company.email.contains("@")) {
            return null;
        }
        String domain = company.email.substring(company.email.indexOf('@') + 1).toLowerCase(java.util.Locale.ROOT);
        boolean verified = !em.createNativeQuery("SELECT 1 FROM xhandler.sender_domain WHERE domain = :d AND verified_at IS NOT NULL")
                .setParameter("d", domain).getResultList().isEmpty();
        return verified ? company.razonSocial.replaceAll("[\\r\\n\"<>]", "") + " <" + company.email + ">" : null;
    }

    /** An SMTP target refused at send time (the message is safe to show the tenant). */
    static class SmtpRefused extends RuntimeException {
        SmtpRefused(String message) {
            super(message);
        }
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asText()));
        return values;
    }
}
