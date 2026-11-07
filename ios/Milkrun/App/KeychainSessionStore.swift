@preconcurrency import MilkrunKit
import Foundation
import Security

/// Shift credentials in the Keychain (this device only, after first unlock), so the driver token
/// is never stored in plain UserDefaults and does not travel with backups to another device.
final class KeychainSessionStore: SessionStore {
    private let account = "milkrun.session"

    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "com.milkrun.driver",
         kSecAttrAccount as String: account]
    }

    func load() -> DevSession? {
        var q = query
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data, let json = String(data: data, encoding: .utf8) else { return nil }
        return Wire.shared.decodeSession(json: json)
    }

    func save(session: DevSession) {
        let data = Data(Wire.shared.encodeSession(session: session).utf8)
        SecItemDelete(query as CFDictionary)
        var q = query
        q[kSecValueData as String] = data
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }

    func clear() {
        SecItemDelete(query as CFDictionary)
    }
}
