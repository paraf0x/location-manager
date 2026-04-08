package ua.favn.bannerrenderer;

/**
 * All 42 Minecraft 1.21 banner pattern types.
 * Each value stores the texture filename used in the Minecraft asset path
 * {@code textures/entity/banner/<name>.png}.
 */
public enum BannerPattern {
    BASE("base"),
    STRIPE_BOTTOM("stripe_bottom"),
    STRIPE_TOP("stripe_top"),
    STRIPE_LEFT("stripe_left"),
    STRIPE_RIGHT("stripe_right"),
    STRIPE_CENTER("stripe_center"),
    STRIPE_MIDDLE("stripe_middle"),
    STRIPE_DOWNRIGHT("stripe_downright"),
    STRIPE_DOWNLEFT("stripe_downleft"),
    SMALL_STRIPES("small_stripes"),
    CROSS("cross"),
    STRAIGHT_CROSS("straight_cross"),
    DIAGONAL_LEFT("diagonal_left"),
    DIAGONAL_RIGHT("diagonal_right"),
    DIAGONAL_UP_LEFT("diagonal_up_left"),
    DIAGONAL_UP_RIGHT("diagonal_up_right"),
    HALF_VERTICAL("half_vertical"),
    HALF_VERTICAL_RIGHT("half_vertical_right"),
    HALF_HORIZONTAL("half_horizontal"),
    HALF_HORIZONTAL_BOTTOM("half_horizontal_bottom"),
    SQUARE_BOTTOM_LEFT("square_bottom_left"),
    SQUARE_BOTTOM_RIGHT("square_bottom_right"),
    SQUARE_TOP_LEFT("square_top_left"),
    SQUARE_TOP_RIGHT("square_top_right"),
    TRIANGLE_BOTTOM("triangle_bottom"),
    TRIANGLE_TOP("triangle_top"),
    TRIANGLES_BOTTOM("triangles_bottom"),
    TRIANGLES_TOP("triangles_top"),
    CIRCLE("circle"),
    RHOMBUS("rhombus"),
    BORDER("border"),
    CURLY_BORDER("curly_border"),
    BRICKS("bricks"),
    GRADIENT("gradient"),
    GRADIENT_UP("gradient_up"),
    CREEPER("creeper"),
    SKULL("skull"),
    FLOWER("flower"),
    MOJANG("mojang"),
    GLOBE("globe"),
    PIGLIN("piglin"),
    FLOW("flow"),
    GUSTER("guster");

    private final String textureName;

    BannerPattern(String textureName) {
        this.textureName = textureName;
    }

    /** Minecraft asset filename without extension (e.g. {@code "stripe_bottom"}). */
    public String getTextureName() {
        return textureName;
    }
}
