import SwiftUI

/// One server: status, players online and a field to run a console command.
struct ServerView: View {
    let server: ServerProfile
    @State private var status: ServerStatus?
    @State private var players: [PlayerInfo] = []
    @State private var error: String?
    @State private var command = ""
    @State private var output: [String] = []
    @State private var sending = false

    var body: some View {
        List {
            if let error {
                Section { Label(error, systemImage: "wifi.exclamationmark").foregroundStyle(.red) }
            }
            if let status {
                Section("Status") {
                    if !status.plainMotd.isEmpty { Text(verbatim: status.plainMotd) }
                    LabeledContent("Players") { Text(verbatim: "\(status.onlinePlayers)/\(status.maxPlayers)") }
                    if let tps = status.tps {
                        LabeledContent("TPS") {
                            Text(verbatim: String(format: "%.1f", tps)).foregroundStyle(tps >= 18 ? Color.green : tps >= 15 ? Color.orange : Color.red)
                        }
                    }
                    if status.memoryMaxMb > 0 {
                        VStack(alignment: .leading, spacing: 6) {
                            LabeledContent("Memory") { Text(verbatim: "\(status.memoryUsedMb) / \(status.memoryMaxMb) MB") }
                            ProgressView(value: Double(status.memoryUsedMb), total: Double(status.memoryMaxMb))
                        }
                    }
                    LabeledContent("Version") { Text(verbatim: "\(status.platform) \(status.minecraftVersion)") }
                    LabeledContent("Uptime") { Text(verbatim: Self.duration(status.uptimeSeconds)) }
                }
                Section("Players online") {
                    if players.isEmpty {
                        Text("Nobody is online.").foregroundStyle(.secondary)
                    }
                    ForEach(players) { player in
                        HStack(spacing: 12) {
                            AsyncImage(url: URL(string: "https://minotar.net/helm/\(player.name)/64.png")) { image in
                                image.resizable().interpolation(.none)
                            } placeholder: {
                                Color.secondary.opacity(0.2)
                            }
                            .frame(width: 32, height: 32)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                            VStack(alignment: .leading) {
                                Text(verbatim: player.name).font(.body.weight(.medium))
                                Text(verbatim: player.world).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(verbatim: "\(player.ping) ms").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            } else if error == nil {
                Section { HStack { Spacer(); ProgressView(); Spacer() } }
            }
            Section {
                HStack {
                    TextField("Command, e.g. say Hello", text: $command)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onSubmit { Task { await send() } }
                    Button {
                        Task { await send() }
                    } label: {
                        Image(systemName: "paperplane.fill")
                    }
                    .disabled(sending || command.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                ForEach(Array(output.enumerated()), id: \.offset) { _, line in
                    Text(verbatim: line).font(.caption.monospaced())
                }
            } header: {
                Text("Console command")
            }
        }
        .navigationTitle(server.name)
        .refreshable { await load() }
        .task {
            while !Task.isCancelled {
                await load()
                try? await Task.sleep(for: .seconds(5))
            }
        }
    }

    private func load() async {
        let api = Api(server)
        do {
            status = try await api.status()
            players = try await api.players()
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func send() async {
        let text = command.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !text.isEmpty else { return }
        sending = true
        defer { sending = false }
        do {
            let lines = try await Api(server).command(text)
            output = ["> /\(text)"] + (lines.isEmpty ? [String(localized: "Done (no output).")] : lines.map {
                $0.replacingOccurrences(of: "§.", with: "", options: .regularExpression)
            })
            command = ""
        } catch {
            output = ["> /\(text)", error.localizedDescription]
        }
    }

    private static func duration(_ seconds: Int) -> String {
        let f = DateComponentsFormatter()
        f.allowedUnits = seconds >= 86_400 ? [.day, .hour] : [.hour, .minute]
        f.unitsStyle = .abbreviated
        return f.string(from: TimeInterval(seconds)) ?? "\(seconds) s"
    }
}
