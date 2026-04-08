package ua.favn.bannerrenderer;

import java.util.List;
import java.util.Objects;

/**
 * Complete definition of a banner: a base color plus up to 6 pattern layers.
 */
public record BannerDefinition(BannerColor baseColor, List<BannerLayer> layers) {

    public static final int MAX_LAYERS = 6;

    public BannerDefinition {
        Objects.requireNonNull(baseColor, "baseColor must not be null");
        Objects.requireNonNull(layers, "layers must not be null");
        if (layers.size() > MAX_LAYERS) {
            throw new IllegalArgumentException(
                    "Banner cannot have more than " + MAX_LAYERS + " layers, got " + layers.size());
        }
        layers = List.copyOf(layers);
    }

    /** Create a solid banner with no patterns. */
    public BannerDefinition(BannerColor baseColor) {
        this(baseColor, List.of());
    }
}
