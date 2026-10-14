import sk.palko.xr18bubble.MeterProtocol;
import sk.palko.xr18bubble.Osc;
import java.util.Arrays;
import java.util.Random;

/** Pure protocol test: no device, network, or Android runtime. */
public final class MeterProtocolTest {
    private static int checks;

    public static void main(String[] args) {
        byte[] subscribe = MeterProtocol.subscription(MeterProtocol.CHANNEL_BANK);
        check(hex(subscribe).equals("2f6d6574657273002c7300002f6d65746572732f31000000"), "bank1 subscription golden wire");
        check(Osc.decode(subscribe, subscribe.length).get(0).args.equals(Arrays.asList("/meters/1")), "subscription type string");
        check(hex(MeterProtocol.subscription(MeterProtocol.DCA_BANK)).endsWith("2f6d65746572732f38000000"), "DCA subscription");
        try { MeterProtocol.subscription("/meters/2"); throw new AssertionError("unknown subscription"); }
        catch (IllegalArgumentException expected) { checks++; }

        short[] levels = new short[40];
        for (int i = 0; i < levels.length; i++) levels[i] = (short) (-256 * i);
        levels[0] = Short.MIN_VALUE;
        levels[15] = -1;
        levels[26] = -3072;
        levels[31] = -10240;
        levels[36] = -1536;
        levels[37] = 0;
        byte[] channel = packet('1', levels);
        MeterProtocol.Frame frame = MeterProtocol.decode(channel, channel.length);
        check(frame != null && frame.bank.equals("/meters/1") && frame.db.length == 40, "bank1 exact count");
        equal(frame.db[0], -128f, "silence");
        equal(frame.db[15], -1f / 256f, "fractional signed endian");
        equal(frame.db[MeterProtocol.BUS_START], -12f, "bus1 index");
        equal(frame.db[MeterProtocol.BUS_START + 5], -40f, "bus6 index");
        equal(frame.db[MeterProtocol.MAIN_START], -6f, "main left index");
        equal(frame.db[MeterProtocol.MAIN_START + 1], 0f, "main right index");
        byte[] dca = packet('8', new short[]{-2560, 0, 2560, Short.MIN_VALUE});
        MeterProtocol.Frame dcaFrame = MeterProtocol.decode(dca, dca.length);
        check(dcaFrame != null && dcaFrame.bank.equals("/meters/8") && dcaFrame.db.length == 4, "DCA exact count");
        equal(dcaFrame.db[0], -10f, "DCA1 dB");
        equal(dcaFrame.db[2], 10f, "DCA preserves positive dB without guessing semantics");

        for (int i = 0; i < channel.length; i++) check(MeterProtocol.decode(channel, i) == null, "truncated " + i);
        check(MeterProtocol.decode(channel, channel.length + 1) == null, "over buffer");
        check(MeterProtocol.decode(null, 32) == null, "null");
        check(MeterProtocol.decode(channel, -1) == null, "negative length");
        byte[] extra = Arrays.copyOf(channel, channel.length + 4);
        check(MeterProtocol.decode(extra, extra.length) == null, "trailing data");
        rejectChanged(channel, 8, '2', "unknown bank");
        rejectChanged(channel, 9, 'x', "nonzero address padding");
        rejectChanged(channel, 13, 'f', "wrong blob type");
        rejectChanged(channel, 15, 1, "nonzero typetag padding");
        rejectChanged(channel, 16, 1, "wrong blob length endian");
        rejectChanged(channel, 19, 80, "wrong blob length");
        rejectChanged(channel, 20, 39, "wrong count");
        rejectChanged(channel, 21, 40, "count is little endian");
        check(MeterProtocol.decode(packet('8', levels), channel.length) == null, "bank8 cannot carry40 values");
        Random random = new Random(1818);
        for (int i = 0; i < 5000; i++) {
            byte[] noise = new byte[random.nextInt(200)];
            random.nextBytes(noise);
            check(MeterProtocol.decode(noise, noise.length) == null, "random malformed datagram " + i);
        }
        System.out.println("MeterProtocolTest passed: " + checks + " checks (no network I/O)");
    }

    private static byte[] packet(char bank, short[] values) {
        byte[] packet = new byte[24 + values.length * 2];
        byte[] address = ("/meters/" + bank).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(address, 0, packet, 0, address.length);
        packet[12] = ','; packet[13] = 'b';
        int bytes = 4 + values.length * 2;
        packet[16] = (byte) (bytes >>> 24); packet[17] = (byte) (bytes >>> 16);
        packet[18] = (byte) (bytes >>> 8); packet[19] = (byte) bytes;
        packet[20] = (byte) values.length;
        for (int i = 0; i < values.length; i++) {
            packet[24 + i * 2] = (byte) values[i];
            packet[25 + i * 2] = (byte) (values[i] >>> 8);
        }
        return packet;
    }

    private static void rejectChanged(byte[] packet, int offset, int value, String message) {
        byte[] changed = packet.clone(); changed[offset] = (byte) value;
        check(MeterProtocol.decode(changed, changed.length) == null, message);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static void equal(float actual, float expected, String message) {
        check(actual == expected, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
