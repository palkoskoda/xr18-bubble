package sk.palko.xr18bubble;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small, Android-independent OSC codec. Incoming malformed datagrams are discarded atomically. */
public final class Osc {
    private static final int MAX_PACKET = 65536;
    private static final int MAX_MESSAGES = 1024;
    private static final int MAX_DEPTH = 16;

    private Osc() {}

    public static final class Message {
        public final String address;
        public final List<Object> args;

        public Message(String address, List<Object> args) {
            this.address = address;
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
        }
    }

    /** Encodes OSC strings, signed int32 and float32. Boolean maps to the X AIR int32 convention. */
    public static byte[] encode(String address, Object... args) {
        requireAddress(address);
        if (args == null) throw new IllegalArgumentException("Arguments must not be null");
        StringBuilder tags = new StringBuilder(",");
        for (Object arg : args) {
            if (arg instanceof String) tags.append('s');
            else if (arg instanceof Integer || arg instanceof Boolean) tags.append('i');
            else if (arg instanceof Float) tags.append('f');
            else throw new IllegalArgumentException("Unsupported OSC argument: " + (arg == null ? "null" : arg.getClass()));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeString(out, address);
        writeString(out, tags.toString());
        for (Object arg : args) {
            if (arg instanceof String) writeString(out, (String) arg);
            else if (arg instanceof Boolean) writeInt(out, (Boolean) arg ? 1 : 0);
            else if (arg instanceof Integer) writeInt(out, (Integer) arg);
            else writeInt(out, Float.floatToIntBits((Float) arg));
            if (out.size() > MAX_PACKET) throw new IllegalArgumentException("OSC packet too large");
        }
        return out.toByteArray();
    }

    /**
     * Decodes a UDP payload; returns an empty list for invalid/truncated/unsupported data.
     * Bundles are flattened in wire order. Timetags are ignored: X AIR controls use immediate messages.
     * Address-only requests (the X AIR parameter enquiry extension) are also accepted.
     */
    public static List<Message> decode(byte[] data, int length) {
        if (data == null || length <= 0 || length > data.length || length > MAX_PACKET || (length & 3) != 0) {
            return Collections.emptyList();
        }
        List<Message> messages = new ArrayList<>();
        try {
            decodePacket(new Reader(data, 0, length), messages, 0);
            return Collections.unmodifiableList(messages);
        } catch (IllegalArgumentException invalid) {
            return Collections.emptyList();
        }
    }

    private static void decodePacket(Reader in, List<Message> messages, int depth) {
        if (depth > MAX_DEPTH) throw invalid();
        String address = in.string();
        if ("#bundle".equals(address)) {
            in.skip(8); // OSC 64-bit timetag.
            while (in.remaining() > 0) {
                int size = in.integer();
                if (size <= 0 || (size & 3) != 0 || size > in.remaining()) throw invalid();
                Reader element = new Reader(in.data, in.position, in.position + size);
                in.skip(size);
                decodePacket(element, messages, depth + 1);
            }
            return;
        }
        requireAddress(address);
        List<Object> args = new ArrayList<>();
        if (in.remaining() > 0) {
            String tags = in.string();
            if (!tags.startsWith(",")) throw invalid();
            for (int i = 1; i < tags.length(); i++) {
                switch (tags.charAt(i)) {
                    case 's': args.add(in.string()); break;
                    case 'i': args.add(in.integer()); break;
                    case 'f': args.add(Float.intBitsToFloat(in.integer())); break;
                    default: throw invalid();
                }
            }
        }
        if (in.remaining() != 0 || messages.size() >= MAX_MESSAGES) throw invalid();
        messages.add(new Message(address, args));
    }

    private static void requireAddress(String address) {
        if (address == null || !address.startsWith("/") || address.indexOf('\0') >= 0 || address.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Invalid OSC address");
        }
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("OSC strings cannot contain NUL");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        out.write(0);
        while ((out.size() & 3) != 0) out.write(0);
        if (out.size() > MAX_PACKET) throw new IllegalArgumentException("OSC packet too large");
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Malformed OSC packet");
    }

    private static final class Reader {
        final byte[] data;
        final int end;
        int position;

        Reader(byte[] data, int position, int end) {
            this.data = data;
            this.position = position;
            this.end = end;
        }

        int remaining() { return end - position; }

        void skip(int size) {
            if (size < 0 || size > remaining()) throw invalid();
            position += size;
        }

        int integer() {
            if (remaining() < 4) throw invalid();
            int value = ((data[position] & 255) << 24) | ((data[position + 1] & 255) << 16)
                    | ((data[position + 2] & 255) << 8) | (data[position + 3] & 255);
            position += 4;
            return value;
        }

        String string() {
            int start = position;
            while (position < end && data[position] != 0) position++;
            if (position == end) throw invalid();
            int stringEnd = position;
            int bytes = stringEnd - start + 1;
            int padded = (bytes + 3) & ~3;
            if (padded > end - start) throw invalid();
            for (int i = stringEnd; i < start + padded; i++) if (data[i] != 0) throw invalid();
            position = start + padded;
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(data, start, stringEnd - start)).toString();
            } catch (CharacterCodingException invalidUtf8) {
                throw invalid();
            }
        }
    }
}
