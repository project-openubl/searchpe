package io.github.projectopenubl.xhandler.tenancy;

/**
 * Who is calling: a person ({@code kind=user}, subject = token {@code sub}) or a machine client
 * ({@code kind=client}, subject = token {@code azp}, the client id).
 */
public record Actor(String kind, String subject, boolean emailVerified, boolean platformSupport) {

    /** Platform work (workers, schedules, the admin CLI's jobs). */
    public static final Actor PLATFORM = new Actor("platform", "worker", false, false);

    public boolean isUser() {
        return "user".equals(kind);
    }

    /** Audit log identity, e.g. {@code user:4f3c…} or {@code client:acme-erp}. */
    public String auditName() {
        return kind + ":" + subject;
    }
}
