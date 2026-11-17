package io.github.projectopenubl.xhandler.webhooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.secrets.SecretBox;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jboss.logging.Logger;

/**
 * Delivers due webhooks (schedule {@code webhooks}, every 2 s). Like the reconciler, it leases due
 * deliveries so a delivery never runs twice at once, and writes each outcome fenced on its attempt
 * count. Retries back off 10 s, 1 min, 5 min, 30 min, 2 h, then 6 h, and stop after 12 attempts.
 */
@ApplicationScoped
public class WebhookDeliveryJob implements JobHandler {

    private static final Logger LOG = Logger.getLogger(WebhookDeliveryJob.class);

    static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6));
    static final int MAX_ATTEMPTS = 12;
    static final int BATCH = 100;
    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final EntityManager em;
    private final TenantAccess access;
    private final SecretBox box;
    private final Egress egress;
    private final ObjectMapper json;

    public WebhookDeliveryJob(EntityManager em, TenantAccess access, SecretBox box, Egress egress, ObjectMapper json) {
        this.em = em;
        this.access = access;
        this.box = box;
        this.egress = egress;
        this.json = json;
    }

    @Override
    public String name() {
        return "webhooks.deliver";
    }

    @Override
    public Map<String, Object> run(JobContext context) throws Exception {
        record Leased(UUID id, UUID tenantId, int attempts) {}
        List<Leased> due = QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT id, tenant_id, attempts FROM xhandler.lease_webhook_deliveries(:n, interval '2 minutes')")
                    .setParameter("n", BATCH)
                    .getResultList();
            return rows.stream().map(r -> new Leased((UUID) r[0], (UUID) r[1], ((Number) r[2]).intValue())).toList();
        });
        int delivered = 0;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Leased lease : due) {
                results.add(pool.submit(() -> deliver(lease.id(), lease.tenantId(), lease.attempts())));
            }
            for (Future<Boolean> result : results) {
                if (Boolean.TRUE.equals(result.get())) {
                    delivered++;
                }
            }
        }
        return Map.of("leased", due.size(), "delivered", delivered);
    }

    private boolean deliver(UUID id, UUID tenantId, int attempts) {
        record Request(String url, byte[] body, byte[] secret, String eventId, String type) {}
        Request request = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            WebhookDelivery delivery = WebhookDelivery.findById(id);
            WebhookSubscription subscription = WebhookSubscription.findById(delivery.subscriptionId);
            if (subscription == null || subscription.deletedAt != null || !subscription.active) {
                delivery.status = "failed";
                delivery.lastError = "subscription removed or disabled";
                delivery.nextAttemptAt = null;
                return null;
            }
            return new Request(subscription.url, json.writeValueAsBytes(delivery.payload), box.open(subscription.sealed(),
                    subscription.context()), delivery.eventId.toString(), delivery.eventType);
        });
        if (request == null) {
            return false;
        }
        Integer status = null;
        String error = null;
        try {
            egress.check(request.url());
            long timestamp = Instant.now().getEpochSecond();
            HttpRequest http = HttpRequest.newBuilder(java.net.URI.create(request.url()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/cloudevents+json")
                    .header("User-Agent", "xhandler-service-webhooks/1")
                    .header("X-Xhandler-Event-Id", request.eventId())
                    .header("X-Xhandler-Event-Type", request.type())
                    .header("X-Xhandler-Signature", signature(request.secret(), timestamp, request.body()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(request.body()))
                    .build();
            status = egress.client().send(http, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status / 100 != 2) {
                error = "HTTP " + status;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "interrupted";
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            java.util.Arrays.fill(request.secret(), (byte) 0);
        }
        Integer finalStatus = status;
        String finalError = error;
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            WebhookDelivery delivery = WebhookDelivery.findById(id, LockModeType.PESSIMISTIC_WRITE);
            if (delivery == null || delivery.attempts != attempts || !"pending".equals(delivery.status)) {
                return;
            }
            Instant now = Instant.now();
            delivery.attempts++;
            delivery.lastStatusCode = finalStatus;
            delivery.lastError = finalError == null ? null : truncate(finalError);
            delivery.updatedAt = now;
            if (finalError == null) {
                delivery.status = "delivered";
                delivery.deliveredAt = now;
                delivery.nextAttemptAt = null;
            } else if (delivery.attempts >= MAX_ATTEMPTS) {
                delivery.status = "failed";
                delivery.nextAttemptAt = null;
            } else {
                delivery.nextAttemptAt = now.plus(BACKOFF.get(Math.min(delivery.attempts - 1, BACKOFF.size() - 1)));
            }
        });
        if (error != null) {
            LOG.debugf("webhook delivery %s failed: %s", id, error);
        }
        return error == null;
    }

    /** {@code t=<unix seconds>,v1=<hex HMAC-SHA256 of "t.body">}: receivers check both and reject old timestamps. */
    static String signature(byte[] secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
