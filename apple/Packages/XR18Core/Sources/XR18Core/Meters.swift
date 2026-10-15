import Foundation

public struct MeterFrame: Equatable {
    public let bank: String
    public let decibels: [Float]
    public init(bank: String, decibels: [Float]) { self.bank = bank; self.decibels = decibels }

    /// Two main meters, one per other supported strip; mute groups have no meter.
    public func levels(for controlID: String) -> [Float] {
        guard let control = ControlCatalog.find(controlID) else { return [] }
        if bank == MeterCodec.dcaBank, control.id.hasPrefix("dca"), let i = Int(control.id.dropFirst(3)), (1...4).contains(i), decibels.count == 4 {
            return [decibels[i - 1]]
        }
        guard bank == MeterCodec.channelBank, decibels.count == 40 else { return [] }
        if control.id == "master" { return [decibels[36], decibels[37]] }
        if control.id.hasPrefix("ch"), let i = Int(control.id.dropFirst(2)), (1...16).contains(i) { return [decibels[i - 1]] }
        if control.id.hasPrefix("bus"), let i = Int(control.id.dropFirst(3)), (1...6).contains(i) { return [decibels[26 + i - 1]] }
        return []
    }
}

public enum MeterCodec {
    public static let channelBank = "/meters/1"
    public static let dcaBank = "/meters/8"
    public static let renewalInterval: TimeInterval = 4
    public static let freshness: TimeInterval = 1

    public static func subscription(_ bank: String) -> OSCMessage? {
        guard bank == channelBank || bank == dcaBank else { return nil }
        return .init(address: "/meters", arguments: [.string(bank)])
    }

    /// X AIR's little-endian count + signed Int16 dB/256 blob, not X32 float meters.
    /// Exactly one OSC blob message of a known bank/count must occupy the datagram.
    public static func decode(_ packet: Data) -> MeterFrame? {
        guard let messages = try? OSCCodec.decode(packet), messages.count == 1,
              let message = messages.first, message.arguments.count == 1,
              case .blob(let data) = message.arguments[0] else { return nil }
        let count: Int
        switch message.address { case channelBank: count = 40; case dcaBank: count = 4; default: return nil }
        // A bundle wrapper is not a manufacturer's meter packet. Restrict exact wire length.
        guard packet.count == 24 + count * 2, data.count == 4 + count * 2 else { return nil }
        let bytes = Array(data)
        let declared = UInt32(bytes[0]) | UInt32(bytes[1]) << 8 | UInt32(bytes[2]) << 16 | UInt32(bytes[3]) << 24
        guard declared == UInt32(count) else { return nil }
        var levels: [Float] = []
        for i in 0..<count {
            let offset = 4 + i * 2
            let raw = UInt16(bytes[offset]) | UInt16(bytes[offset + 1]) << 8
            levels.append(Float(Int16(bitPattern: raw)) / 256)
        }
        return .init(bank: message.address, decibels: levels)
    }
}
