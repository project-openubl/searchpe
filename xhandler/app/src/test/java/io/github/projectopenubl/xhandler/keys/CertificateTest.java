package io.github.projectopenubl.xhandler.keys;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.createCompany;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class CertificateTest {

    private static final File VALID = new File("src/test/resources/certificates/valid-20123456789.pfx");
    private static final File EXPIRED = new File("src/test/resources/certificates/expired-10467793549.pfx");
    private static final String INVOICE = "{\"kind\":\"Invoice\",\"spec\":{\"serie_numero\":\"F001-1\"}}";

    @BeforeEach
    void reset() {
        EngineStub.reset();
    }

    private static io.restassured.response.ValidatableResponse upload(UUID tenant, UUID company, File pfx, String password) {
        return given().auth().oauth2(new KeycloakTestClient().getAccessToken("alice"))
                .multiPart("file", pfx, "application/x-pkcs12")
                .multiPart("password", password)
                .put("/api/v1/tenants/" + tenant + "/companies/" + company + "/certificate")
                .then();
    }

    @Test
    void certificate_import_verifies_the_ruc_and_documents_are_signed_in_the_vault() throws Exception {
        UUID tenant = tenantOf("alice");
        String ruc = randomRuc();
        UUID company = createCompany("alice", tenant, ruc);
        File pfx = TestCertificates.generate(ruc);

        upload(tenant, company, pfx, TestCertificates.PASSWORD).statusCode(200)
                .body("ruc", equalTo(ruc))
                .body("ruc_matches", equalTo(true))
                .body("valid_until", notNullValue());
        as("alice").get("/api/v1/tenants/" + tenant + "/companies/" + company)
                .then().body("ruc_verified_at", notNullValue());

        // Signing goes engine prepare -> vault -> engine finalize; the key never reaches the engine
        as("alice").body(INVOICE).post("/api/v1/tenants/" + tenant + "/companies/" + company + "/dev/documents")
                .then().statusCode(200).body("digest_value", equalTo("digest-123"));

        JsonNode finalize = new ObjectMapper().readTree(EngineStub.lastBody("/v1/sign/finalize"));
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(finalize.get("certificate").asText())));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update("data-to-sign".getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(finalize.get("signature_value").asText())));
        assertTrue(certificate.getSubjectX500Principal().getName().contains("RUC " + ruc));
        assertTrue(EngineStub.requests().stream().noneMatch(r -> r.body().contains("PRIVATE")));

        // Uploading again replaces the active key
        upload(tenant, company, pfx, TestCertificates.PASSWORD).statusCode(200);
    }

    @Test
    void beta_company_without_certificate_signs_with_the_demo_key() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());

        as("alice").body(INVOICE).post("/api/v1/tenants/" + tenant + "/companies/" + company + "/dev/documents")
                .then().statusCode(200).body("digest_value", equalTo("digest-demo"));
        assertTrue(EngineStub.lastBody("/v1/sign").contains("\"demo_key\":true"));
    }

    @Test
    void expired_certificate_is_rejected() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());
        upload(tenant, company, EXPIRED, "password").statusCode(422)
                .body("kind", equalTo("invalid_certificate"))
                .body("message", equalTo("El certificado digital está vencido."));
    }

    @Test
    void wrong_password_is_rejected() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());
        upload(tenant, company, VALID, "not-the-password").statusCode(422).body("kind", equalTo("wrong_password"));
    }

    @Test
    void ruc_is_read_from_common_subject_formats() {
        assertEquals("20123456789", CertificateImporter.rucOf("CN=X, OU=RUC 20123456789, C=PE").orElseThrow());
        assertEquals("20123456789", CertificateImporter.rucOf("SERIALNUMBER=RUC:20123456789, CN=X").orElseThrow());
        assertEquals("20123456789", CertificateImporter.rucOf("OID.2.5.4.97=VATPE-20123456789, CN=X").orElseThrow());
        assertEquals("10467793549", CertificateImporter.rucOf("OU=DNI 9999999 RUC 10467793549 - DEMO").orElseThrow());
        assertTrue(CertificateImporter.rucOf("CN=Sin RUC, C=PE").isEmpty());
    }

    @Test
    void software_vault_signs_and_rewraps_keys() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        java.security.KeyPair pair = generator.generateKeyPair();

        KeyVault old = TestVaults.software("test-1");
        String ref = old.storePrivateKey(pair.getPrivate(), null);
        assertTrue(ref.startsWith("sw:test-1:"));

        KeyVault current = TestVaults.software("test-2");
        String rewrapped = current.rewrapPrivateKey(ref);
        assertTrue(rewrapped.startsWith("sw:test-2:"));

        byte[] data = "hola".getBytes(StandardCharsets.UTF_8);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(data);
        assertTrue(verifier.verify(current.sign(rewrapped, data)));
    }
}
