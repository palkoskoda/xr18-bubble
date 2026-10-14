import sk.palko.xr18bubble.Osc;
import sk.palko.xr18bubble.XAirProtocol;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Run with javac/java, without an Android SDK or a mixer. */
public final class ProtocolTest {
    private static int checks;

    public static void main(String[] args) {
        goldenBytes();
        malformedPackets();
        bundles();
        controls();
        taper();
        System.out.println("ProtocolTest passed: " + checks + " checks (no network I/O)");
    }

    private static void goldenBytes() {
        // Literal bytes from the manufacturer's X AIR Remote Control Protocol, page 2.
        byte[] meters = hex("2f6d657465727300" + "2c736900" + "2f6d65746572732f30000000" + "00000008");
        equal(meters, Osc.encode("/meters", "/meters/0", 8), "manufacturer meter request");
        equal(hex("2f78696e666f00002c000000"), Osc.encode("/xinfo"), "GET has empty type tag");
        equal(hex("2f7800002c696600ffffffff3f400000"), Osc.encode("/x", -1, 0.75f), "int/float big endian");
        equal(hex("2f7800002c73000061626300"), Osc.encode("/x", "abc"), "string padding boundary");
        List<Osc.Message> decoded = Osc.decode(meters, meters.length);
        check(decoded.size() == 1 && decoded.get(0).address.equals("/meters"), "golden decode address");
        check(decoded.get(0).args.equals(Arrays.asList("/meters/0", 8)), "golden decode args");
        byte[] mixed = Osc.encode("/x", "spev á", Integer.MIN_VALUE, -0.25f, true);
        check(Osc.decode(mixed, mixed.length).get(0).args.equals(Arrays.asList("spev á", Integer.MIN_VALUE, -0.25f, 1)),
                "UTF-8 and mixed type round trip");
    }

    private static void malformedPackets() {
        byte[] packet = Osc.encode("/ch/01/mix/fader", 0.75f);
        for (int len = 0; len < packet.length; len++) {
            // Address-only packets are a valid X AIR enquiry extension.
            if (len == 20) continue;
            check(Osc.decode(packet, len).isEmpty(), "truncation rejected " + len);
        }
        check(Osc.decode(packet, packet.length + 1).isEmpty(), "length beyond buffer");
        check(Osc.decode(packet, -1).isEmpty(), "negative length");
        check(Osc.decode(null, 4).isEmpty(), "null datagram");
        check(Osc.decode(hex("2f7800002c690000"), 8).isEmpty(), "missing int");
        check(Osc.decode(hex("2f7800002c6400000000000000000000"), 16).isEmpty(), "unsupported type rejected");
        check(Osc.decode(hex("2f7800012c000000"), 8).isEmpty(), "nonzero string padding");
        check(Osc.decode(hex("2f7800002c730000c0af0000"), 12).isEmpty(), "invalid UTF-8");
        check(Osc.decode(hex("2f7800002c00000000000000"), 12).isEmpty(), "unexpected trailing data");
        expectIllegal(() -> Osc.encode("bad", 1), "invalid outgoing address");
        expectIllegal(() -> Osc.encode("/x", "a\u0000b"), "embedded NUL rejected");
        expectIllegal(() -> Osc.encode("/x", 1.0d), "double not silently narrowed");
        // Random UDP noise must never escape the parser as an unchecked bounds failure.
        Random random = new Random(18);
        for (int i = 0; i < 1000; i++) {
            byte[] noise = new byte[random.nextInt(256)];
            random.nextBytes(noise);
            Osc.decode(noise, noise.length);
        }
        checks++;
    }

    private static void bundles() {
        byte[] first = Osc.encode("/lr/mix/fader", 0.75f);
        byte[] second = Osc.encode("/config/mute/1", 1);
        byte[] packet = bundle(first, bundle(second));
        List<Osc.Message> messages = Osc.decode(packet, packet.length);
        check(messages.size() == 2, "nested bundle flattened");
        check(messages.get(0).address.equals("/lr/mix/fader"), "bundle preserves order");
        check(messages.get(1).args.get(0).equals(1), "nested bundle args");
        byte[] bad = bundle(first, hex("2f7800002c690000"));
        check(Osc.decode(bad, bad.length).isEmpty(), "bad bundle atomically discarded");
        for (String size : new String[]{"ffffffff", "7fffffff", "00000000", "00000003"}) {
            byte[] malformed = hex("2362756e646c65000000000000000001" + size);
            check(Osc.decode(malformed, malformed.length).isEmpty(), "bad bundle size " + size);
        }
        byte[] nested = first;
        for (int i = 0; i < 20; i++) nested = bundle(nested);
        check(Osc.decode(nested, nested.length).isEmpty(), "bundle recursion bounded");
    }

