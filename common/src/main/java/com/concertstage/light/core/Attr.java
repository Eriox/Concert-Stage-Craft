package com.concertstage.light.core;

/**
 * Attributes a fixture can have. Every value is normalised 0..1 in the programmer, the cues and the presets; the profile of the
 * fixture turns it into the physical value (degrees, beam angle, gobo slot...). NaN means "not set" in a sparse {@link Look}.
 *
 * <ul>
 * <li>PAN / TILT: 0.5 is the neutral position (head straight ahead), 0 and 1 the mechanical limits of the profile.</li>
 * <li>RED / GREEN / BLUE / WHITE: additive emitters (the VORTEX mixes its colour with CMY flags: same 0..1 RGB here).</li>
 * <li>CTO: colour temperature of the white emitters, 0 = 6500 K cold, 1 = 2700 K warm.</li>
 * <li>STROBE: 0 = open shutter, above 0 the flash rate from 1 Hz to the profile maximum (capped by the safety settings).</li>
 * <li>GOBO: slot index / (gobo count), 0 = open. GOBO_ROT and PRISM_ROT: 0.5 = stopped, below / above = turning one way / the other.</li>
 * <li>PRISM: 0 = out, 1 = 3-facet prism in. IRIS: 1 = open, 0 = closed to the smallest beam.</li>
 * </ul>
 */
public enum Attr {
    DIMMER("Dim", "intensity"), PAN("Pan", "position"), TILT("Tilt", "position"),
    RED("R", "color"), GREEN("G", "color"), BLUE("B", "color"), WHITE("W", "color"), CTO("CTO", "color"),
    ZOOM("Zoom", "beam"), FOCUS("Focus", "beam"), IRIS("Iris", "beam"), GOBO("Gobo", "beam"), GOBO_ROT("Gobo rot", "beam"),
    PRISM("Prism", "beam"), PRISM_ROT("Prism rot", "beam"), STROBE("Strobe", "intensity"),
    // Append only: ordinal IDs are persisted in existing shows and network commands.
    CYAN("Cyan", "color"), MAGENTA("Magenta", "color"), YELLOW("Yellow", "color"), COLOR_WHEEL("Color wheel", "color"),
    FROST("Frost", "beam"), DOUSER("Douser", "intensity"),
    BLADE_1("Blade 1", "beam"), BLADE_2("Blade 2", "beam"), BLADE_3("Blade 3", "beam"), BLADE_4("Blade 4", "beam"),
    FRAME_ROT("Frame rotation", "beam"), LASER_PATTERN("Laser pattern", "beam"), LASER_SIZE("Scan size", "beam"),
    LASER_SPEED("Scan speed", "beam"), MOVE_SPEED("Motor speed", "position"),
    GOBO_2("Gobo 2", "beam"), GOBO_2_ROT("Gobo 2 rot", "beam"), ANIMATION("Animation", "beam"),
    PRISM_2("Prism 2", "beam"), PRISM_2_ROT("Prism 2 rot", "beam"), SHUTTER("Shutter", "intensity"),
    // Rev. 21: the three light zones of the PULSESTRIKE M and the FLASHCORE FX (top RGB bar, centre white strobe, bottom RGB bar), each
    // with its own intensity and strobe, the bars with their own colour. See FixtureProfile#section.
    TOP_DIM("Top dim", "intensity"), TOP_RED("Top R", "color"), TOP_GREEN("Top G", "color"), TOP_BLUE("Top B", "color"),
    TOP_STROBE("Top strobe", "intensity"), MID_DIM("Centre dim", "intensity"), MID_STROBE("Centre strobe", "intensity"),
    BOT_DIM("Bottom dim", "intensity"), BOT_RED("Bottom R", "color"), BOT_GREEN("Bottom G", "color"), BOT_BLUE("Bottom B", "color"),
    BOT_STROBE("Bottom strobe", "intensity");

    public static final Attr[] ALL = values();
    public static final int COUNT = ALL.length;

    public final String label;
    /** Feature group, as on a real desk: intensity, position, color, beam. */
    public final String family;

    Attr(String label, String family) {
        this.label = label;
        this.family = family;
    }

    /** Default value of a fixture that nothing controls (lamp off, head straight, white, open beam). */
    public float home() {
        return switch (this) {
            case GOBO_2, ANIMATION, PRISM_2, CYAN, MAGENTA, YELLOW, COLOR_WHEEL, FROST, BLADE_1, BLADE_2, BLADE_3, BLADE_4, LASER_PATTERN, MOVE_SPEED, DIMMER, STROBE, GOBO, PRISM, CTO, FOCUS -> 0f;
            // rev. 21: a zone follows the fixture until it is told otherwise: full intensity of the fixture, white (its colour times the
            // colour of the fixture), no strobe of its own (the shared strobe still applies)
            case TOP_STROBE, MID_STROBE, BOT_STROBE -> 0f;
            case GOBO_2_ROT, PRISM_2_ROT, FRAME_ROT, LASER_SPEED, PAN, TILT, GOBO_ROT, PRISM_ROT -> 0.5f;
            case ZOOM -> 0.35f;
            case IRIS -> 1f;
            default -> 1f;                       // RED GREEN BLUE WHITE: white light
        };
    }

    /**
     * Attributes that jump instead of fading (a gobo slot, a prism) unless the cue says otherwise. Rev. 19: the strobe too, as on a
     * real desk (shutter channels snap): a cue fading from a strobe to an open shutter used to go down through every rate (8 Hz, 4,
     * 2, 1...), and one fading from open to a strobe started with seconds of slow 1 Hz flashes — flashes nobody had asked for. It now
     * changes at the start of the fade: the strobe stops at once, or starts at its rate.
     */
    public boolean snaps() {
        return this == GOBO || this == PRISM || this == COLOR_WHEEL || this == LASER_PATTERN || this == GOBO_2 || this == PRISM_2 || this == STROBE
            || this == TOP_STROBE || this == MID_STROBE || this == BOT_STROBE;
    }

    /** Intensity attributes combine Highest-Takes-Precedence between playbacks; the others Latest-Takes-Precedence by priority. */
    public boolean htp() {
        return this == DIMMER;
    }

    /** Rev. 21: an attribute of one light zone (top / centre / bottom) of a zone fixture. */
    public boolean zoneAttr() {
        return ordinal() >= TOP_DIM.ordinal() && ordinal() <= BOT_STROBE.ordinal();
    }

    /** Rev. 21: a strobe channel (the shared one or the strobe of one zone): same DMX ranges. */
    public boolean strobe() {
        return this == STROBE || this == TOP_STROBE || this == MID_STROBE || this == BOT_STROBE;
    }

    public static Attr byId(int id) {
        return id >= 0 && id < COUNT ? ALL[id] : null;
    }
}
