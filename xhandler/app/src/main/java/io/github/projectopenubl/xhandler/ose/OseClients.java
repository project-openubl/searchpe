package io.github.projectopenubl.xhandler.ose;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Who sends us documents to check (DESIGN.md §11.4 step 1; RS 117-2017 art. 7.9: web services
 * "con usuario y contraseña"). Passwords are PBKDF2-hashed; 5 failed attempts lock the user for
 * 15 minutes. Failures are counted in their own transaction, so a refused request still counts.
 */
@ApplicationScoped
public class OseClients {

    public record Client(UUID id, String issuerRuc, String username, UUID tenantId) {}

    /** A refused login: SUNAT's code 0102 for the issuer, nothing about why. */
    public static final class Unauthorized extends RuntimeException {
        Unauthorized() {
            super("Usuario o contraseña incorrectos");
        }
    }

    static final int MAX_FAILURES = 5;
    private static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EntityManager em;

    public OseClients(EntityManager em) {
        this.em = em;
    }

    public Client authenticate(String username, String password) {
        if (username == null || password == null || username.isBlank() || password.isEmpty() || username.length() > 100
                || password.length() > 200) {
            throw new Unauthorized();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                        SELECT id, issuer_ruc, username, tenant_id, password_hash, status, locked_until > now()
                        FROM xhandler.ose_client WHERE username = :u""").setParameter("u", username).getResultList());
        if (rows.isEmpty()) {
            // Same cost as a real check: no timing oracle on which usernames exist
            verify(password, "pbkdf2$" + ITERATIONS + "$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
            throw new Unauthorized();
        }
        Object[] r = rows.get(0);
        UUID id = (UUID) r[0];
        if (!"active".equals(r[5]) || Boolean.TRUE.equals(r[6]) || !verify(password, (String) r[4])) {
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                            UPDATE xhandler.ose_client SET failed_attempts = failed_attempts + 1,
                                locked_until = CASE WHEN failed_attempts + 1 >= :max THEN now() + interval '15 minutes' ELSE locked_until END
                            WHERE id = :id""").setParameter("max", MAX_FAILURES).setParameter("id", id).executeUpdate());
            throw new Unauthorized();
        }
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "UPDATE xhandler.ose_client SET failed_attempts = 0, locked_until = NULL, last_login_at = now() WHERE id = :id")
                .setParameter("id", id).executeUpdate());
        return new Client(id, (String) r[1], (String) r[2], (UUID) r[3]);
    }

    /** By username only: the SFTP server authenticated the user already. */
    public java.util.Optional<Client> byUsername(String username) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                        "SELECT id, issuer_ruc, username, tenant_id FROM xhandler.ose_client WHERE username = :u AND status = 'active'")
                .setParameter("u", username).getResultList());
        return rows.stream().findFirst().map(r -> new Client((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3]));
    }

    public static String newPassword() {
        byte[] raw = new byte[18];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }

    static boolean verify(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, Base64.getDecoder().decode(parts[2]), Integer.parseInt(parts[1]));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
