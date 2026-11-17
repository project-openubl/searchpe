package io.github.projectopenubl.xhandler.webhooks;

import io.github.projectopenubl.xhandler.api.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Outbound calls to tenant-supplied hosts (DESIGN.md §16, SSRF). In production they go through the
 * egress proxy, which denies private ranges and pins DNS. This class is the in-app layer of the same
 * rule: https only, allowed ports only, and every resolved address must be public (no loopback,
 * private, link-local, CGNAT, unique-local or metadata addresses), so the engine on localhost, cloud
 * metadata and internal services are unreachable even without the proxy.
 */
@ApplicationScoped
public class Egress {

    static final Set<Integer> ALLOWED_PORTS = Set.of(-1, 443, 8443);

    private final HttpClient client;
    private final boolean allowPrivate;

    public Egress(
            @ConfigProperty(name = "xhandler.egress.proxy") Optional<String> proxy,
            @ConfigProperty(name = "xhandler.egress.allow-private", defaultValue = "false") boolean allowPrivate) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER);
        proxy.ifPresent(p -> {
            int colon = p.lastIndexOf(':');
            builder.proxy(ProxySelector.of(new InetSocketAddress(p.substring(0, colon), Integer.parseInt(p.substring(colon + 1)))));
        });
        this.client = builder.build();
        this.allowPrivate = allowPrivate;
    }

    public HttpClient client() {
        return client;
    }

    /** Refuses a URL the platform must not call (422 with a Spanish message). */
    public URI check(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("invalid_url", "La URL no es válida.", null);
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw ApiException.invalid("invalid_url", "La URL no es válida.", null);
        }
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if (!https && !(allowPrivate && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw ApiException.invalid("invalid_url", "La URL debe usar https.", null);
        }
        if (!allowPrivate && !ALLOWED_PORTS.contains(uri.getPort())) {
            throw ApiException.invalid("invalid_url", "Puerto no permitido (use 443 u 8443).", null);
        }
        if (!allowPrivate) {
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(uri.getHost());
            } catch (UnknownHostException e) {
                throw ApiException.invalid("invalid_url", "No se pudo resolver el host de la URL.", null);
            }
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw ApiException.invalid("invalid_url", "La URL apunta a una dirección no permitida.", null);
                }
            }
        }
        return uri;
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            // CGNAT 100.64/10, benchmark 198.18/15, 0/8, 192.0.0/24, metadata 169.254 (link-local above)
            return !(first == 0 || (first == 100 && second >= 64 && second <= 127) || (first == 198 && (second == 18 || second == 19))
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0) || first >= 240);
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xff;
            // Unique local fc00::/7; IPv4-mapped addresses are checked as IPv4
            if ((first & 0xfe) == 0xfc) {
                return false;
            }
            boolean mapped = b[10] == (byte) 0xff && b[11] == (byte) 0xff;
            for (int i = 0; i < 10 && mapped; i++) {
                mapped = b[i] == 0;
            }
            if (mapped) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
