package io.github.projectopenubl.xhandler.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A minimal S3 client (PUT, GET, HEAD, DELETE with AWS Signature V4), enough for immutable,
 * content-addressed objects. Thread-safe: {@link HttpClient} is, and nothing else is shared.
 */
public class S3FileStore implements FileStore {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final S3Config config;
    private final HttpClient http;

    public S3FileStore(S3Config config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
    }

    @Override
    public Stored put(UUID tenantId, byte[] content) {
        String sha256 = FileStore.sha256(content);
        String key = FileStore.keyOf(tenantId, sha256);
        // Content-addressed: if it is there, it is these bytes
        if (send("HEAD", key, null).statusCode() != 200) {
            HttpResponse<byte[]> response = send("PUT", key, content);
            if (response.statusCode() / 100 != 2) {
                throw new StorageException("S3 PUT failed with HTTP " + response.statusCode());
            }
        }
        return new Stored(key, sha256, content.length);
    }

    @Override
    public byte[] get(String key) {
        HttpResponse<byte[]> response = send("GET", key, null);
        if (response.statusCode() != 200) {
            throw new StorageException("S3 GET failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    @Override
    public void delete(String key) {
        HttpResponse<byte[]> response = send("DELETE", key, null);
        if (response.statusCode() / 100 != 2 && response.statusCode() != 404) {
            throw new StorageException("S3 DELETE failed with HTTP " + response.statusCode());
        }
    }

    @Override
    public boolean healthy() {
        try {
            int status = send("HEAD", "", null).statusCode();
            return status == 200 || status == 403;
        } catch (StorageException e) {
            return false;
        }
    }

    private HttpResponse<byte[]> send(String method, String key, byte[] body) {
        byte[] payload = body == null ? new byte[0] : body;
        String payloadHash = FileStore.sha256(payload);
        URI endpoint = URI.create(config.endpoint());
        String path = "/" + config.bucket() + (key.isEmpty() ? "" : "/" + key);
        URI uri = endpoint.resolve(path);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String host = endpoint.getPort() == -1 ? endpoint.getHost() : endpoint.getHost() + ":" + endpoint.getPort();

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(config.timeout())
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (config.accessKey().isPresent() && config.secretKey().isPresent()) {
            String scope = DATE.format(now) + "/" + config.region() + "/s3/aws4_request";
            String canonical = method + "\n" + path + "\n\n"
                    + "host:" + host + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + amzDate + "\n\n"
                    + "host;x-amz-content-sha256;x-amz-date\n" + payloadHash;
            String toSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                    + FileStore.sha256(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = hmac(hmac(hmac(hmac(("AWS4" + config.secretKey().get()).getBytes(StandardCharsets.UTF_8),
                    DATE.format(now)), config.region()), "s3"), "aws4_request");
            String signature = HexFormat.of().formatHex(hmac(signingKey, toSign));
            request.header("Authorization", "AWS4-HMAC-SHA256 Credential=" + config.accessKey().get() + "/" + scope
                    + ", SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=" + signature);
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new StorageException("S3 " + method + " failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("interrupted");
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Storage failed; the caller's transaction should roll back. */
    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }
    }
}
