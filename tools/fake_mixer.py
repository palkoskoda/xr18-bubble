"""Small XR18-like OSC/UDP fake mixer for integration tests.

This intentionally implements only the control paths needed by the companion
application.  It is a deterministic test double, not an XR18 emulator.
"""

from __future__ import annotations

import argparse
import json
import math
import socket
import struct
import threading
import time
from pathlib import Path
from typing import Any


def _pad(value: bytes) -> bytes:
    return value + b"\0" * ((4 - len(value) % 4) % 4)


def osc_string(value: str) -> bytes:
    return _pad(value.encode("utf-8") + b"\0")


def osc_encode(path: str, *args: Any) -> bytes:
    """Encode one OSC message, supporting only str/int/float arguments."""
    tags = ["s" if isinstance(v, str) else "i" if isinstance(v, int) else "f" for v in args]
    payload = bytearray(osc_string(path) + osc_string("," + "".join(tags)))
    for value, tag in zip(args, tags):
        payload.extend(osc_string(value) if tag == "s" else struct.pack(">i", value) if tag == "i" else struct.pack(">f", value))
    return bytes(payload)


def _read_string(packet: bytes, offset: int) -> tuple[str, int]:
    end = packet.index(b"\0", offset)
    value = packet[offset:end].decode("utf-8")
    return value, (end + 4) & ~3


def osc_decode(packet: bytes) -> tuple[str, list[Any]]:
    path, offset = _read_string(packet, 0)
    # X AIR also accepts address-only enquiries.
    if offset == len(packet):
        return path, []
    tags, offset = _read_string(packet, offset)
    if not tags.startswith(","):
        raise ValueError("invalid OSC type tag")
    args: list[Any] = []
    for tag in tags[1:]:
        if tag == "s":
            value, offset = _read_string(packet, offset)
            args.append(value)
        elif tag == "i":
            args.append(struct.unpack_from(">i", packet, offset)[0])
            offset += 4
        elif tag == "f":
            args.append(struct.unpack_from(">f", packet, offset)[0])
            offset += 4
        else:
            raise ValueError(f"unsupported OSC type: {tag}")
    return path, args


