import sk.palko.xr18bubble.PanelSizing;

/** Run with javac/java; no Android device or network needed. */
public final class PanelSizingTest {
    private static int checks;

    public static void main(String[] args) {
        equal(64, PanelSizing.stripWidth("small"), "small strip");
        equal(76, PanelSizing.stripWidth("medium"), "medium strip");
        equal(92, PanelSizing.stripWidth("large"), "large strip");
        equal(76, PanelSizing.stripWidth(null), "default strip");
        equal(40, PanelSizing.bubbleDiameter("small"), "small bubble");
        equal(48, PanelSizing.bubbleDiameter("medium"), "medium bubble");
        equal(56, PanelSizing.bubbleDiameter("large"), "large bubble");
        equal(48, PanelSizing.bubbleDiameter("unknown"), "default bubble");

        equal(180, width(0, 0), "empty panel");
        equal(96, width(1, 0), "one fader minimum");
        equal(174, width(2, 0), "two faders");
        equal(256, width(3, 0), "three faders");
        equal(174, width(2, 2), "mixed rows do not add widths");
        equal(256, width(1, 3), "button row determines width");
        equal(256, width(3, 1), "fader row determines width");
        equal(96, width(0, 1), "one mute button");
        equal(174, width(0, 2), "two mute buttons");
        equal(256, width(0, 3), "three mute buttons");
        equal(220, PanelSizing.panelWidthDp(3, 0, "small", 1000), "small panel");
        equal(304, PanelSizing.panelWidthDp(3, 0, "large", 1000), "large panel");
        equal(200, PanelSizing.panelWidthDp(3, 3, "medium", 200), "width clamps to screen");
        equal(80, PanelSizing.panelWidthDp(1, 0, "medium", 80), "narrow screen wins over minimum");
        equal(0, PanelSizing.panelWidthDp(1, 0, "medium", 0), "zero available width");
        equal(1000, PanelSizing.panelWidthDp(Integer.MAX_VALUE, 0, "large", 1000), "large count cannot overflow");
        equal(180, width(-1, -2), "negative counts treated as empty");

        equal(132, height(0, 0), "empty hint height");
        equal(262, height(1, 0), "single fader height");
        equal(262, height(2, 0), "fader count does not add height");
        equal(262, height(3, 0), "three faders same height");
        equal(106, height(0, 1), "mute-only panel is compact");
        equal(106, height(0, 3), "single button row height");
        equal(316, height(2, 3), "mixed rows height");
        equal(232, PanelSizing.panelHeightDp(1, 0, "small", 1000), "small fader height");
        equal(302, PanelSizing.panelHeightDp(1, 0, "large", 1000), "large fader height");
        equal(286, PanelSizing.panelHeightDp(1, 1, "small", 1000), "small mixed height");
        equal(356, PanelSizing.panelHeightDp(1, 1, "large", 1000), "large mixed height");
        equal(200, PanelSizing.panelHeightDp(2, 3, "medium", 200), "height clamps to screen");
        equal(80, PanelSizing.panelHeightDp(0, 1, "medium", 80), "short screen wins over minimum");
        equal(0, PanelSizing.panelHeightDp(1, 0, "medium", -1), "negative available height");
        accessibilityPresets();
        screenBounds();
        System.out.println("PanelSizingTest passed: " + checks + " checks");
    }

