import Foundation

final class ProvisioningManager {
    private let storage: SecureStorage
    private let parser: ProvisioningXmlParser
    private let sipManager: SipAccountManager
    private let userDefaults: UserDefaults

    init(
        storage: SecureStorage = SecureStorage(),
        parser: ProvisioningXmlParser = ProvisioningXmlParser(),
        sipManager: SipAccountManager = SipAccountManager(),
        userDefaults: UserDefaults = .standard
    ) {
        self.storage = storage
        self.parser = parser
        self.sipManager = sipManager
        self.userDefaults = userDefaults
    }

    func start(url: URL, completion: @escaping (Result<ProvisioningConfig, Error>) -> Void) {
        let task = URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
            guard let self else { return }
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let data = data else {
                completion(.failure(NSError(domain: "Provisioning", code: -1, userInfo: [NSLocalizedDescriptionKey: "Empty response"])))
                return
            }
            do {
                let config = self.parser.parse(data: data)
                try self.store(config)
                try self.configureSip(config)
                self.userDefaults.set(true, forKey: Self.provisionedKey)
                completion(.success(config))
            } catch {
                completion(.failure(error))
            }
        }
        task.resume()
    }

    private func store(_ config: ProvisioningConfig) throws {
        if let sipPassword = first(config, keys: ["sip_password", "SipPassword"]) {
            try storage.saveSipPassword(sipPassword)
        }
        if let apiKey = config.get("api_key") { try storage.saveApiKey(apiKey) }
        if let ldapPassword = config.get("ldap_password") { try storage.saveLdapPassword(ldapPassword) }
    }

    private func configureSip(_ config: ProvisioningConfig) throws {
        guard let username = first(config, keys: ["sip_username", "SipUsername"]),
              let password = first(config, keys: ["sip_password", "SipPassword"]),
              let domain = first(config, keys: ["sip_domain", "SipDomaine", "SipDomain"]) else {
            throw NSError(domain: "Provisioning", code: -2, userInfo: [NSLocalizedDescriptionKey: "Missing SIP credentials"])
        }
        let port = Int(config.get("sip_wss_port") ?? "") ?? 443
        let accountConfig = SipAccountConfig(username: username, password: password, domain: domain, wssPort: port)
        try sipManager.configureAccount(config: accountConfig)
    }

    private func first(_ config: ProvisioningConfig, keys: [String]) -> String? {
        for key in keys {
            if let value = config.get(key), !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return value
            }
        }
        return nil
    }

    func isProvisioned() -> Bool {
        userDefaults.bool(forKey: Self.provisionedKey)
    }

    private static let provisionedKey = "is_provisioned"
}
