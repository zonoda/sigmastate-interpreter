const {
    ProverSecretObj, SigmaPropProver$,
    ProverHintsObj, SigmaPropVerifierObj
} = require("sigmastate-js/main");

describe("SigmaPropProver", () => {
    let w = 0xadf47e32000fc75e2923dba482c843c7f6b684cbf2ceec5bfdf5fe6d13cabe5dn
    let secret = ProverSecretObj.dlog(w)
    expect(secret.secret()).toEqual(w)

    let p = SigmaPropProver$.withSecrets([secret])
    expect(p).not.toBeUndefined()

    it("generateCommitments", () => {
        let hints = p.generateCommitments(secret.publicKey())
        expect(hints).not.toBeUndefined()
    });

    it("signMessage", () => {
        let message = Int8Array.of(1, 2, 3)
        let signature = p.signMessage(secret.publicKey(), message, ProverHintsObj.empty())
        expect(signature).not.toBeUndefined()
        expect(signature.length).toBeGreaterThan(0)

        let V = SigmaPropVerifierObj.create()
        let ok = V.verifySignature(secret.publicKey(), message, signature)
        expect(ok).toEqual(true)
    });

});