    private static void controls() {
        check(XAirProtocol.PORT == 10024, "X AIR port");
        check(XAirProtocol.CONTROLS.size() == 31, "all requested controls");
        check(XAirProtocol.findControl("main").faderPath.equals("/lr/mix/fader"), "main path");
        check(XAirProtocol.findControl("dca4").mutePath.equals("/dca/4/on"), "DCA has no mix segment");
        check(XAirProtocol.findControl("bus6").faderPath.equals("/bus/6/mix/fader"), "bus unpadded");
        check(XAirProtocol.findControl("ch1").mutePath.equals("/ch/01/mix/on"), "channel two-digit padding");
        check(XAirProtocol.findControl("ch16").faderPath.equals("/ch/16/mix/fader"), "last input path");
        check(XAirProtocol.findControl("missing") == null, "unknown control");
        for (XAirProtocol.Control control : XAirProtocol.CONTROLS) {
            check(control.isMuted(control.muteValue(true)), "mute round trip " + control.id);
            check(!control.isMuted(control.muteValue(false)), "unmute round trip " + control.id);
            check(control.muteMask == 0, "no shared bitmask " + control.id);
        }
        for (int i = 1; i <= 4; i++) {
            XAirProtocol.Control group = XAirProtocol.findControl("mute" + i);
            check(group.mutePath.equals("/config/mute/" + i), "independent mute-group address " + i);
            check(group.faderPath == null && group.muteValue(true) == 1, "mute-group polarity " + i);
        }
        check(XAirProtocol.findControl("main").muteValue(true) == 0, "strip-on inverse mute polarity");
    }

    private static void taper() {
        float[][] knots = {{0f,-90f},{0.0625f,-60f},{0.25f,-30f},{0.5f,-10f},{0.75f,0f},{1f,10f}};
        for (float[] point : knots) {
            near(point[1], XAirProtocol.normalizedToDb(point[0]), 0.00001f, "decode taper knot");
            near(point[0], XAirProtocol.dbToNormalized(point[1]), 0.00001f, "encode taper knot");
        }
        for (int i = 0; i <= 1024; i++) {
            float normalized = i / 1024f;
            near(normalized, XAirProtocol.dbToNormalized(XAirProtocol.normalizedToDb(normalized)), 0.000001f,
                    "1024-step fader round trip");
        }
        near(0.6f, XAirProtocol.dbToNormalized(-6f), 0.00001f, "-6 dB correct upper segment");
        near(-90f, XAirProtocol.normalizedToDb(-1f), 0f, "negative position clamp");
        near(10f, XAirProtocol.normalizedToDb(2f), 0f, "upper position clamp");
        near(0f, XAirProtocol.dbToNormalized(Float.NEGATIVE_INFINITY), 0f, "silence encoding");
        near(1f, XAirProtocol.dbToNormalized(Float.POSITIVE_INFINITY), 0f, "upper dB clamp");
        expectIllegal(() -> XAirProtocol.dbToNormalized(Float.NaN), "NaN dB rejected");
        expectIllegal(() -> XAirProtocol.normalizedToDb(Float.NaN), "NaN position rejected");
    }

    private static byte[] bundle(byte[]... packets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = hex("2362756e646c65000000000000000001");
        out.write(header, 0, header.length);
        for (byte[] packet : packets) {
            byte[] size = java.nio.ByteBuffer.allocate(4).putInt(packet.length).array();
            out.write(size, 0, size.length);
            out.write(packet, 0, packet.length);
        }
        return out.toByteArray();
    }

    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    private static void equal(byte[] expected, byte[] actual, String label) {
        check(Arrays.equals(expected, actual), label);
    }

    private static void near(float expected, float actual, float tolerance, String label) {
        check(Math.abs(expected - actual) <= tolerance, label + ": " + expected + " != " + actual);
    }

    private static void expectIllegal(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(label);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
