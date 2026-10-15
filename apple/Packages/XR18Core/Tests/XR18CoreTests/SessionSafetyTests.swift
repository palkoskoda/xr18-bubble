import XCTest
@testable import XR18Core

final class SessionSafetyTests: XCTestCase {
    private let fader = "/lr/mix/fader"
    private let mute = "/lr/mix/on"
    private func info(_ model: String = "XR18") -> OSCMessage {
        .init(address: "/xinfo", arguments: [.string("192.168.1.1"), .string("Stage"), .string(model), .string("1.20")])
    }
    private func state() -> [OSCMessage] {
        [.init(address: fader, arguments: [.float(0.75)]), .init(address: mute, arguments: [.int(1)])]
    }

    func testVerifiedHandshakeAndEverySelectedPathRequired() {
        var session = SessionSafety()
        let token = session.begin(selectedIDs: ["master", "mute1"], now: 10)
        XCTAssertNil(session.command(path: fader, value: .float(1), generation: token, now: 10))
        session.receive(state(), generation: token, now: 10.1)
        XCTAssertTrue(session.values.isEmpty)
        session.receive([info("XR16")], generation: token, now: 10.2)
        XCTAssertNil(session.identity)
        session.receive([info()], generation: token, now: 10.3)
        XCTAssertTrue(session.isConnected(at: 10.3))
        XCTAssertFalse(session.isReady(at: 10.3))
        session.receive(state(), generation: token, now: 10.4)
        XCTAssertFalse(session.isReady(at: 10.4))
        session.receive([.init(address: "/config/mute/1", arguments: [.int(0)])], generation: token, now: 10.5)
        XCTAssertTrue(session.isReady(at: 10.5))
        XCTAssertNotNil(session.command(path: fader, value: .float(0.4), generation: token, now: 10.6))
        XCTAssertEqual(session.values[fader], .float(0.75), "SET creation must not optimistically change confirmed state")
        XCTAssertNil(session.command(path: "/ch/01/mix/fader", value: .float(0.4), generation: token, now: 10.6))
    }

    func testSupportedIdentityAndMalformedIdentity() {
        for model in ["XR18", "X18", "MR18", " xr18 "] { XCTAssertNotNil(MixerIdentity.parse(info(model))) }
        for model in ["XR12", "XR16", "X32", "", "XR18x"] { XCTAssertNil(MixerIdentity.parse(info(model))) }
        XCTAssertNil(MixerIdentity.parse(.init(address: "/xinfo", arguments: [.string("ip"), .int(1), .string("XR18")])))
        XCTAssertNil(MixerIdentity.parse(.init(address: "/info", arguments: info().arguments)))
    }

    func testInvalidAndWrongGenerationValuesDoNotUnlockOrExtendFreshness() {
        var session = SessionSafety()
        let token = session.begin(selectedIDs: ["master"], now: 0)
        session.receive([info()] + state(), generation: UUID(), now: 0.1)
        XCTAssertNil(session.identity)
        session.receive([info()], generation: token, now: 0.1)
        let invalid: [OSCMessage] = [.init(address: fader, arguments: [.int(1)]), .init(address: fader, arguments: [.float(.nan)]),
                                    .init(address: fader, arguments: [.float(.infinity)]), .init(address: fader, arguments: [.float(-0.01)]),
                                    .init(address: fader, arguments: [.float(1.01)]), .init(address: mute, arguments: [.int(2)]),
                                    .init(address: mute, arguments: [.float(1)]), .init(address: fader, arguments: [.float(0.4), .int(1)])]
        session.receive(invalid, generation: token, now: 1)
        XCTAssertTrue(session.values.isEmpty)
        XCTAssertFalse(session.isReady(at: 1))
        session.receive(state(), generation: token, now: 1)
        XCTAssertTrue(session.isReady(at: 1))
        for value in [OSCValue.int(1), .float(.nan), .float(.infinity), .float(-1), .float(2)] {
            XCTAssertNil(session.command(path: fader, value: value, generation: token, now: 1))
        }
        session.receive(invalid + [info()], generation: token, now: 4.6)
        XCTAssertFalse(session.isReady(at: 4.6))
        XCTAssertTrue(session.isConnected(at: 4.6))
        XCTAssertTrue(session.expire(at: 5.1), "Invalid values and repeated xinfo must not keep a dead mixer alive")
        XCTAssertNotEqual(session.generation, token)
    }

    func testTimeoutResetsGenerationAndLatePacketCannotRevive() {
        var session = SessionSafety()
        let first = session.begin(selectedIDs: ["master"], now: 100)
        session.receive([info()] + state(), generation: first, now: 100.1)
        XCTAssertFalse(session.isReady(at: 103.61))
        XCTAssertNil(session.command(path: fader, value: .float(1), generation: first, now: 103.61))
        session.receive(state(), generation: first, now: 104.2)
        XCTAssertNotEqual(session.generation, first)
        XCTAssertNil(session.identity)
        XCTAssertTrue(session.values.isEmpty)
        let second = session.begin(selectedIDs: ["master"], now: 105)
        session.receive([info()] + state(), generation: first, now: 105.1)
        XCTAssertFalse(session.isReady(at: 105.1))
        session.receive([info()] + state(), generation: second, now: 105.2)
        XCTAssertTrue(session.isReady(at: 105.2))
        XCTAssertNil(session.command(path: fader, value: .float(1), generation: first, now: 105.2), "No write captured from a previous connection may be replayed")
        XCTAssertNotNil(session.command(path: fader, value: .float(1), generation: second, now: 105.2))
        session.disconnect()
        XCTAssertTrue(session.values.isEmpty)
        XCTAssertFalse(session.isReady(at: 105.3))
    }

    func testSelectionChangesNeedNewStateAndEmptySelectionNeverWrites() {
        var session = SessionSafety()
        let token = session.begin(selectedIDs: ["master"], now: 0)
        session.receive([info()] + state(), generation: token, now: 0.1)
        session.select(["master", "dca1"])
        XCTAssertFalse(session.isReady(at: 0.2))
        session.receive([.init(address: "/dca/1/fader", arguments: [.float(0.5)]), .init(address: "/dca/1/on", arguments: [.int(1)])], generation: token, now: 0.2)
        XCTAssertTrue(session.isReady(at: 0.2))
        session.select([])
        XCTAssertFalse(session.isReady(at: 0.3))
        XCTAssertNil(session.command(path: fader, value: .float(0), generation: token, now: 0.3))
    }

    func testSilentHandshakeTimeoutAndClockRegressionFailClosed() {
        var session = SessionSafety()
        let token = session.begin(selectedIDs: ["master"], now: 10)
        XCTAssertTrue(session.expire(at: 14.01))
        XCTAssertNotEqual(token, session.generation)
        let next = session.begin(selectedIDs: ["master"], now: 20)
        session.receive([info()] + state(), generation: next, now: 20.1)
        XCTAssertFalse(session.isReady(at: 19))
        XCTAssertTrue(session.expire(at: 19))
    }
}
