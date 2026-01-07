import Foundation

struct SipAccountConfig {
    let username: String
    let password: String
    let domain: String
    let wssPort: Int
}

final class SipAccountManager {
    func initializeIfNeeded() throws {
        #if canImport(PJSIP)
        // Initialize PJSIP stack here when available.
        #else
        throw NSError(domain: "SipAccountManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "PJSIP not available"])
        #endif
    }

    func configureAccount(config: SipAccountConfig) throws {
        try initializeIfNeeded()
        #if canImport(PJSIP)
        // Configure transport and register account using WSS.
        #endif
    }
}
