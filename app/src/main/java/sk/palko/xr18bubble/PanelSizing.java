package sk.palko.xr18bubble;

/** Device-independent panel measurements, in dp. No Android dependencies. */
public final class PanelSizing {
    private static final float[] CONTROL_SCALES = { 1f, 1.15f, 1.35f, 1.8f, 2.4f, 3.3f, 4.5f };
    private static final int[] STRIP_WIDTHS = { 64, 76, 92, 120, 160, 224, 320 };
    private static final int[] FADER_HEIGHTS = { 180, 210, 250, 330, 420, 540, 720 };
    private static final int[] MUTE_HEIGHTS = { 48, 48, 48, 80, 108, 148, 208 };
    private static final int[] HEADER_HEIGHTS = { 24, 24, 24, 44, 48, 52, 56 };
    private static final int[] STATUS_HEIGHTS = { 12, 12, 12, 22, 26, 30, 34 };
    private static final int[] RETURN_HEIGHTS = { 34, 34, 34, 54, 62, 70, 78 };
    private PanelSizing() {}

    /** Stable preference values, returned defensively so callers cannot alter their order. */
    public static String[] controlSizes() {
        return new String[] { "small", "medium", "large", "xlarge", "xxlarge", "huge", "maximum" };
    }

    /** Independent text multiplier; unknown or missing preferences retain existing sizes. */
    public static String[] fontSizes(){return new String[]{"75","100","125","150","175","200","250","300"};}
    public static float fontScale(String size){
        if("75".equals(size))return .75f;if("125".equals(size))return 1.25f;
        if("150".equals(size))return 1.5f;if("175".equals(size))return 1.75f;
        if("200".equals(size))return 2f;if("250".equals(size))return 2.5f;
        if("300".equals(size))return 3f;return 1f;
    }

    private static int controlIndex(String size) {
        if ("small".equals(size)) return 0;
        if ("large".equals(size)) return 2;
        if ("xlarge".equals(size)) return 3;
        if ("xxlarge".equals(size)) return 4;
        if ("huge".equals(size)) return 5;
        if ("maximum".equals(size)) return 6;
        return 1;
    }

    public static float controlScale(String size) {
        return CONTROL_SCALES[controlIndex(size)];
    }

    public static int stripWidth(String size) {
        return STRIP_WIDTHS[controlIndex(size)];
    }

    public static int faderHeightDp(String size) {
        return FADER_HEIGHTS[controlIndex(size)];
    }

    public static int muteHeightDp(String size) {
        return MUTE_HEIGHTS[controlIndex(size)];
    }

    public static int headerHeightDp(String size) {
        return HEADER_HEIGHTS[controlIndex(size)];
    }

    public static int statusHeightDp(String size) {
        return STATUS_HEIGHTS[controlIndex(size)];
    }

    /** Includes the gap above the optional return-to-Wi-Fi action. */
    public static int returnHeightDp(String size) {
        return RETURN_HEIGHTS[controlIndex(size)];
    }

    public static int bubbleDiameter(String size) {
        if ("small".equals(size)) return 40;
        if ("large".equals(size)) return 56;
        return 48;
    }

    /** Fader strips and mute-only buttons occupy separate rows. */
    public static int panelWidthDp(int faderCount, int buttonCount, String size, int availableDp) {
        int columns = Math.max(Math.max(0, faderCount), Math.max(0, buttonCount));
        long desired = columns == 0 ? 180 : (long) columns * stripWidth(size) + (columns - 1L) * 6 + 16;
        return fit(desired, availableDp);
    }

    /** Includes a single mute-button row; the caller handles further wrapped rows. */
    public static int panelHeightDp(int faderCount, int buttonCount, String size, int availableHeightDp) {
        long desired = 16L + headerHeightDp(size) + statusHeightDp(size);
        if (faderCount <= 0 && buttonCount <= 0) {
            desired += 80;
        } else {
            if (faderCount > 0) desired += faderHeightDp(size);
            if (buttonCount > 0) desired += muteHeightDp(size) + 6;
        }
        return fit(desired, availableHeightDp);
    }

    private static int fit(long desired, int available) {
        // Available screen space wins if even the preferred minimum cannot fit.
        return (int) Math.min(Math.max(0, available), Math.max(96L, desired));
    }
}
