package io.github.projectopenubl.xhandler.keys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a self-signed PKCS#12 for a RUC with the JDK's keytool, so tests don't depend on a fixed
 * RUC (RUCs are unique on the platform, and the test database may outlive a run).
 */
public final class TestCertificates {

    public static final String PASSWORD = "password";

    private TestCertificates() {}

    public static File generate(String ruc) {
        try {
            Path dir = Files.createTempDirectory("xhandler-cert");
            Path pfx = dir.resolve(ruc + ".pfx");
            String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
            Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "empresa", "-keyalg", "RSA", "-keysize", "2048",
                            "-sigalg", "SHA256withRSA", "-validity", "365",
                            "-dname", "CN=EMPRESA DE PRUEBA, OU=RUC " + ruc + ", O=Empresa de Prueba S.A.C., C=PE",
                            "-storetype", "PKCS12", "-keystore", pfx.toString(), "-storepass", PASSWORD)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) {
                throw new IllegalStateException("keytool failed: " + output);
            }
            pfx.toFile().deleteOnExit();
            return pfx.toFile();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
