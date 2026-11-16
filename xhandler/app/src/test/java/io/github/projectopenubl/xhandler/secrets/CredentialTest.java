package io.github.projectopenubl.xhandler.secrets;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.createCompany;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.AuditLog;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.keys.TestVaults;
import io.github.projectopenubl.xhandler.tenancy.Actor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class CredentialTest {

    @Inject
    TenantAccess access;

    @Inject
    CredentialService credentials;

    @Inject
    KeyRotation rotation;

    @Test
    void sol_credentials_are_encrypted_masked_and_audited() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());
        String path = "/api/v1/tenants/" + tenant + "/companies/" + company + "/credentials";

        as("alice").body(Map.of("username", "20123456789MODDATOS", "password", "s3cr3t-clave"))
                .put(path + "/sol").then().statusCode(200)
                .body("label", equalTo("2012…ATOS"))
                .body(not(containsString("s3cr3t-clave")));
        as("alice").get(path).then().statusCode(200)
                .body("find { it.kind == 'sol' }.label", equalTo("2012…ATOS"))
                .body(not(containsString("s3cr3t-clave")));

        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            Credential stored = Credential.find("companyId = ?1 and kind = 'sol'", company).firstResult();
            String raw = new String(stored.ciphertext, StandardCharsets.ISO_8859_1);
            assertFalse(raw.contains("s3cr3t-clave"));
            assertEquals("test-2", stored.masterKeyId);

            Map<String, String> read = credentials.read(platform(tenant), Company.findById(company), "sol", "test").orElseThrow();
            assertEquals("s3cr3t-clave", read.get("password"));
            assertTrue(AuditLog.count("action = 'credential.read' and objectId = ?1", stored.id.toString()) >= 1);
            assertTrue(AuditLog.count("action = 'credential.stored' and objectId = ?1", stored.id.toString()) >= 1);
        });
    }

    @Test
    void viewers_cant_write_credentials() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());
        as("bob").body(Map.of("username", "x", "password", "y"))
                .put("/api/v1/tenants/" + tenant + "/companies/" + company + "/credentials/sol")
                .then().statusCode(404);
    }

    @Test
    void rotation_rewraps_secrets_sealed_with_an_old_master_key() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());

        // A secret sealed with the previous master key (test-1); the app's current one is test-2
        SecretBox oldBox = new SecretBox(TestVaults.software("test-1"));
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenant);
            Credential c = new Credential();
            c.id = Ids.newId();
            c.tenantId = tenant;
            c.companyId = company;
            c.kind = "smtp";
            c.label = "smtp";
            c.seal(oldBox.seal("{\"password\":\"vieja\"}".getBytes(StandardCharsets.UTF_8), c.context()));
            c.persist();
            return c.id;
        });

        KeyRotation.Result result = rotation.rewrapAll();
        assertTrue(result.credentials() >= 1);

        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            Credential c = Credential.findById(id);
            assertEquals("test-2", c.masterKeyId);
            Map<String, String> read = credentials.read(platform(tenant), Company.findById(company), "smtp", "test").orElseThrow();
            assertEquals("vieja", read.get("password"));
        });
    }

    @Test
    void a_ciphertext_only_opens_in_its_own_row() {
        SecretBox box = new SecretBox(TestVaults.software("test-2"));
        SecretBox.Sealed sealed = box.seal("clave".getBytes(StandardCharsets.UTF_8), "credential:a");
        assertEquals("clave", new String(box.open(sealed, "credential:a"), StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> box.open(sealed, "credential:b"));
    }

    private static TenantScope platform(UUID tenant) {
        return new TenantScope(tenant, new Actor("client", "test", false, false), Role.OWNER);
    }
}
