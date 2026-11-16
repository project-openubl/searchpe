package io.github.projectopenubl.xhandler.keys;

import io.github.projectopenubl.xhandler.api.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.BadPaddingException;

/**
 * Reads a PKCS#12 (.pfx) keystore. The keystore and its password are only in memory here, never
 * logged or stored (DESIGN.md §6); the caller moves the key into the {@link KeyVault}.
 */
final class CertificateImporter {

    /** RUC in the subject: "RUC 20123456789", "RUC:2012...", or organizationIdentifier "VATPE-2012...". */
    private static final Pattern RUC_LABELED = Pattern.compile("(?:RUC|VATPE)[^0-9]{0,3}(\\d{11})(?!\\d)");
    private static final Pattern RUC_ANY = Pattern.compile("\\b((?:10|15|16|17|20)\\d{9})\\b");

    record Imported(PrivateKey privateKey, X509Certificate certificate, Optional<String> ruc) {}

    private CertificateImporter() {}

    static Imported read(byte[] pkcs12, char[] password, Instant now) {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pkcs12), password);
        } catch (IOException e) {
            if (e.getCause() instanceof BadPaddingException || e.getCause() instanceof java.security.UnrecoverableKeyException) {
                throw wrongPassword();
            }
            throw invalid("No se pudo leer el archivo .pfx.", e.getMessage());
        } catch (GeneralSecurityException e) {
            throw invalid("No se pudo leer el archivo .pfx.", e.getMessage());
        }

        try {
            for (String alias : Collections.list(keyStore.aliases())) {
                if (!keyStore.isKeyEntry(alias)) {
                    continue;
                }
                PrivateKey key = (PrivateKey) keyStore.getKey(alias, password);
                if (!(keyStore.getCertificate(alias) instanceof X509Certificate certificate)) {
                    continue;
                }
                if (!(key instanceof RSAPrivateKey)) {
                    throw invalid("El certificado debe usar una llave RSA.", key.getAlgorithm());
                }
                if (certificate.getNotAfter().toInstant().isBefore(now)) {
                    throw invalid("El certificado digital está vencido.", "valid until " + certificate.getNotAfter().toInstant());
                }
                if (certificate.getNotBefore().toInstant().isAfter(now)) {
                    throw invalid("El certificado digital aún no es válido.", "valid from " + certificate.getNotBefore().toInstant());
                }
                if (!keyMatchesCertificate(key, certificate)) {
                    throw invalid("La llave privada no corresponde al certificado.", null);
                }
                return new Imported(key, certificate, rucOf(certificate.getSubjectX500Principal().getName()));
            }
        } catch (java.security.UnrecoverableKeyException e) {
            throw wrongPassword();
        } catch (GeneralSecurityException e) {
            throw invalid("No se pudo leer la llave privada del certificado.", e.getMessage());
        }
        throw invalid("El archivo .pfx no contiene una llave privada con su certificado.", null);
    }

    private static boolean keyMatchesCertificate(PrivateKey key, X509Certificate certificate) throws GeneralSecurityException {
        byte[] probe = "xhandler-key-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(probe);
        byte[] signature = signer.sign();
        java.security.Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(probe);
        return verifier.verify(signature);
    }

    static Optional<String> rucOf(String subject) {
        Matcher labeled = RUC_LABELED.matcher(subject);
        if (labeled.find()) {
            return Optional.of(labeled.group(1));
        }
        Matcher any = RUC_ANY.matcher(subject);
        return any.find() ? Optional.of(any.group(1)) : Optional.empty();
    }

    private static ApiException wrongPassword() {
        return ApiException.invalid("wrong_password", "La contraseña del certificado digital es incorrecta.", null);
    }

    private static ApiException invalid(String message, String detail) {
        return ApiException.invalid("invalid_certificate", message, detail);
    }
}
