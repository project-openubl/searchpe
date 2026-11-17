package io.github.projectopenubl.xhandler.ose;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A misconfigured OSE must fail at start, not sign CDR-OSE with the demo key and forward to the
 * fake (DESIGN.md §11.4): in a production build with the OSE enabled ({@code xhandler.ose.ruc}),
 * the environment must be {@code production} or {@code beta}, unless {@code xhandler.ose.allow-fake}
 * says otherwise (a staging deployment).
 */
@ApplicationScoped
public class OseConfigCheck {

    private final Optional<String> ruc;
    private final String environment;
    private final boolean allowFake;

    public OseConfigCheck(@ConfigProperty(name = "xhandler.ose.ruc") Optional<String> ruc,
            @ConfigProperty(name = "xhandler.ose.environment", defaultValue = "fake") String environment,
            @ConfigProperty(name = "xhandler.ose.allow-fake", defaultValue = "false") boolean allowFake) {
        this.ruc = ruc;
        this.environment = environment;
        this.allowFake = allowFake;
    }

    void onStart(@Observes StartupEvent event) {
        check(LaunchMode.current());
    }

    void check(LaunchMode mode) {
        if (mode != LaunchMode.NORMAL || ruc.isEmpty()) {
            return;
        }
        if (!"production".equals(environment) && !"beta".equals(environment) && !allowFake) {
            throw new IllegalStateException("xhandler.ose.environment is '" + environment
                    + "' in production: set production or beta (or xhandler.ose.allow-fake=true for staging)");
        }
    }
}
