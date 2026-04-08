package ua.favn.bannerrenderer;

/**
 * Minecraft dye colors with their RGB values used for banner rendering.
 * Names match Bukkit's DyeColor enum for easy mapping.
 */
public enum BannerColor {
    WHITE(255, 255, 255),
    ORANGE(216, 127, 51),
    MAGENTA(178, 76, 216),
    LIGHT_BLUE(102, 153, 216),
    YELLOW(229, 229, 51),
    LIME(127, 204, 25),
    PINK(242, 127, 165),
    GRAY(76, 76, 76),
    LIGHT_GRAY(153, 153, 153),
    CYAN(76, 127, 153),
    PURPLE(127, 63, 178),
    BLUE(51, 76, 178),
    BROWN(102, 76, 51),
    GREEN(102, 127, 51),
    RED(153, 51, 51),
    BLACK(25, 25, 25);

    private final int red;
    private final int green;
    private final int blue;
    private final int rgb;

    BannerColor(int red, int green, int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.rgb = (0xFF << 24) | (red << 16) | (green << 8) | blue;
    }

    public int getRed() {
        return red;
    }

    public int getGreen() {
        return green;
    }

    public int getBlue() {
        return blue;
    }

    /** Fully opaque ARGB value. */
    public int getRgb() {
        return rgb;
    }
}
