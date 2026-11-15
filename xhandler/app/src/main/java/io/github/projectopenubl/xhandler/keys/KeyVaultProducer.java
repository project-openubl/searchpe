package io.github.projectopenubl.xhandler.keys;

import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Builds the configured {@link KeyVault}. */
public class KeyVaultProducer {

    @Produces
    @ApplicationScoped
    KeyVault keyVault(VaultConfig config) {
        return switch (config.kind()) {
            case "software" -> {
                if (LaunchMode.current() == LaunchMode.NORMAL && !config.software().allowInProduction()) {
                    throw new IllegalStateException(
                            "xhandler.vault.kind=software is for development and tests; use pkcs11 (an HSM) in production");
                }
                String current = config.software().currentMasterKey()
                        .orElseThrow(() -> new IllegalStateException("xhandler.vault.software.current-master-key is required"));
                yield new SoftwareKeyVault(config.software().masterKeys(), current);
            }
            case "pkcs11" -> new Pkcs11KeyVault(
                    config.pkcs11().library().orElseThrow(() -> new IllegalStateException("xhandler.vault.pkcs11.library is required")),
                    config.pkcs11().slotListIndex(),
                    config.pkcs11().pin().orElseThrow(() -> new IllegalStateException("xhandler.vault.pkcs11.pin is required")).toCharArray(),
                    config.pkcs11().currentMasterKey());
            default -> throw new IllegalStateException("unknown xhandler.vault.kind: " + config.kind());
        };
    }
}
