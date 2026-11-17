package io.github.projectopenubl.xhandler.email;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.mail.MailClient;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Sends email (DESIGN.md §11.6, §26.5): through the platform sender (SPF/DKIM/DMARC on the platform
 * domain), or through a tenant's own SMTP server. Never called inside a transaction.
 */
@ApplicationScoped
public class EmailSender {

    /** An email. {@code from}: null for the platform default. */
    public record Message(String from, String replyTo, List<String> to, List<String> cc, String subject, String html,
            List<Attachment> attachments) {}

    public record Attachment(String name, String contentType, byte[] content) {}

    /** A tenant's SMTP server (credential kind smtp). */
    public record Smtp(String host, int port, String username, String password, String from, boolean startTls) {}

    private final Mailer mailer;
    private final Vertx vertx;
    private final String platformFrom;

    public EmailSender(Mailer mailer, Vertx vertx,
            @ConfigProperty(name = "xhandler.mail.from", defaultValue = "Facturación <facturacion@xhandler.local>") String platformFrom) {
        this.mailer = mailer;
        this.vertx = vertx;
        this.platformFrom = platformFrom;
    }

    public void sendPlatform(Message message) {
        Mail mail = new Mail();
        mail.setFrom(message.from() == null ? platformFrom : message.from());
        if (message.replyTo() != null) {
            mail.setReplyTo(message.replyTo());
        }
        mail.setTo(message.to());
        mail.setCc(message.cc() == null ? List.of() : message.cc());
        mail.setSubject(message.subject());
        mail.setHtml(message.html());
        for (Attachment a : message.attachments() == null ? List.<Attachment>of() : message.attachments()) {
            mail.addAttachment(a.name(), a.content(), a.contentType());
        }
        mailer.send(mail);
    }

    public void sendTenantSmtp(Smtp smtp, Message message) {
        MailConfig config = new MailConfig()
                .setHostname(smtp.host())
                .setPort(smtp.port())
                .setUsername(smtp.username())
                .setPassword(smtp.password())
                .setStarttls(smtp.startTls() ? StartTLSOptions.REQUIRED : StartTLSOptions.OPTIONAL)
                .setSsl(smtp.port() == 465)
                .setConnectTimeout(10_000);
        MailClient client = MailClient.create(vertx, config);
        try {
            io.vertx.ext.mail.MailMessage mail = new io.vertx.ext.mail.MailMessage()
                    .setFrom(smtp.from())
                    .setTo(message.to())
                    .setCc(message.cc() == null ? List.of() : message.cc())
                    .setSubject(message.subject())
                    .setHtml(message.html());
            if (message.replyTo() != null) {
                mail.setHeaders(io.vertx.core.MultiMap.caseInsensitiveMultiMap().add("Reply-To", message.replyTo()));
            }
            List<io.vertx.ext.mail.MailAttachment> attachments = new java.util.ArrayList<>();
            for (Attachment a : message.attachments() == null ? List.<Attachment>of() : message.attachments()) {
                attachments.add(io.vertx.ext.mail.MailAttachment.create().setName(a.name()).setContentType(a.contentType())
                        .setData(io.vertx.core.buffer.Buffer.buffer(a.content())));
            }
            mail.setAttachment(attachments);
            client.sendMail(mail).await().atMost(Duration.ofSeconds(60));
        } finally {
            client.closeAndAwait();
        }
    }

    static Smtp smtp(Map<String, String> fields) {
        return new Smtp(fields.get("host"), Integer.parseInt(fields.getOrDefault("port", "587")), fields.get("username"),
                fields.get("password"), fields.get("from"), !"false".equals(fields.get("start_tls")));
    }
}
