import XCTest
@testable import XR18Core

final class OSCCodecTests: XCTestCase {
    func testKnownWireVectors() throws {
        XCTAssertEqual(try OSCCodec.enquiry("/xinfo"), Data([47,120,105,110,102,111,0,0]))
        XCTAssertEqual(try OSCCodec.encode(.init(address: "/on", arguments: [.int(1)])), Data([47,111,110,0,44,105,0,0,0,0,0,1]))
        XCTAssertEqual(try OSCCodec.encode(.init(address: "/f", arguments: [.float(0.5)])), Data([47,102,0,0,44,102,0,0,63,0,0,0]))
        XCTAssertEqual(try OSCCodec.decode(try OSCCodec.enquiry("/xremote")), [.init(address: "/xremote")])
    }

    func testUTF8BlobAndSignedRoundTrip() throws {
        let message = OSCMessage(address: "/example", arguments: [.int(.min), .int(.max), .float(-0.125), .string("Pavol Škoda 🎚"), .blob(Data([1, 2, 3, 255, 0]))])
        XCTAssertEqual(try OSCCodec.decode(try OSCCodec.encode(message)), [message])
    }

    func testBundlesFlattenInOrder() throws {
        let first = OSCMessage(address: "/first", arguments: [.int(1)])
        let second = OSCMessage(address: "/second", arguments: [.string("two")])
        let inner = try OSCCodec.encodeBundle([second])
        var outer = try OSCCodec.encodeBundle([first])
        outer.append(contentsOf: lengthPrefix(inner.count)); outer.append(inner)
        XCTAssertEqual(try OSCCodec.decode(outer), [first, second])
    }

    func testEveryTruncationAndTrailingGarbageRejected() throws {
        let valid = try OSCCodec.encode(.init(address: "/value", arguments: [.string("hello"), .float(0.7), .blob(Data([1,2,3]))]))
        // The address alone is intentionally a valid X AIR GET extension.
        for length in 0..<valid.count where length != 8 { XCTAssertThrowsError(try OSCCodec.decode(Data(valid.prefix(length))), "length \(length)") }
        var extra = valid; extra.append(contentsOf: [0, 0, 0, 0])
        XCTAssertThrowsError(try OSCCodec.decode(extra))
    }

    func testBadPaddingInvalidUTF8AndUnsupportedTagsAreRejected() throws {
        var invalidPadding = try OSCCodec.encode(.init(address: "/a", arguments: [.int(1)]))
        invalidPadding[3] = 1
        XCTAssertThrowsError(try OSCCodec.decode(invalidPadding))
        XCTAssertThrowsError(try OSCCodec.decode(Data([47, 255, 0, 0])))
        XCTAssertThrowsError(try OSCCodec.decode(Data([47, 97, 0, 0, 44, 100, 0, 0])))
        var badBlob = try OSCCodec.encode(.init(address: "/blob", arguments: [.blob(Data([1]))]))
        badBlob[badBlob.count - 1] = 1
        XCTAssertThrowsError(try OSCCodec.decode(badBlob))
        for address in ["", "xinfo", "/bad address", "/bad\0"] {
            XCTAssertThrowsError(try OSCCodec.encode(.init(address: address)))
        }
        XCTAssertThrowsError(try OSCCodec.encode(.init(address: "/s", arguments: [.string("bad\0value")])))
    }

    func testBundleFailureAtomicAndResourceLimits() throws {
        let good = OSCMessage(address: "/ok", arguments: [.int(1)])
        var packet = try OSCCodec.encodeBundle([good])
        packet.append(contentsOf: [0, 0, 0, 4, 47, 255, 0, 0])
        XCTAssertThrowsError(try OSCCodec.decode(packet))
        var nested = try OSCCodec.encode(good)
        for _ in 0...OSCCodec.maximumBundleDepth {
            var wrapper = try OSCCodec.encodeBundle([])
            wrapper.append(contentsOf: lengthPrefix(nested.count)); wrapper.append(nested); nested = wrapper
        }
        XCTAssertThrowsError(try OSCCodec.decode(nested))
        let messages = Array(repeating: good, count: OSCCodec.maximumMessages)
        var full = try OSCCodec.encodeBundle(messages)
        let extra = try OSCCodec.encode(good)
        full.append(contentsOf: lengthPrefix(extra.count)); full.append(extra)
        XCTAssertThrowsError(try OSCCodec.decode(full))
        XCTAssertThrowsError(try OSCCodec.encodeBundle(messages + [good]))
        XCTAssertThrowsError(try OSCCodec.decode(Data(repeating: 0, count: OSCCodec.maximumPacketSize + 4)))
        XCTAssertThrowsError(try OSCCodec.encode(.init(address: "/blob", arguments: [.blob(Data(repeating: 0, count: OSCCodec.maximumPacketSize))])))
    }

    private func lengthPrefix(_ count: Int) -> [UInt8] {
        let n = UInt32(count)
        return [UInt8(truncatingIfNeeded: n >> 24), UInt8(truncatingIfNeeded: n >> 16), UInt8(truncatingIfNeeded: n >> 8), UInt8(truncatingIfNeeded: n)]
    }
}
