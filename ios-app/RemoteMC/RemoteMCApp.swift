import SwiftUI

@main
struct RemoteMCApp: App {
    @StateObject private var store = ServerStore()

    var body: some Scene {
        WindowGroup {
            ServerListView()
                .environmentObject(store)
                // The QR code of /remotemc pair, scanned with the camera, opens the app with its link.
                .onOpenURL { url in store.pendingLink = .init(text: url.absoluteString) }
        }
    }
}
