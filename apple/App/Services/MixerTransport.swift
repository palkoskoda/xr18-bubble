import Foundation
import Network
import XR18Core

/// A connected UDP socket filters the peer. Every callback belongs to one connection.
@MainActor final class MixerTransport {
    var onPacket: ((Data) -> Void)?
    var onReady: (() -> Void)?
    var onFailure: ((String) -> Void)?
    private var connection: NWConnection?
    private var token = UUID()
    private var ready = false
    func start(host: String) {
        stop()
        let current = token
        let parameters = NWParameters.udp
        #if !targetEnvironment(simulator)
        parameters.requiredInterfaceType = .wifi
        #endif
        let socket = NWConnection(host: NWEndpoint.Host(host), port: 10024, using: parameters)
        connection = socket
        socket.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                guard let self, self.token == current else { return }
                switch state {
                case .ready: self.ready = true; self.onReady?()
                case .failed(let error): self.ready = false; self.onFailure?(error.localizedDescription)
                case .waiting(let error): self.ready = false; self.onFailure?(error.localizedDescription)
                default: break
                }
            }
        }
        socket.start(queue: DispatchQueue(label: "xr18.apple.udp"))
        receive(socket, token: current)
    }
    func stop() {
        token = UUID(); ready = false
        connection?.stateUpdateHandler = nil
        connection?.cancel(); connection = nil
    }
    func send(_ message: OSCMessage) {
        guard ready, let connection,
              let data = try? (message.arguments.isEmpty ? OSCCodec.enquiry(message.address) : OSCCodec.encode(message)) else { return }
        connection.send(content: data, completion: .contentProcessed { _ in })
    }
    private func receive(_ socket: NWConnection, token current: UUID) {
        socket.receiveMessage { [weak self] data, _, _, error in
            Task { @MainActor in
                guard let self, self.token == current else { return }
                if let data { self.onPacket?(data) }
                if let error { self.onFailure?(error.localizedDescription) }
                else { self.receive(socket, token: current) }
            }
        }
    }
}
