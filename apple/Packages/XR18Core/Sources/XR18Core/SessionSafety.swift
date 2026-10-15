import Foundation

public struct MixerIdentity: Equatable {
    public let advertisedIP: String
    public let name: String
    public let model: String
    public let firmware: String

    /// /xinfo replies are IP, name, supported model, and optional firmware strings.
    public static func parse(_ message: OSCMessage) -> MixerIdentity? {
        guard message.address == "/xinfo", message.arguments.count >= 3,
              case .string(let ip) = message.arguments[0],
              case .string(let name) = message.arguments[1],
              case .string(let rawModel) = message.arguments[2] else { return nil }
        let model = rawModel.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard ["XR18", "MR18", "X18"].contains(model) else { return nil }
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let firmware: String
        if message.arguments.count > 3, case .string(let version) = message.arguments[3] { firmware = version } else { firmware = "" }
        return .init(advertisedIP: ip, name: trimmedName.isEmpty ? model : trimmedName, model: model, firmware: firmware)
    }
}

/// Network-independent, deterministic write gate. Own on a single executor.
/// The transport must filter datagrams to the chosen unicast host and port 10024,
/// and capture `generation` in every callback and immediately executed write.
/// This type never stores pending commands and never changes a value optimistically.
public struct SessionSafety {
    public static let connectionTimeout: TimeInterval = 4
    public static let valueFreshness: TimeInterval = 3.5
    public private(set) var generation = UUID()
    public private(set) var identity: MixerIdentity?
    public private(set) var values: [String: OSCValue] = [:]
    public private(set) var requiredPaths: Set<String> = []
    private var fresh: [String: TimeInterval] = [:]
    private var started: TimeInterval?
    private var lastValid: TimeInterval?

    public init() {}

    /// Starts an isolated connection; values and the previous identity are discarded.
    @discardableResult
    public mutating func begin(selectedIDs: [String], now: TimeInterval) -> UUID {
        disconnect()
        select(selectedIDs)
        if now.isFinite { started = now }
        return generation
    }

    public mutating func disconnect() {
        generation = UUID(); identity = nil; values.removeAll(); fresh.removeAll()
        started = nil; lastValid = nil
    }

    public mutating func select(_ ids: [String]) { requiredPaths = ControlCatalog.paths(for: ids) }

    public mutating func receive(_ messages: [OSCMessage], generation token: UUID, now: TimeInterval) {
        guard now.isFinite, started != nil, token == generation else { return }
        // Late packets can never re-enable the old connection after its deadline.
        guard !expire(at: now), token == generation else { return }
        for message in messages {
            if message.address == "/xinfo" {
                if identity == nil, let verified = MixerIdentity.parse(message) {
                    identity = verified; lastValid = now
                }
                continue
            }
            guard identity != nil, message.arguments.count == 1,
                  let value = ControlCatalog.validated(path: message.address, value: message.arguments[0]) else { continue }
            values[message.address] = value; fresh[message.address] = now; lastValid = now
        }
    }

    public func isConnected(at now: TimeInterval) -> Bool {
        guard identity != nil, let time = lastValid, now.isFinite else { return false }
        return now >= time && now - time <= Self.connectionTimeout
    }

    public func isReady(at now: TimeInterval) -> Bool {
        guard isConnected(at: now), !requiredPaths.isEmpty else { return false }
        return requiredPaths.allSatisfy { path in
            guard let time = fresh[path] else { return false }
            return now >= time && now - time <= Self.valueFreshness
        }
    }

    /// Call periodically and before consuming a response. Expiration rotates the generation.
    /// A transport must start a new socket/session via begin() before reconnecting.
    @discardableResult
    public mutating func expire(at now: TimeInterval) -> Bool {
        guard let origin = lastValid ?? started else { return false }
        guard now.isFinite, now >= origin, now - origin <= Self.connectionTimeout else {
            disconnect(); return true
        }
        return false
    }

    /// Evaluate immediately on the transport executor; return nil means discard permanently.
    /// Query the path after sending; the receive echo/readback alone updates `values`.
    public func command(path: String, value: OSCValue, generation token: UUID, now: TimeInterval) -> OSCMessage? {
        guard token == generation, requiredPaths.contains(path), isReady(at: now),
              let value = ControlCatalog.validated(path: path, value: value) else { return nil }
        return OSCMessage(address: path, arguments: [value])
    }
}
