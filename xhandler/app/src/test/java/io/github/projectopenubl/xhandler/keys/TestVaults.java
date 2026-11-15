package io.github.projectopenubl.xhandler.keys;

import java.util.Map;

/** Software vaults with the test profile's master keys, for tests outside this package. */
public final class TestVaults {

    public static final Map<String, String> MASTER_KEYS = Map.of(
            "test-1", "dGVzdC1tYXN0ZXIta2V5LTEtMDAwMDAwMDAwMDAwMDA=",
            "test-2", "dGVzdC1tYXN0ZXIta2V5LTItMDAwMDAwMDAwMDAwMDA=");

    private TestVaults() {}

    public static KeyVault software(String currentMasterKey) {
        return new SoftwareKeyVault(MASTER_KEYS, currentMasterKey);
    }
}
