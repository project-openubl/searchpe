package io.github.projectopenubl.xhandler.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.api.ApiException;
import java.net.InetAddress;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The in-app SSRF guard (DESIGN.md §16). */
class EgressTest {

    static boolean isPublic(String address) throws Exception {
        return Egress.isPublic(InetAddress.getByName(address));
    }

    @Test
    void private_and_special_addresses_are_refused() throws Exception {
        for (String address : new String[] {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "::1", "fd00::1", "fe80::1", "::ffff:10.0.0.1", "64:ff9b::a00:1", "2002:a00:1::", "2001:0:a00:1::"}) {
            assertFalse(isPublic(address), address);
        }
        assertTrue(isPublic("8.8.8.8"));
        assertTrue(isPublic("2606:4700:4700::1111"));
    }

    @Test
    void production_rules() {
        Egress strict = new Egress(Optional.of("proxy.internal:3128"), false, true);
        assertEquals("invalid_url", assertThrows(ApiException.class, () -> strict.check("http://example.com/hook")).kind());
        assertEquals("invalid_url", assertThrows(ApiException.class, () -> strict.check("https://example.com:22/hook")).kind());
        assertEquals("invalid_url", assertThrows(ApiException.class, () -> strict.check("https://user:pw@example.com/")).kind());
        assertEquals("invalid_url", assertThrows(ApiException.class, () -> strict.check("https://127.0.0.1/")).kind());
        Egress noProxy = new Egress(Optional.empty(), false, true);
        assertEquals("egress_unavailable", assertThrows(ApiException.class, () -> noProxy.check("https://example.com/")).kind());
    }
}
