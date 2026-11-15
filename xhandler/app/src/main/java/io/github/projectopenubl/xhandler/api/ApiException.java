package io.github.projectopenubl.xhandler.api;

/**
 * An error for API clients: HTTP status, a stable {@code kind}, a Spanish message, and an optional
 * detail (AGENTS.md: user-facing text is Spanish).
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String kind;
    private final String detail;

    public ApiException(int status, String kind, String message, String detail) {
        super(message);
        this.status = status;
        this.kind = kind;
        this.detail = detail;
    }

    public ApiException(int status, String kind, String message) {
        this(status, kind, message, null);
    }

    public static ApiException notFound() {
        return new ApiException(404, "not_found", "No encontrado.");
    }

    public static ApiException forbidden() {
        return new ApiException(403, "forbidden", "No tiene permiso para realizar esta acción.");
    }

    public static ApiException conflict(String kind, String message) {
        return new ApiException(409, kind, message);
    }

    public static ApiException invalid(String kind, String message, String detail) {
        return new ApiException(422, kind, message, detail);
    }

    public int status() {
        return status;
    }

    public String kind() {
        return kind;
    }

    public String detail() {
        return detail;
    }
}
