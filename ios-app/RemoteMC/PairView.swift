import SwiftUI

/// Pairs a server: with the link of /remotemc pair (from the QR code or copied) or by typing the
/// address and the 6-digit code.
struct PairView: View {
    @EnvironmentObject private var store: ServerStore
    @Environment(\.dismiss) private var dismiss
    let link: String?

    @State private var linkText = ""
    @State private var host = ""
    @State private var port = "8765"
    @State private var code = ""
    @State private var name = ""
    @State private var busy = false
    @State private var error: String?

    private var parsed: ConnectLink? { ConnectLink.parse(linkText) }
    private var canPair: Bool {
        parsed != nil || (!host.trimmingCharacters(in: .whitespaces).isEmpty && code.filter(\.isNumber).count == 6 && Int(port) != nil)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(text: $linkText, prompt: Text(verbatim: "remotemc://…")) { Text("Pairing link") }
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Paste link") { linkText = UIPasteboard.general.string ?? "" }
                } header: {
                    Text("Pairing link")
                } footer: {
                    Text("Type /remotemc pair on the server. Scanning its QR code with the iPhone camera opens RemoteMC right away.")
                }
                if parsed == nil {
                    Section("Or enter it by hand") {
                        TextField("Address", text: $host)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                        TextField("Port", text: $port).keyboardType(.numberPad)
                        TextField("6-digit code", text: $code).keyboardType(.numberPad)
                    }
                }
                Section {
                    TextField("Name (optional)", text: $name)
                }
                if let error {
                    Section { Label(error, systemImage: "exclamationmark.triangle").foregroundStyle(.red) }
                }
                Section {
                    Button {
                        Task { await pair() }
                    } label: {
                        HStack {
                            Text("Pair")
                            if busy { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(busy || !canPair)
                }
            }
            .navigationTitle("Add server")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
            .task {
                // Opened from a QR code: pair right away.
                if let link, ConnectLink.parse(link) != nil {
                    linkText = link
                    await pair()
                }
            }
        }
    }

    private func pair() async {
        busy = true
        error = nil
        defer { busy = false }
        let target: ConnectLink
        if let parsed {
            target = parsed
        } else {
            // The plugin uses HTTPS with its own certificate by default.
            target = ConnectLink(
                host: host.trimmingCharacters(in: .whitespaces), port: Int(port) ?? 8765,
                code: code.filter(\.isNumber), token: nil, fingerprint: nil, tls: true
            )
        }
        var server = ServerProfile(
            name: name.trimmingCharacters(in: .whitespaces).isEmpty ? target.host : name.trimmingCharacters(in: .whitespaces),
            host: target.host, port: target.port, token: target.token ?? "", tls: target.tls, fingerprint: target.fingerprint
        )
        do {
            if let code = target.code {
                let api = Api(baseURL: server.baseURL, token: nil, fingerprint: target.fingerprint, trustFirst: target.tls && target.fingerprint == nil)
                let result = try await api.pair(code: code, device: "iPhone")
                server.token = result.token
                if server.fingerprint == nil && server.tls { server.fingerprint = api.pinning.seen }
            }
            // Check that the token works before saving.
            _ = try await Api(server).status()
            store.add(server)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
