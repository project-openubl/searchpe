package io.github.projectopenubl.xhandler.keys;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "xhandler.vault")
public interface VaultConfig {

    /** {@code pkcs11} (HSM, production) or {@code software} (development and tests only). */
    @WithDefault("pkcs11")
    String kind();

    Software software();

    Pkcs11 pkcs11();

    interface Software {
        /** Master keys: id → base64 of 32 random bytes (AES-256). */
        Map<String, String> masterKeys();

        Optional<String> currentMasterKey();

        /** The software vault refuses to start in production unless this is true. */
        @WithDefault("false")
        boolean allowInProduction();
    }

    interface Pkcs11 {
        /** Path of the PKCS#11 module, e.g. /usr/lib64/pkcs11/libsofthsm2.so */
        Optional<String> library();

        @WithDefault("0")
        int slotListIndex();

        Optional<String> pin();

        /** Id of the master key (AES-256, kept in the HSM) new secrets are wrapped with. */
        @WithDefault("master-1")
        String currentMasterKey();
    }
}
