package io.github.projectopenubl.xhandler.keys;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Base64;
import java.util.Optional;

/**
 * Signs documents without the private key leaving the vault: the engine prepares the data to sign,
 * the vault signs it, the engine assembles the signature (DESIGN.md §4.1, R1).
 *
 * <p>Beta companies without their own certificate sign with SUNAT's public demo key.
 */
@ApplicationScoped
public class SigningService {

    private final Engine engine;
    private final KeyVault vault;
    private final HsmExecutor hsm;
    private final PlatformKeys platformKeys;

    public SigningService(Engine engine, KeyVault vault, HsmExecutor hsm, PlatformKeys platformKeys) {
        this.engine = engine;
        this.vault = vault;
        this.hsm = hsm;
        this.platformKeys = platformKeys;
    }

    /** A signed document; {@code signingKeyId} is null for SUNAT's demo key. */
    public record Signed(String xml, String digestValue, java.util.UUID signingKeyId) {}

    /**
     * Must run with the company's tenant open (reads its signing key). Beta and the SUNAT fake
     * accept SUNAT's public demo key when the company has no certificate; production never does.
     */
    public Signed sign(Company company, String xml) {
        return sign(company, company.sunatEnv, xml);
    }

    /** Signs for a given environment (a document's own, which may differ after a switch). */
    public Signed sign(Company company, String sunatEnv, String xml) {
        if ("pse".equals(company.emissionMode)) {
            // As PSE the platform signs with its own certificate (RS 199-2015 art. 5); the issuer stays liable
            Optional<PlatformKeys.Key> pse = platformKeys.active("pse");
            if (pse.isPresent()) {
                EngineModels.SignPrepareResponse prepared = engine.signPrepare(xml);
                byte[] signature = platformKeys.sign(pse.get(), Base64.getDecoder().decode(prepared.dataToSign()));
                EngineModels.SignResponse signed = engine.signFinalize(xml, signature, pse.get().certificate());
                return new Signed(signed.xml(), signed.digestValue(), null);
            }
            if ("production".equals(sunatEnv)) {
                throw ApiException.invalid("no_platform_certificate", "La plataforma aún no tiene su certificado de PSE.", null);
            }
        }
        Optional<SigningKey> key = SigningKey.active(company.id);
        if (key.isEmpty()) {
            if (!"production".equals(sunatEnv)) {
                EngineModels.SignResponse demo = engine.signWithDemoKey(xml);
                return new Signed(demo.xml(), demo.digestValue(), null);
            }
            throw ApiException.invalid("no_certificate", "La empresa no tiene un certificado digital.", null);
        }
        EngineModels.SignPrepareResponse prepared = engine.signPrepare(xml);
        String keyRef = key.get().keyRef;
        byte[] data = Base64.getDecoder().decode(prepared.dataToSign());
        byte[] signature = hsm.call(() -> vault.sign(keyRef, data));
        EngineModels.SignResponse signed = engine.signFinalize(xml, signature, key.get().certificate);
        return new Signed(signed.xml(), signed.digestValue(), key.get().id);
    }
}
