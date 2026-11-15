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

    public SigningService(Engine engine, KeyVault vault) {
        this.engine = engine;
        this.vault = vault;
    }

    /** Must run with the company's tenant open (reads its signing key). */
    public EngineModels.SignResponse sign(Company company, String xml) {
        Optional<SigningKey> key = SigningKey.active(company.id);
        if (key.isEmpty()) {
            if ("beta".equals(company.sunatEnv)) {
                return engine.signWithDemoKey(xml);
            }
            throw ApiException.invalid("no_certificate", "La empresa no tiene un certificado digital.", null);
        }
        EngineModels.SignPrepareResponse prepared = engine.signPrepare(xml);
        byte[] signature = vault.sign(key.get().keyRef, Base64.getDecoder().decode(prepared.dataToSign()));
        return engine.signFinalize(xml, signature, key.get().certificate);
    }
}
