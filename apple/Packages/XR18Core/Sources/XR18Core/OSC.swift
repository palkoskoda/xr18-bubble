import Foundation

public enum OSCValue: Equatable {
    case int(Int32), float(Float), string(String), blob(Data)
}

public struct OSCMessage: Equatable {
    public let address: String
    public let arguments: [OSCValue]
    public init(address: String, arguments: [OSCValue] = []) { self.address = address; self.arguments = arguments }
}

public enum OSCError: Error, Equatable { case malformed, tooLarge, invalidAddress, unsupportedType }

/// Atomic, bounded OSC codec. Bundle timetags are ignored for immediate mixer controls.
/// Unknown argument types invalidate the complete datagram, including preceding bundle elements.
public enum OSCCodec {
    public static let maximumPacketSize = 65_536
    public static let maximumMessages = 1_024
    public static let maximumBundleDepth = 16

    public static func encode(_ message: OSCMessage) throws -> Data {
        try requireAddress(message.address)
        var result = Data()
        try appendString(message.address, to: &result)
        var tags = ","
        for value in message.arguments {
            switch value { case .int: tags += "i"; case .float: tags += "f"; case .string: tags += "s"; case .blob: tags += "b" }
        }
        try appendString(tags, to: &result)
        for value in message.arguments {
            switch value {
            case .int(let n): appendInteger(UInt32(bitPattern: n), to: &result)
            case .float(let n): appendInteger(n.bitPattern, to: &result)
            case .string(let s): try appendString(s, to: &result)
            case .blob(let bytes):
                guard bytes.count <= maximumPacketSize else { throw OSCError.tooLarge }
                appendInteger(UInt32(bytes.count), to: &result)
                result.append(bytes)
                while result.count % 4 != 0 { result.append(0) }
            }
            guard result.count <= maximumPacketSize else { throw OSCError.tooLarge }
        }
        return result
    }

    /// X AIR's address-only enquiry extension (not a SET).
    public static func enquiry(_ address: String) throws -> Data {
        try requireAddress(address)
        var result = Data(); try appendString(address, to: &result); return result
    }

    public static func encodeBundle(_ messages: [OSCMessage]) throws -> Data {
        guard messages.count <= maximumMessages else { throw OSCError.tooLarge }
        var result = Data(); try appendString("#bundle", to: &result)
        result.append(contentsOf: [0, 0, 0, 0, 0, 0, 0, 1])
        for message in messages {
            let data = try encode(message)
            appendInteger(UInt32(data.count), to: &result); result.append(data)
            guard result.count <= maximumPacketSize else { throw OSCError.tooLarge }
        }
        return result
    }

    public static func decode(_ data: Data) throws -> [OSCMessage] {
        guard !data.isEmpty, data.count <= maximumPacketSize, data.count % 4 == 0 else { throw OSCError.malformed }
        let bytes = Array(data)
        var reader = Reader(bytes: bytes, offset: 0, end: bytes.count)
        var result: [OSCMessage] = []
        try decodePacket(&reader, depth: 0, result: &result)
        return result
    }

    private static func decodePacket(_ reader: inout Reader, depth: Int, result: inout [OSCMessage]) throws {
        guard depth <= maximumBundleDepth else { throw OSCError.malformed }
        let address = try reader.string()
        if address == "#bundle" {
            try reader.skip(8)
            while reader.remaining > 0 {
                let size = Int(try reader.integer())
                guard size > 0, size % 4 == 0, size <= reader.remaining else { throw OSCError.malformed }
                var element = Reader(bytes: reader.bytes, offset: reader.offset, end: reader.offset + size)
                try reader.skip(size)
                try decodePacket(&element, depth: depth + 1, result: &result)
            }
            return
        }
        try requireAddress(address)
        var values: [OSCValue] = []
        if reader.remaining > 0 {
            let tags = try reader.string()
            guard tags.first == "," else { throw OSCError.malformed }
            for type in tags.dropFirst() {
                switch type {
                case "i": values.append(.int(Int32(bitPattern: try reader.integer())))
                case "f": values.append(.float(Float(bitPattern: try reader.integer())))
                case "s": values.append(.string(try reader.string()))
                case "b": values.append(.blob(try reader.blob()))
                default: throw OSCError.unsupportedType
                }
            }
        }
        guard reader.remaining == 0, result.count < maximumMessages else { throw OSCError.malformed }
        result.append(.init(address: address, arguments: values))
    }

    private static func requireAddress(_ address: String) throws {
        guard address.hasPrefix("/"), !address.contains("\0"), !address.contains(" ") else { throw OSCError.invalidAddress }
    }
    private static func appendInteger(_ number: UInt32, to data: inout Data) {
        data.append(UInt8(truncatingIfNeeded: number >> 24)); data.append(UInt8(truncatingIfNeeded: number >> 16))
        data.append(UInt8(truncatingIfNeeded: number >> 8)); data.append(UInt8(truncatingIfNeeded: number))
    }
    private static func appendString(_ string: String, to data: inout Data) throws {
        guard !string.contains("\0") else { throw OSCError.malformed }
        guard string.utf8.count < maximumPacketSize else { throw OSCError.tooLarge }
        data.append(contentsOf: string.utf8); data.append(0)
        while data.count % 4 != 0 { data.append(0) }
        guard data.count <= maximumPacketSize else { throw OSCError.tooLarge }
    }

    private struct Reader {
        let bytes: [UInt8]
        var offset: Int
        let end: Int
        var remaining: Int { end - offset }
        mutating func skip(_ n: Int) throws {
            guard n >= 0, n <= remaining else { throw OSCError.malformed }; offset += n
        }
        mutating func integer() throws -> UInt32 {
            guard remaining >= 4 else { throw OSCError.malformed }
            let value = UInt32(bytes[offset]) << 24 | UInt32(bytes[offset + 1]) << 16 | UInt32(bytes[offset + 2]) << 8 | UInt32(bytes[offset + 3])
            offset += 4; return value
        }
        mutating func string() throws -> String {
            let start = offset
            while offset < end, bytes[offset] != 0 { offset += 1 }
            guard offset < end else { throw OSCError.malformed }
            let stringEnd = offset
            let padded = (stringEnd - start + 4) & ~3
            guard padded <= end - start else { throw OSCError.malformed }
            guard bytes[stringEnd..<(start + padded)].allSatisfy({ $0 == 0 }),
                  let result = String(bytes: bytes[start..<stringEnd], encoding: .utf8) else { throw OSCError.malformed }
            offset = start + padded; return result
        }
        mutating func blob() throws -> Data {
            let size = Int(try integer())
            guard size <= remaining else { throw OSCError.malformed }
            let padded = (size + 3) & ~3
            guard padded <= remaining, bytes[(offset + size)..<(offset + padded)].allSatisfy({ $0 == 0 }) else { throw OSCError.malformed }
            let result = Data(bytes[offset..<(offset + size)])
            offset += padded; return result
        }
    }
}
