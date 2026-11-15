package io.github.projectopenubl.xhandler.engine;

/** The engine answered with an error. */
public class EngineException extends RuntimeException {

    private final int status;
    private final String kind;

    public EngineException(int status, String kind, String message) {
        super(message);
        this.status = status;
        this.kind = kind;
    }

    public int status() {
        return status;
    }

    public String kind() {
        return kind;
    }
}
