import CryptoKit
import Foundation
import Security

struct ApiError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// Checks the server's certificate by its SHA-256 fingerprint, like the phone app does: the plugin
/// makes its own certificate, so the usual checks (issuer, name) would always fail.
final class PinningDelegate: NSObject, URLSessionDelegate {
    let expected: String?
    /// While pairing by address there is no fingerprint yet: the first one seen is trusted and saved.
    let trustFirst: Bool
    private(set) var seen: String?

    init(expected: String?, trustFirst: Bool) {
        self.expected = expected
        self.trustFirst = trustFirst
    }

    static func fingerprint(_ certificate: SecCertificate) -> String {
        let der = SecCertificateCopyData(certificate) as Data
        return Data(SHA256.hash(data: der)).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        guard expected != nil || trustFirst else {
            // No fingerprint saved: a real certificate (e.g. behind a reverse proxy), normal checks.
            completionHandler(.performDefaultHandling, nil)
            return
        }
        guard let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate], let leaf = chain.first else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        let actual = Self.fingerprint(leaf)
        seen = actual
        if let expected, actual != expected {
            completionHandler(.cancelAuthenticationChallenge, nil)
        } else {
            completionHandler(.useCredential, URLCredential(trust: trust))
        }
    }
}

/// Talks to the RemoteMC plugin (same API as the phone and Windows apps).
final class Api {
    private let baseURL: String
    private let token: String?
    let pinning: PinningDelegate
    private let session: URLSession

    init(baseURL: String, token: String?, fingerprint: String?, trustFirst: Bool = false) {
        self.baseURL = baseURL
        self.token = token
        pinning = PinningDelegate(expected: fingerprint, trustFirst: trustFirst)
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 10
        session = URLSession(configuration: config, delegate: pinning, delegateQueue: nil)
    }

    convenience init(_ server: ServerProfile) {
        self.init(baseURL: server.baseURL, token: server.token, fingerprint: server.fingerprint)
    }

    deinit { session.finishTasksAndInvalidate() }

    func status() async throws -> ServerStatus { ServerStatus(try await call("GET", "/api/v1/status")) }

    func players() async throws -> [PlayerInfo] {
        let list = try await call("GET", "/api/v1/players")["players"] as? [[String: Any]] ?? []
        return list.map(PlayerInfo.init)
    }

    /// Runs a console command; returns what it printed (if the server could capture it).
    func command(_ text: String) async throws -> [String] {
        let o = try await call("POST", "/api/v1/command", body: ["command": text])
        return o["output"] as? [String] ?? []
    }

    /// Exchanges a pairing code for this phone's own token; returns the token and the server's name.
    func pair(code: String, device: String) async throws -> (token: String, name: String) {
        _ = try await call("GET", "/api/v1/ping")
        let o = try await call("POST", "/api/v1/pair", body: ["code": code, "device": device])
        guard let token = o["token"] as? String, !token.isEmpty else {
            throw ApiError(message: String(localized: "The server sent an unexpected answer."))
        }
        return (token, o.string("name"))
    }

    private func call(_ method: String, _ path: String, body: [String: Any]? = nil) async throws -> [String: Any] {
        guard let url = URL(string: baseURL + path) else { throw ApiError(message: String(localized: "Invalid address")) }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if method == "POST" {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body ?? [:])
        }
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError {
            if let expected = pinning.expected, let seen = pinning.seen, seen != expected {
                throw ApiError(message: String(localized: "The server's certificate has changed. Pair the server again."))
            }
            switch error.code {
            case .timedOut, .cannotConnectToHost, .cannotFindHost, .networkConnectionLost, .notConnectedToInternet:
                throw ApiError(message: String(localized: "The server can't be reached. Is it running, and is the port open?"))
            default:
                throw ApiError(message: error.localizedDescription)
            }
        }
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            if status == 401 { throw ApiError(message: String(localized: "Access was revoked or the code is wrong. Pair the server again.")) }
            let message = json?.string("message") ?? ""
            throw ApiError(message: message.isEmpty ? "HTTP \(status) \(json?.string("error") ?? "")" : message)
        }
        guard let json else { throw ApiError(message: String(localized: "The server sent an unexpected answer.")) }
        return json
    }
}
