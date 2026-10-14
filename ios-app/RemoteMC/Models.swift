import Foundation

/// A paired server. Saved in the keychain, because it holds the access token.
struct ServerProfile: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var name: String
    var host: String
    var port: Int
    var token: String
    /// HTTPS with the plugin's own certificate (or a real one behind a proxy).
    var tls: Bool
    /// SHA-256 of the plugin's certificate (base64url, like the phone app); nil = normal checks.
    var fingerprint: String?

    var address: String { "\(host):\(port)" }

    var baseURL: String {
        let h = host.contains(":") && !host.hasPrefix("[") ? "[\(host)]" : host
        return "\(tls ? "https" : "http")://\(h):\(port)"
    }
}

/// Contents of the QR code / link from `/remotemc pair`:
/// `remotemc://host:port?code=123456&fp=<sha256>` (or `tls=0` without encryption).
struct ConnectLink {
    var host: String
    var port: Int
    var code: String?
    var token: String?
    var fingerprint: String?
    var tls: Bool

    static func parse(_ text: String) -> ConnectLink? {
        guard let url = URLComponents(string: text.trimmingCharacters(in: .whitespacesAndNewlines)),
              let scheme = url.scheme?.lowercased(), ["remotemc", "mcremote"].contains(scheme),
              var host = url.host, !host.isEmpty else { return nil }
        host = host.trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
        var query: [String: String] = [:]
        for item in url.queryItems ?? [] { query[item.name] = item.value ?? "" }
        let code = query["code"].map { $0.filter(\.isNumber) }.flatMap { $0.count == 6 ? $0 : nil }
        let token = query["token"].flatMap { $0.isEmpty ? nil : $0 }
        if code == nil && token == nil { return nil }
        let fp = query["fp"].flatMap { $0.isEmpty ? nil : $0 }
        let tls: Bool
        if query["tls"] == "0" { tls = false } else if fp != nil { tls = true } else { tls = query["https"] == "1" }
        let port = url.port.flatMap { (1...65535).contains($0) ? $0 : nil } ?? 8765
        return ConnectLink(host: host, port: port, code: code, token: token, fingerprint: fp, tls: tls)
    }
}

/// Live status of a server (`/api/v1/status`).
struct ServerStatus {
    var motd: String
    var platform: String
    var minecraftVersion: String
    var onlinePlayers: Int
    var maxPlayers: Int
    var tps: Double?
    var memoryUsedMb: Int
    var memoryMaxMb: Int
    var uptimeSeconds: Int

    init(_ o: [String: Any]) {
        motd = o.string("motd")
        platform = o.string("platform")
        minecraftVersion = o.string("minecraftVersion")
        onlinePlayers = o.int("onlinePlayers")
        maxPlayers = o.int("maxPlayers")
        tps = (o["tps"] as? [NSNumber])?.first?.doubleValue
        memoryUsedMb = o.int("memoryUsedMb")
        memoryMaxMb = o.int("memoryMaxMb")
        uptimeSeconds = o.int("uptimeSeconds")
    }

    /// The MOTD without Minecraft colour codes, on one line.
    var plainMotd: String {
        motd.replacingOccurrences(of: "§.", with: "", options: .regularExpression)
            .split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.joined(separator: " ")
    }
}

/// A player online (`/api/v1/players`).
struct PlayerInfo: Identifiable {
    var name: String
    var world: String
    var ping: Int
    var gameMode: String
    var id: String { name }

    init(_ o: [String: Any]) {
        name = o.string("name")
        world = o.string("world")
        ping = o.int("ping")
        gameMode = o.string("gameMode")
    }
}

extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String { (self[key] as? String) ?? "" }
    func int(_ key: String) -> Int { (self[key] as? NSNumber)?.intValue ?? 0 }
}
