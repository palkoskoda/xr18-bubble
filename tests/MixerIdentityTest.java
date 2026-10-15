import sk.palko.xr18bubble.MixerClient;
import sk.palko.xr18bubble.Osc;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** JVM regression checks for X AIR /xinfo identity validation. */
public final class MixerIdentityTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Method identity = MixerClient.class.getDeclaredMethod("identity", Osc.Message.class);
        identity.setAccessible(true);

        // This is the real response shape observed from the affected mixer.
        byte[] packet = hex("2f78696e666f00002c73737373000000"
                + "3139322e3136382e312e323335000000"
                + "585231382d736b6f64610000"
                + "5852313856320000"
                + "312e323300000000");
        List<Osc.Message> decoded = Osc.decode(packet, packet.length);
        check(decoded.size() == 1, "real XR18V2 /xinfo decodes");
        String[] real = parse(identity, decoded.get(0));
        check(real != null && real[0].equals("XR18-skoda") && real[1].equals("XR18V2"),
                "real XR18V2 identity accepted");

        for (String model : new String[]{"XR18", "MR18", "X18", "XR18V2", " xr18v2 "}) {
            check(parse(identity, message(model)) != null, "accepted model: " + model);
        }
        for (String model : new String[]{"XR18V20", "XR18V3", "XR12", "XR16", "X32", "unknown"}) {
            check(parse(identity, message(model)) == null, "rejected model: " + model);
        }
        check(parse(identity, new Osc.Message("/xinfo", Arrays.asList("ip", "name"))) == null,
                "missing model rejected");
        check(parse(identity, new Osc.Message("/xinfo", Arrays.asList("ip", "name", 18))) == null,
                "non-string model rejected");
        check(parse(identity, new Osc.Message("/xinfo", Arrays.asList("ip", 18, "XR18"))) == null,
                "non-string name rejected");
        System.out.println("MixerIdentityTest passed: " + checks + " checks");
    }

    private static Osc.Message message(String model) {
        return new Osc.Message("/xinfo", Arrays.asList("192.168.1.235", "XR18-skoda", model, "1.23"));
    }

    private static String[] parse(Method identity, Osc.Message message) throws Exception {
        return (String[]) identity.invoke(null, message);
    }

    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
