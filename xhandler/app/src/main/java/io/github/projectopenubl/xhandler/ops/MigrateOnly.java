package io.github.projectopenubl.xhandler.ops;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * The migration Job (deploy/base/migrate-job.yaml, DESIGN.md §15.1): with
 * {@code xhandler.migrate-only=true} the process runs Flyway as the schema owner at start, then
 * exits before anything else (the role check, workers) starts. Never set it on serving pods.
 */
@ApplicationScoped
public class MigrateOnly {

    private final boolean migrateOnly;

    public MigrateOnly(@ConfigProperty(name = "xhandler.migrate-only", defaultValue = "false") boolean migrateOnly) {
        this.migrateOnly = migrateOnly;
    }

    public boolean active() {
        return migrateOnly;
    }

    void onStart(@Observes @Priority(1) StartupEvent event) {
        if (migrateOnly) {
            Logger.getLogger(MigrateOnly.class).info("migrations applied; exiting (migrate-only)");
            Quarkus.asyncExit(0);
        }
    }
}
