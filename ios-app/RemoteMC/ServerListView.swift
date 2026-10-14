import SwiftUI

struct ServerListView: View {
    @EnvironmentObject private var store: ServerStore
    @State private var adding = false

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.servers) { server in
                    NavigationLink(value: server) { ServerRow(server: server) }
                }
                .onDelete { store.remove(at: $0) }
                .onMove { store.move(from: $0, to: $1) }
            }
            .overlay {
                if store.servers.isEmpty {
                    ContentUnavailableView {
                        Label("No servers yet", systemImage: "server.rack")
                    } description: {
                        Text("Type /remotemc pair on your server and scan the QR code with the iPhone camera – or tap + and enter the code.")
                    } actions: {
                        Button("Add server") { adding = true }.buttonStyle(.borderedProminent)
                    }
                }
            }
            .navigationTitle("RemoteMC")
            .navigationDestination(for: ServerProfile.self) { ServerView(server: $0) }
            .toolbar {
                if !store.servers.isEmpty {
                    ToolbarItem(placement: .topBarLeading) { EditButton() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { adding = true } label: { Label("Add server", systemImage: "plus") }
                }
            }
            .sheet(isPresented: $adding) { PairView(link: nil) }
            .sheet(item: $store.pendingLink) { PairView(link: $0.text) }
        }
    }
}

/// A server in the list with a status dot and the number of players.
private struct ServerRow: View {
    let server: ServerProfile
    @State private var status: ServerStatus?
    @State private var offline = false

    var body: some View {
        HStack(spacing: 14) {
            Text(String(server.name.prefix(1)).uppercased())
                .font(.title2.bold())
                .frame(width: 44, height: 44)
                .background(Color.accentColor.opacity(0.2), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text(server.name).font(.headline).lineLimit(1)
                Text(verbatim: server.address).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            if let status {
                Label { Text(verbatim: "\(status.onlinePlayers)/\(status.maxPlayers)") } icon: { Image(systemName: "person.2") }
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            Circle().fill(status != nil ? Color.green : offline ? Color.red : Color.gray).frame(width: 10, height: 10)
        }
        .task(id: server.id) {
            while !Task.isCancelled {
                do {
                    status = try await Api(server).status()
                    offline = false
                } catch {
                    status = nil
                    offline = true
                }
                try? await Task.sleep(for: .seconds(10))
            }
        }
    }
}