    private static void accessibilityPresets() {
        String[] sizes = PanelSizing.controlSizes();
        String[] expected = { "small", "medium", "large", "xlarge", "xxlarge", "huge", "maximum" };
        float[] scales = { 1f, 1.15f, 1.35f, 1.8f, 2.4f, 3.3f, 4.5f };
        int[] widths = { 64, 76, 92, 120, 160, 224, 320 };
        int[] faders = { 180, 210, 250, 330, 420, 540, 720 };
        int[] mutes = { 48, 48, 48, 80, 108, 148, 208 };
        int[] headers = { 24, 24, 24, 44, 48, 52, 56 };
        int[] statuses = { 12, 12, 12, 22, 26, 30, 34 };
        int[] returns = { 34, 34, 34, 54, 62, 70, 78 };
        equal(expected.length, sizes.length, "seven size choices");
        sizes[0] = "changed by caller";
        sizes = PanelSizing.controlSizes();
        for (int i = 0; i < sizes.length; i++) {
            String size = sizes[i];
            check(expected[i].equals(size), "size order and defensive array: " + i);
            check(Float.compare(scales[i], PanelSizing.controlScale(size)) == 0, "scale: " + size);
            equal(widths[i], PanelSizing.stripWidth(size), "strip: " + size);
            equal(faders[i], PanelSizing.faderHeightDp(size), "fader: " + size);
            equal(mutes[i], PanelSizing.muteHeightDp(size), "mute: " + size);
            equal(headers[i], PanelSizing.headerHeightDp(size), "header: " + size);
            equal(statuses[i], PanelSizing.statusHeightDp(size), "status: " + size);
            equal(returns[i], PanelSizing.returnHeightDp(size), "return: " + size);
            int chrome = 16 + headers[i] + statuses[i];
            equal(chrome + 80, PanelSizing.panelHeightDp(0, 0, size, 2000), "empty height: " + size);
            equal(chrome + faders[i], PanelSizing.panelHeightDp(1, 0, size, 2000), "fader height: " + size);
            equal(chrome + mutes[i] + 6, PanelSizing.panelHeightDp(0, 1, size, 2000), "mute-only height: " + size);
            equal(chrome + faders[i] + mutes[i] + 6, PanelSizing.panelHeightDp(1, 1, size, 2000), "mixed height: " + size);
            if (i > 0) {
                check(PanelSizing.controlScale(size) > PanelSizing.controlScale(sizes[i - 1]), "scale grows: " + size);
                check(widths[i] > widths[i - 1] && faders[i] > faders[i - 1], "fader dimensions grow: " + size);
            }
            if (i > 2) {
                check(PanelSizing.panelHeightDp(0, 1, size, 2000) >
                    PanelSizing.panelHeightDp(0, 1, sizes[i - 1], 2000), "mute-only panel grows: " + size);
            }
        }
        for (String fallback : new String[] { null, "unknown", "", "MAXIMUM" }) {
            check(Float.compare(1.15f, PanelSizing.controlScale(fallback)) == 0, "fallback scale");
            equal(76, PanelSizing.stripWidth(fallback), "fallback width");
            equal(210, PanelSizing.faderHeightDp(fallback), "fallback fader");
            equal(48, PanelSizing.muteHeightDp(fallback), "fallback mute");
            equal(24, PanelSizing.headerHeightDp(fallback), "fallback header");
            equal(12, PanelSizing.statusHeightDp(fallback), "fallback status");
            equal(34, PanelSizing.returnHeightDp(fallback), "fallback return");
        }
        // Control accessibility sizes do not silently enlarge the separate bubble preference.
        equal(48, PanelSizing.bubbleDiameter("maximum"), "unknown bubble size remains medium");
    }

    private static void screenBounds() {
        int[][] screens = { { 320, 568 }, { 568, 320 }, { 1080, 1920 }, { 80, 40 },
            { 0, 0 }, { -1, -20 }, { Integer.MAX_VALUE, Integer.MAX_VALUE } };
        int[] counts = { Integer.MIN_VALUE, -1, 0, 1, 3, 31, Integer.MAX_VALUE };
        for (String size : PanelSizing.controlSizes()) {
            for (int[] screen : screens) {
                for (int faders : counts) {
                    for (int buttons : counts) {
                        int width = PanelSizing.panelWidthDp(faders, buttons, size, screen[0]);
                        int height = PanelSizing.panelHeightDp(faders, buttons, size, screen[1]);
                        check(width >= 0 && width <= Math.max(0, screen[0]), "width within screen: " + size);
                        check(height >= 0 && height <= Math.max(0, screen[1]), "height within screen: " + size);
                        if (faders <= 0 && buttons <= 0) {
                            equal(PanelSizing.panelWidthDp(0, 0, size, screen[0]), width, "negative counts empty width");
                            equal(PanelSizing.panelHeightDp(0, 0, size, screen[1]), height, "negative counts empty height");
                        }
                        if (faders == Integer.MAX_VALUE || buttons == Integer.MAX_VALUE) {
                            equal(Math.max(0, screen[0]), width, "extreme count fills available width without overflow");
                        }
                    }
                }
            }
        }
        equal(320, PanelSizing.panelWidthDp(1, 0, "maximum", 320), "maximum single strip on narrow phone");
        equal(320, PanelSizing.panelHeightDp(1, 1, "maximum", 320), "maximum mixed panel on landscape phone");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static int width(int faders, int buttons) {
        return PanelSizing.panelWidthDp(faders, buttons, "medium", 1000);
    }

    private static int height(int faders, int buttons) {
        return PanelSizing.panelHeightDp(faders, buttons, "medium", 1000);
    }

    private static void equal(int expected, int actual, String message) {
        checks++;
        if (actual != expected) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
