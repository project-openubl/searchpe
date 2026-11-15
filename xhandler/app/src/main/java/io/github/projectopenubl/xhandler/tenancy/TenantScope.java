package io.github.projectopenubl.xhandler.tenancy;

import java.util.UUID;

/** The tenant a request works in, who is acting, and with which role. */
public record TenantScope(UUID tenantId, Actor actor, Role role) {}