class FakeMixer:
    def __init__(self, host: str = "0.0.0.0", port: int = 10024, log: str | None = None,
                 withhold: list[str] | None = None, meters: bool = False,
                 meter_log: str | None = None, model: str = "XR18"):
        self.host, self.port = host, port
        self.log_path = Path(log) if log else None
        self.withheld = set(withhold or ())
        self.meters_enabled = meters
        self.meters_paused = False
        self.meter_log_path = Path(meter_log) if meter_log else None
        self.model = model
        self.meter_subscriptions: dict[tuple[tuple[str, int], str], float] = {}
        self.last_meter_frame = 0.0
        # Keep this list in lockstep with XAirProtocol.CONTROLS: 31 controls.
        # XR18 uses /on (the value is an integer 0/1), not a synthetic /mute
        # or /level path.
        self.state: dict[str, Any] = {
            "/lr/mix/fader": 0.65,
            "/lr/mix/on": 1,
            "/config/mute/1": 0,
            "/config/mute/2": 0,
            "/config/mute/3": 0,
            "/config/mute/4": 0,
        }
        for index in range(1, 5):
            self.state[f"/dca/{index}/fader"] = 0.75
            self.state[f"/dca/{index}/on"] = 1
        for index in range(1, 7):
            self.state[f"/bus/{index}/mix/fader"] = 0.65
            self.state[f"/bus/{index}/mix/on"] = 1
        for index in range(1, 17):
            number = f"{index:02d}"
            self.state[f"/ch/{number}/mix/fader"] = 0.65
            self.state[f"/ch/{number}/mix/on"] = 1

        # Names are separate config values and are useful to verify strip
        # discovery without emulating the rest of the mixer state.
        self.state["/lr/config/name"] = "MAIN LR"
        for index in range(1, 5):
            self.state[f"/dca/{index}/config/name"] = f"DCA {index}"
        for index in range(1, 7):
            self.state[f"/bus/{index}/config/name"] = f"BUS {index}"
        for index in range(1, 17):
            self.state[f"/ch/{index:02d}/config/name"] = f"CH {index:02d}"
        self.clients: dict[tuple[str, int], float] = {}
        self.sock: socket.socket | None = None
        self.running = False

    def _reply(self, address: tuple[str, int], path: str, *values: Any) -> None:
        assert self.sock is not None
        self.sock.sendto(osc_encode(path, *values), address)

    def _log(self, address: tuple[str, int], path: str, value: Any) -> None:
        if self.log_path:
            with self.log_path.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps({"client": list(address), "path": path, "value": value}) + "\n")

    def _meter_log(self, event: str, bank: str, address: tuple[str, int]) -> None:
        if self.meter_log_path:
            with self.meter_log_path.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps({"event": event, "bank": bank, "client": list(address),
                                         "time": time.monotonic()}) + "\n")

    def _stream_meters(self) -> None:
        if not self.meters_enabled:
            return
        now = time.monotonic()
        self.meter_subscriptions = {key: expiry for key, expiry in self.meter_subscriptions.items() if expiry > now}
        if self.meters_paused or now - self.last_meter_frame < .05:
            return
        self.last_meter_frame = now
        for (address, bank) in self.meter_subscriptions:
            count = 40 if bank == "/meters/1" else 4
            values = [round((-22 + 12 * math.sin(now * 2 + index * .25)) * 256) for index in range(count)]
            # Manufacturer format: OSC blob byte length BE, count LE, int16 dB*256 LE.
            blob = struct.pack("<I", count) + struct.pack("<" + "h" * count, *values)
            packet = osc_string(bank) + osc_string(",b") + struct.pack(">I", len(blob)) + _pad(blob)
            assert self.sock is not None
            self.sock.sendto(packet, address)
            self._meter_log("frame", bank, address)

    def _handle(self, packet: bytes, address: tuple[str, int]) -> None:
        path, args = osc_decode(packet)
        now = time.monotonic()
        self.clients = {client: expiry for client, expiry in self.clients.items() if expiry > now}
        if path == "/__test/model" and len(args) == 1 and isinstance(args[0], str):
            self.model = args[0]
            return
        if path == "/__test/meters" and args:
            self.meters_paused = not bool(args[0])
            return
        if path == "/meters" and self.meters_enabled and len(args) == 1 and args[0] in ("/meters/1", "/meters/8"):
            self.meter_subscriptions[(address, args[0])] = now + 10
            self._meter_log("subscribe", args[0], address)
            return
        # Test-only endpoint: unblock missing GET replies without writing mixer values.
        if path == "/__test/release":
            released = self.withheld.copy()
            self.withheld.clear()
            for client in self.clients:
                for key in released:
                    if key in self.state:
                        self._reply(client, key, self.state[key])
            return
        if path == "/xremote":
            self.clients[address] = now + 10
            self._reply(address, "/xremote")
            return
        if path == "/xinfo":
            self._reply(address, "/xinfo", "192.168.1.10", "XR18 test", self.model, "1.20")
            return
        if path == "/info":
            self._reply(address, "/info", "1.20", "XR18 test", "XR18", "1.20")
            return
        if path == "/status":
            self._reply(address, "/status", "active", "192.168.1.10", "XR18 test")
            return
        if path in self.state and not args:
            if path not in self.withheld:
                self._reply(address, path, self.state[path])
            return
        if args and path in self.state:
            value = args[0]
            self.state[path] = value
            self._log(address, path, value)
            message = osc_encode(path, value)
            for client in list(self.clients):
                self.sock.sendto(message, client)

    def serve_forever(self) -> None:
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind((self.host, self.port))
        if self.meters_enabled:
            self.sock.settimeout(.025)
        self.port = self.sock.getsockname()[1]
        self.running = True
        try:
            while self.running:
                try:
                    packet, address = self.sock.recvfrom(4096)
                except socket.timeout:
                    self._stream_meters()
                    continue
                except OSError:
                    if self.running:
                        raise
                    break
                try:
                    self._handle(packet, address)
                    self._stream_meters()
                except (ValueError, IndexError, struct.error):
                    continue
        finally:
            self.running = False
            self.sock.close()
            self.sock = None

    def stop(self) -> None:
        self.running = False
        if self.sock:
            self.sock.close()


