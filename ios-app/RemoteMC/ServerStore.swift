import Foundation
import Security

/// The paired servers, kept in the keychain (they contain the access tokens).
@MainActor
final class ServerStore: ObservableObject {
    @Published private(set) var servers: [ServerProfile] = []
    /// A pairing link opened from the camera (QR code) that is waiting to be paired.
    @Published var pendingLink: PendingLink?

    struct PendingLink: Identifiable {
        let id = UUID()
        let text: String
    }

    private let service = "de.remotemc.ios"
    private let account = "servers"

    init() {
        if let data = load(), let list = try? JSONDecoder().decode([ServerProfile].self, from: data) {
            servers = list
        }
    }

    func add(_ server: ServerProfile) {
        // Pairing the same address again replaces the old entry.
        servers.removeAll { $0.host == server.host && $0.port == server.port }
        servers.append(server)
        save()
    }

    func remove(at offsets: IndexSet) {
        servers.remove(atOffsets: offsets)
        save()
    }

    func move(from source: IndexSet, to destination: Int) {
        servers.move(fromOffsets: source, toOffset: destination)
        save()
    }

    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }

    private func load() -> Data? {
        var q = query
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        return SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess ? result as? Data : nil
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(servers) else { return }
        SecItemDelete(query as CFDictionary)
        var q = query
        q[kSecValueData as String] = data
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }
}
