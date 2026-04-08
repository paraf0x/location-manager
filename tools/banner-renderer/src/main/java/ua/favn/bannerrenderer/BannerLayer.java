package ua.favn.bannerrenderer;

import java.util.Objects;

/**
 * A single pattern layer on a banner: a pattern type combined with a dye color.
 */
public record BannerLayer(BannerPattern pattern, BannerColor color) {
    public BannerLayer {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(color, "color must not be null");
    }
}