def self_test() -> None:
    mixer = FakeMixer(host="127.0.0.1", port=0)
    thread = threading.Thread(target=mixer.serve_forever, daemon=True)
    thread.start()
    while not mixer.running:
        time.sleep(0.001)
    client = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    client.settimeout(1)
    try:
        target = ("127.0.0.1", mixer.port)
        client.sendto(osc_string("/xinfo"), target)
        assert osc_decode(client.recv(4096))[1][2] == "XR18"
        client.sendto(osc_encode("/xinfo"), target)
        assert osc_decode(client.recv(4096))[1] == ["192.168.1.10", "XR18 test", "XR18", "1.20"]
        client.sendto(osc_encode("/xremote"), target)
        client.recv(4096)
        client.sendto(osc_encode("/lr/mix/fader"), target)
        assert abs(osc_decode(client.recv(4096))[1][0] - 0.65) < 1e-6
        client.sendto(osc_encode("/lr/mix/fader", 0.42), target)
        path, args = osc_decode(client.recv(4096))
        assert path == "/lr/mix/fader" and abs(args[0] - 0.42) < 1e-6
    finally:
        client.close()
        mixer.stop()
        thread.join(timeout=1)

    # Meter mode is opt-in; verify its actual XR18 blob layout independently of the app.
    mixer = FakeMixer(host="127.0.0.1", port=0, meters=True)
    thread = threading.Thread(target=mixer.serve_forever, daemon=True)
    thread.start()
    while not mixer.running:
        time.sleep(.001)
    client = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    client.settimeout(1)
    try:
        target = ("127.0.0.1", mixer.port)
        client.sendto(osc_encode("/meters", "/meters/1"), target)
        packet = client.recv(4096)
        assert packet[:16] == osc_string("/meters/1") + osc_string(",b")
        assert struct.unpack_from(">I", packet, 16)[0] == 84
        assert struct.unpack_from("<I", packet, 20)[0] == 40
        values = struct.unpack_from("<40h", packet, 24)
        assert all(-34 * 256 <= value <= -10 * 256 for value in values)
        updated = client.recv(4096)
        assert packet != updated, "Meter values must change over time"
        client.sendto(osc_encode("/meters", "/meters/8"), target)
        for _ in range(10):
            packet = client.recv(4096)
            if packet[:12] == osc_string("/meters/8"):
                assert len(packet) == 32
                assert struct.unpack_from(">I", packet, 16)[0] == 12
                assert struct.unpack_from("<I", packet, 20)[0] == 4
                break
        else:
            raise AssertionError("DCA meter bank missing")
    finally:
        client.close()
        mixer.stop()
        thread.join(timeout=1)


def main() -> None:
    parser = argparse.ArgumentParser(description="Minimal XR18 OSC fake mixer")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=10024)
    parser.add_argument("--log", help="append SET events as JSON lines")
    parser.add_argument("--withhold", action="append", default=[], metavar="OSC_PATH",
                        help="omit GET replies for this path; repeatable; /__test/release restores replies")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--meters", action="store_true", help="opt in to changing XR18 bank1/bank8 meter blobs")
    parser.add_argument("--meter-log", help="append meter subscribe/frame events as JSON lines, separate from SET log")
    parser.add_argument("--model", default="XR18", help="handshake model; use UNSUPPORTED to test identity validation")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("self-test ok")
        return
    FakeMixer(args.host, args.port, args.log, args.withhold, args.meters, args.meter_log, args.model).serve_forever()


if __name__ == "__main__":
    main()
