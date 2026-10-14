package sk.palko.xr18bubble;

/** Device-independent panel measurements, in dp. No Android dependencies. */
public final class PanelSizing {
    private PanelSizing() {}

    public static int stripWidth(String size) {
        if ("small".equals(size)) return 64;
        if ("large".equals(size)) return 92;
        return 76;
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
        long desired = 36 + 16;
        if (faderCount <= 0 && buttonCount <= 0) {
            desired += 80;
        } else {
            if (faderCount > 0) desired += "small".equals(size) ? 180 : "large".equals(size) ? 250 : 210;
            if (buttonCount > 0) desired += 48 + 6;
        }
        return fit(desired, availableHeightDp);
    }

    private static int fit(long desired, int available) {
        // Available screen space wins if even the preferred minimum cannot fit.
        return (int) Math.min(Math.max(0, available), Math.max(96L, desired));
    }
}
