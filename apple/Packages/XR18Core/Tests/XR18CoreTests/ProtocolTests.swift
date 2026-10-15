import XCTest
@testable import XR18Core

final class ProtocolTests: XCTestCase {
    func testAll31ControlsAndWirePaths() {
        XCTAssertEqual(ControlCatalog.all.count, 31)
        XCTAssertEqual(Set(ControlCatalog.all.map(\.id)).count, 31)
        XCTAssertEqual(ControlCatalog.find("ch1")?.faderPath, "/ch/01/mix/fader")
        XCTAssertEqual(ControlCatalog.find("ch16")?.faderPath, "/ch/16/mix/fader")
        XCTAssertEqual(ControlCatalog.find("bus6")?.faderPath, "/bus/6/mix/fader")
        XCTAssertEqual(ControlCatalog.find("main"), ControlCatalog.find("master"))
        XCTAssertNil(ControlCatalog.find("ch17"))
        XCTAssertEqual(ControlCatalog.paths(for: ControlCatalog.all.map(\.id)).count, 58)
        for control in ControlCatalog.all {
            for muted in [false, true] { XCTAssertEqual(control.isMuted(wireValue: control.muteValue(muted: muted)), muted) }
            if control.id.hasPrefix("mute") { XCTAssertNil(control.faderPath); XCTAssertFalse(control.muteInverted) }
            else { XCTAssertTrue(control.muteInverted) }
        }
        XCTAssertEqual(ControlCatalog.find("mute1")?.muteValue(muted: true), 1)
        XCTAssertEqual(ControlCatalog.find("master")?.muteValue(muted: true), 0)
    }

    func testTaperBreakpointsAndRoundTrips() {
        let points: [(Float, Float)] = [(0, -90), (0.0625, -60), (0.25, -30), (0.5, -10), (0.75, 0), (1, 10)]
        for (fader, db) in points {
            XCTAssertEqual(XAir.faderToDB(fader), db, accuracy: 0.0001)
            XCTAssertEqual(XAir.dbToFader(db), fader, accuracy: 0.0001)
        }
        var previous: Float = -.infinity
        for i in 0...10_000 {
            let value = Float(i) / 10_000
            let db = XAir.faderToDB(value)
            XCTAssertGreaterThanOrEqual(db, previous)
            XCTAssertEqual(XAir.dbToFader(db), value, accuracy: 0.000001)
            previous = db
        }
        XCTAssertEqual(XAir.dbToFader(-.infinity), 0)
        XCTAssertEqual(XAir.dbToFader(.infinity), 1)
        XCTAssertEqual(XAir.faderToDB(-.infinity), -90)
        XCTAssertEqual(XAir.faderToDB(.infinity), 10)
        XCTAssertTrue(XAir.faderToDB(.nan).isNaN)
        XCTAssertTrue(XAir.dbToFader(.nan).isNaN)
    }

    func testProfilePersistenceContainsNoCredentialField() throws {
        let profile = MixerProfile()
        XCTAssertEqual(profile.controls, ["master"])
        let data = try JSONEncoder().encode(profile)
        XCTAssertEqual(try JSONDecoder().decode(MixerProfile.self, from: data), profile)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(json.keys), Set(["id", "name", "host", "wifiSSID", "returnSSID", "controls"]))
    }

    func testMeterBanksSignedEndianAndRouting() throws {
        let channel = try XCTUnwrap(MeterCodec.decode(meterPacket(bank: MeterCodec.channelBank, count: 40)))
        XCTAssertEqual(channel.decibels[0], -128)
        XCTAssertEqual(channel.decibels[1], -60)
        XCTAssertEqual(channel.decibels[2], 10)
        XCTAssertEqual(channel.levels(for: "ch1"), [-128])
        XCTAssertEqual(channel.levels(for: "bus1"), [26])
        XCTAssertEqual(channel.levels(for: "master"), [36, 37])
        XCTAssertEqual(channel.levels(for: "dca1"), [])
        XCTAssertEqual(channel.levels(for: "mute1"), [])
        let dca = try XCTUnwrap(MeterCodec.decode(meterPacket(bank: MeterCodec.dcaBank, count: 4)))
        XCTAssertEqual(dca.levels(for: "dca4"), [3])
        XCTAssertEqual(dca.levels(for: "master"), [])
        XCTAssertEqual(MeterCodec.subscription(MeterCodec.channelBank), OSCMessage(address: "/meters", arguments: [.string("/meters/1")]))
        XCTAssertNil(MeterCodec.subscription("/meters/2"))
    }

    func testMalformedMetersRejectedWithoutPartialFrame() throws {
        let valid = try meterPacket(bank: MeterCodec.channelBank, count: 40)
        for count in 0..<valid.count { XCTAssertNil(MeterCodec.decode(Data(valid.prefix(count)))) }
        var wrongCount = valid; wrongCount[20] = 39
        XCTAssertNil(MeterCodec.decode(wrongCount))
        var wrongEndian = valid; wrongEndian[20] = 0; wrongEndian[23] = 40
        XCTAssertNil(MeterCodec.decode(wrongEndian))
        var wrongSize = valid; wrongSize[19] = 83
        XCTAssertNil(MeterCodec.decode(wrongSize))
        XCTAssertNil(MeterCodec.decode(try meterPacket(bank: "/meters/2", count: 40)))
        XCTAssertNil(MeterCodec.decode(try meterPacket(bank: MeterCodec.dcaBank, count: 40)))
        let message = try XCTUnwrap(try OSCCodec.decode(valid).first)
        XCTAssertNil(MeterCodec.decode(try OSCCodec.encodeBundle([message])))
        XCTAssertNil(MeterCodec.decode(try OSCCodec.encode(.init(address: MeterCodec.channelBank, arguments: [.float(0.5)]))))
    }

    private func meterPacket(bank: String, count: Int) throws -> Data {
        var bytes = Data([UInt8(count), 0, 0, 0])
        for i in 0..<count {
            let raw: Int16 = i == 0 ? .min : i == 1 ? -60 * 256 : i == 2 ? 10 * 256 : Int16(i * 256)
            let number = UInt16(bitPattern: raw)
            bytes.append(UInt8(truncatingIfNeeded: number)); bytes.append(UInt8(truncatingIfNeeded: number >> 8))
        }
        return try OSCCodec.encode(.init(address: bank, arguments: [.blob(bytes)]))
    }
}
