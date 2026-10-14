package sk.palko.xr18bubble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * XR18 OSC addresses and the X AIR four-segment fader taper. No network I/O.
 * Manufacturer transport specification (firmware 1.11+), mirrored at:
 * https://media.discopiu.com/files/2022/3/25/864756-original.pdf
 * Parameter paths cross-checked against the X AIR command table:
 * https://schwiftything.com/blogs/code-tips-tricks/x-air-m-air-osc-commands
 * Taper and X AIR address padding cross-checked against the client implementation notes:
 * https://github.com/stblassitude/xair-api-go/blob/main/WIRE_PROTOCOL.md
 */
public final class XAirProtocol {
    public static final int PORT = 10024;
    public static final List<Control> CONTROLS;

    static {
        List<Control> controls = new ArrayList<>();
        controls.add(new Control("main", "MAIN LR", "/lr/mix/fader", "/lr/mix/on", false));
        for (int i = 1; i <= 4; i++) controls.add(new Control("dca" + i, "DCA " + i,
                "/dca/" + i + "/fader", "/dca/" + i + "/on", false));
        // These are independent boolean parameters, NOT a shared /config/mute bitmask.
        for (int i = 1; i <= 4; i++) controls.add(new Control("mute" + i, "MUTE GROUP " + i,
                null, "/config/mute/" + i, true));
        for (int i = 1; i <= 6; i++) controls.add(new Control("bus" + i, "BUS " + i,
                "/bus/" + i + "/mix/fader", "/bus/" + i + "/mix/on", false));
        for (int i = 1; i <= 16; i++) {
            String number = String.format(Locale.ROOT, "%02d", i);
            controls.add(new Control("ch" + i, "CH " + number,
                    "/ch/" + number + "/mix/fader", "/ch/" + number + "/mix/on", false));
        }
        CONTROLS = Collections.unmodifiableList(controls);
    }

    private XAirProtocol() {}

    public static final class Control {
        public final String id;
        public final String label;
        public final String faderPath;
        public final String mutePath;
        /** True for mute-group activation; false for the signal-on switches of strips/DCAs. */
        public final boolean muteIsOn;
        /** Zero: each supported control has its own integer boolean address. */
        public final int muteMask = 0;

        private Control(String id, String label, String faderPath, String mutePath, boolean muteIsOn) {
            this.id = id;
            this.label = label;
            this.faderPath = faderPath;
            this.mutePath = mutePath;
            this.muteIsOn = muteIsOn;
        }

        public boolean isMuted(int wireValue) {
            return muteIsOn ? wireValue != 0 : wireValue == 0;
        }

        public int muteValue(boolean muted) {
            return muted == muteIsOn ? 1 : 0;
        }
    }

    public static Control findControl(String id) {
        for (Control control : CONTROLS) if (control.id.equals(id)) return control;
        return null;
    }

    /** Returns the exact taper without display rounding. 0 means the -90 dB floor (silence). */
    public static float normalizedToDb(float normalized) {
        if (Float.isNaN(normalized)) throw new IllegalArgumentException("NaN fader value");
        float value = Math.max(0f, Math.min(1f, normalized));
        if (value >= 0.5f) return 40f * value - 30f;
        if (value >= 0.25f) return 80f * value - 50f;
        if (value >= 0.0625f) return 160f * value - 70f;
        return 480f * value - 90f;
    }

    /** Inverse taper, clamped to the XR18's normalized range. Negative infinity means silence. */
    public static float dbToNormalized(float db) {
        if (Float.isNaN(db)) throw new IllegalArgumentException("NaN dB value");
        if (db >= 10f) return 1f;
        if (db >= -10f) return (db + 30f) / 40f;
        if (db >= -30f) return (db + 50f) / 80f;
        if (db >= -60f) return (db + 70f) / 160f;
        if (db >= -90f) return (db + 90f) / 480f;
        return 0f;
    }
}
