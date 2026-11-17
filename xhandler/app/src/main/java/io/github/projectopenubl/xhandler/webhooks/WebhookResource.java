package io.github.projectopenubl.xhandler.webhooks;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.secrets.SecretBox;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook subscriptions, delivery logs, redelivery and test events (DESIGN.md §12, §26.2). The
 * signing secret is returned once, at creation.
 */
@Path("/api/v1/tenants/{tenantId}/webhooks")
public class WebhookResource {

    public record CreateWebhook(@NotBlank @Size(max = 2000) String url, @NotEmpty List<@NotBlank String> events) {}

    public record WebhookView(UUID id, String url, List<String> events, boolean active, Instant createdAt, String secret) {}

    public record DeliveryView(UUID id, UUID eventId, String eventType, String status, int attempts, Instant nextAttemptAt,
            Integer lastStatusCode, String lastError, Instant deliveredAt, Instant createdAt) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantAccess access;
    private final SecretBox box;
    private final Egress egress;
    private final WebhookEvents events;
    private final Auditor auditor;

    public WebhookResource(TenantAccess access, SecretBox box, Egress egress, WebhookEvents events, Auditor auditor) {
        this.access = access;
        this.box = box;
        this.egress = egress;
        this.events = events;
        this.auditor = auditor;
    }

    @POST
    @Transactional
    public Response create(@PathParam("tenantId") UUID tenantId, @Valid CreateWebhook request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        egress.check(request.url());
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.id = Ids.newId();
        subscription.tenantId = tenantId;
        subscription.url = request.url();
        subscription.events = request.events().toArray(String[]::new);
        subscription.seal(box.seal(secret.getBytes(StandardCharsets.UTF_8), subscription.context()));
        subscription.createdAt = Instant.now();
        subscription.updatedAt = subscription.createdAt;
        subscription.persist();
        auditor.record(scope, "webhook.created", "webhook", subscription.id, Map.of("url", request.url()));
        return Response.status(201).entity(view(subscription, secret)).build();
    }

    @GET
    @Transactional
    public List<WebhookView> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        List<WebhookSubscription> subscriptions = WebhookSubscription.list("deletedAt is null order by createdAt");
        return subscriptions.stream().map(s -> view(s, null)).toList();
    }

    @DELETE
    @Path("/{webhookId}")
    @Transactional
    public Response delete(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        WebhookSubscription subscription = find(webhookId);
        subscription.deletedAt = Instant.now();
        subscription.active = false;
        auditor.record(scope, "webhook.deleted", "webhook", webhookId);
        return Response.noContent().build();
    }

    /** Sends a {@code webhook.test} event to this subscription only (webhook tools, §26.2). */
    @POST
    @Path("/{webhookId}/test")
    @Transactional
    public DeliveryView test(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        WebhookSubscription subscription = find(webhookId);
        auditor.record(scope, "webhook.test_sent", "webhook", webhookId);
        Map<String, Object> event = Map.of("specversion", "1.0", "id", Ids.newId().toString(), "source", WebhookEvents.SOURCE,
                "type", "webhook.test", "subject", "xh:webhook:" + webhookId, "time", Instant.now().toString(),
                "datacontenttype", "application/json", "data", Map.of("message", "Evento de prueba de xhandler"));
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.id = Ids.newId();
        delivery.tenantId = tenantId;
        delivery.subscriptionId = subscription.id;
        delivery.eventId = UUID.fromString((String) event.get("id"));
        delivery.eventType = "webhook.test";
        delivery.payload = event;
        delivery.nextAttemptAt = Instant.now();
        delivery.createdAt = delivery.nextAttemptAt;
        delivery.updatedAt = delivery.nextAttemptAt;
        delivery.persist();
        return view(delivery);
    }

    @GET
    @Path("/{webhookId}/deliveries")
    @Transactional
    public Page<DeliveryView> deliveries(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId,
            @QueryParam("status") String status, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        find(webhookId);
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        List<WebhookDelivery> page = (status == null
                        ? WebhookDelivery.<WebhookDelivery>find("subscriptionId = ?1 order by createdAt desc", webhookId)
                        : WebhookDelivery.<WebhookDelivery>find("subscriptionId = ?1 and status = ?2 order by createdAt desc", webhookId, status))
                .range(start, start + size - 1)
                .list();
        return new Page<>(page.stream().map(WebhookResource::view).toList(), size, start);
    }

    @POST
    @Path("/{webhookId}/deliveries/{deliveryId}/redeliver")
    @Transactional
    public DeliveryView redeliver(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId,
            @PathParam("deliveryId") UUID deliveryId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        WebhookDelivery delivery = WebhookDelivery.findById(deliveryId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (delivery == null || !delivery.subscriptionId.equals(webhookId)) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "webhook.redelivered", "webhook_delivery", deliveryId);
        delivery.status = "pending";
        delivery.attempts = 0;
        delivery.nextAttemptAt = Instant.now();
        delivery.updatedAt = delivery.nextAttemptAt;
        return view(delivery);
    }

    private static WebhookSubscription find(UUID id) {
        WebhookSubscription subscription = WebhookSubscription.findById(id);
        if (subscription == null || subscription.deletedAt != null) {
            throw ApiException.notFound();
        }
        return subscription;
    }

    private static WebhookView view(WebhookSubscription s, String secret) {
        return new WebhookView(s.id, s.url, List.of(s.events), s.active, s.createdAt, secret);
    }

    private static DeliveryView view(WebhookDelivery d) {
        return new DeliveryView(d.id, d.eventId, d.eventType, d.status, d.attempts, d.nextAttemptAt, d.lastStatusCode, d.lastError,
                d.deliveredAt, d.createdAt);
    }
}
