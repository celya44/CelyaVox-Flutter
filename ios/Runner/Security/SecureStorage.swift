import Foundation
import Security

final class SecureStorage {
    private func query(for key: String) -> [String: Any] {
        return [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
    }

    func set(_ value: String, for key: String) throws {
        let data = Data(value.utf8)
        var attributes = query(for: key)
        attributes[kSecValueData as String] = data

        SecItemDelete(attributes as CFDictionary)
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil) }
    }

    func get(_ key: String) throws -> String? {
        var attributes = query(for: key)
        attributes[kSecReturnData as String] = kCFBooleanTrue
        attributes[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(attributes as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil)
        }
        return String(data: data, encoding: .utf8)
    }

    func saveSipPassword(_ value: String) throws { try set(value, for: Keys.sipPassword) }
    func sipPassword() throws -> String? { try get(Keys.sipPassword) }

    func saveApiKey(_ value: String) throws { try set(value, for: Keys.apiKey) }
    func apiKey() throws -> String? { try get(Keys.apiKey) }

    func saveLdapPassword(_ value: String) throws { try set(value, for: Keys.ldapPassword) }
    func ldapPassword() throws -> String? { try get(Keys.ldapPassword) }

    private enum Keys {
        static let sipPassword = "sip_password"
        static let apiKey = "api_key"
        static let ldapPassword = "ldap_password"
    }
}
