package sk.palko.xr18bubble;

/**
 * X AIR meter banks, independent of Android. Source: MUSIC Group's X AIR Mixer
 * Series Remote Control Protocol (firmware 1.11), pages 1-4. The blob's little
 * endian layout is also demonstrated by Patrick-Gilles Maillot's XAir_Command.c.
 * https://media.discopiu.com/files/2022/3/25/864756-original.pdf
 * https://github.com/pmaillot/X32-Behringer/blob/master/XAir_Command.c
 *
 * Bank 1: 16 channel pre, 10 aux/FX pre, 6 bus pre, 4 FX send pre,
 *         2 main post, 2 monitor. Bank 8: 4 DCA meters.
 * These are signed 16-bit dB/256 values, not X32's float32 linear meters.
 */
public final class MeterProtocol {
    public static final String CHANNEL_BANK = "/meters/1";
    public static final String DCA_BANK = "/meters/8";
    public static final int CHANNEL_START = 0;
    public static final int BUS_START = 26;
    public static final int MAIN_START = 36;
    public static final long RENEW_MS = 4000;
    public static final long STALE_MS = 1000;

    private MeterProtocol() {}

    public static final class Frame {
        public final String bank;
        public final float[] db;
        private Frame(String bank, float[] db) { this.bank = bank; this.db = db; }
    }

    /** Subscription uses the same socket as replies; renew before its ~10 s expiry. */
    public static byte[] subscription(String bank) {
        if (!CHANNEL_BANK.equals(bank) && !DCA_BANK.equals(bank)) {
            throw new IllegalArgumentException("Unsupported meter bank");
        }
        return Osc.encode("/meters", bank);
    }

    /**
     * Decodes the manufacturer's single OSC blob reply. Exact bank/count/length,
     * OSC zero padding and payload endianness are checked before publishing a frame.
     * Unknown banks and malformed packets return null without retaining any data.
     */
    public static Frame decode(byte[] packet, int length) {
        if (packet == null || length < 24 || length > packet.length || (length & 3) != 0) return null;
        String bank;
        int count;
        if (address(packet, CHANNEL_BANK)) { bank = CHANNEL_BANK; count = 40; }
        else if (address(packet, DCA_BANK)) { bank = DCA_BANK; count = 4; }
        else return null;
        // Both supported addresses occupy 12 OSC bytes; the blob typetag occupies 4.
        if (packet[12] != ',' || packet[13] != 'b' || packet[14] != 0 || packet[15] != 0) return null;
        int blobBytes = bigInt(packet, 16);
        int expectedBytes = 4 + count * 2;
        if (blobBytes != expectedBytes || length != 20 + expectedBytes) return null;
        if (littleInt(packet, 20) != count) return null;
        float[] db = new float[count];
        for (int i = 0; i < count; i++) {
            int offset = 24 + i * 2;
            short raw = (short) ((packet[offset] & 255) | ((packet[offset + 1] & 255) << 8));
            db[i] = raw / 256f;
        }
        return new Frame(bank, db);
    }

    private static boolean address(byte[] packet, String address) {
        for (int i = 0; i < address.length(); i++) if (packet[i] != address.charAt(i)) return false;
        for (int i = address.length(); i < 12; i++) if (packet[i] != 0) return false;
        return true;
    }

    private static int bigInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 255) << 24) | ((bytes[offset + 1] & 255) << 16)
                | ((bytes[offset + 2] & 255) << 8) | (bytes[offset + 3] & 255);
    }

    private static int littleInt(byte[] bytes, int offset) {
        return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8)
                | ((bytes[offset + 2] & 255) << 16) | ((bytes[offset + 3] & 255) << 24);
    }
}
