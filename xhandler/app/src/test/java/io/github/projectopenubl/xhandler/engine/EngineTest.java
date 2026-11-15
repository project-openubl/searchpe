package io.github.projectopenubl.xhandler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.Test;

class EngineTest {

    /** An engine client whose every call fails like an unreachable or timed-out engine. */
    private static final EngineClient UNREACHABLE = new EngineClient() {
        @Override
        public EngineModels.BuildResponse build(EngineModels.BuildRequest request) {
            throw new ProcessingException("Connection refused");
        }

        @Override
        public EngineModels.SignResponse sign(EngineModels.SignRequest request) {
            throw new ProcessingException("Connection refused");
        }

        @Override
        public EngineModels.SendResponse send(EngineModels.SendRequest request) {
            throw new ProcessingException("Read timed out");
        }
    };

    @Test
    void unreachable_engine_is_engine_unavailable() {
        Engine engine = new Engine(UNREACHABLE);

        EngineException e = assertThrows(
                EngineException.class, () -> engine.send("<x/>", EngineModels.Environment.BETA));

        assertEquals(502, e.status());
        assertEquals(Engine.UNAVAILABLE, e.kind());
    }

    @Test
    void environment_is_lowercase_regardless_of_locale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals("production", EngineModels.Environment.PRODUCTION.json());
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
