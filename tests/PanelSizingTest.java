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
        System.out.println("PanelSizingTest passed: " + checks + " checks");
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
