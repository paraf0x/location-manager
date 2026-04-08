package ua.favn.bannerrenderer;

import java.nio.file.Path;
import java.util.List;

public class Demo {
    public static void main(String[] args) throws Exception {
        Path outDir = Path.of("demo-output");
        BannerRenderer renderer = new BannerRenderer();
        int scale = 6; // 120x240 pixels for visibility
        int w = BannerRenderer.NATIVE_WIDTH * scale;
        int h = BannerRenderer.NATIVE_HEIGHT * scale;

        // 1. Simple solid red banner
        renderer.renderToFile(
                new BannerDefinition(BannerColor.RED),
                outDir.resolve("01_solid_red.png"), w, h);

        // 2. Swedish flag style (blue base, yellow cross)
        renderer.renderToFile(
                new BannerDefinition(BannerColor.BLUE, List.of(
                        new BannerLayer(BannerPattern.STRAIGHT_CROSS, BannerColor.YELLOW))),
                outDir.resolve("02_swedish_flag.png"), w, h);

        // 3. Japanese flag style (white base, red circle)
        renderer.renderToFile(
                new BannerDefinition(BannerColor.WHITE, List.of(
                        new BannerLayer(BannerPattern.CIRCLE, BannerColor.RED))),
                outDir.resolve("03_japanese_flag.png"), w, h);

        // 4. Creeper face (green base, black creeper)
        renderer.renderToFile(
                new BannerDefinition(BannerColor.LIME, List.of(
                        new BannerLayer(BannerPattern.CREEPER, BannerColor.BLACK))),
                outDir.resolve("04_creeper_face.png"), w, h);

        // 5. Complex heraldic shield (6 layers)
        renderer.renderToFile(
                new BannerDefinition(BannerColor.BLUE, List.of(
                        new BannerLayer(BannerPattern.GRADIENT, BannerColor.BLACK),
                        new BannerLayer(BannerPattern.RHOMBUS, BannerColor.YELLOW),
                        new BannerLayer(BannerPattern.STRIPE_CENTER, BannerColor.WHITE),
                        new BannerLayer(BannerPattern.STRIPE_MIDDLE, BannerColor.WHITE),
                        new BannerLayer(BannerPattern.FLOWER, BannerColor.RED),
                        new BannerLayer(BannerPattern.BORDER, BannerColor.ORANGE))),
                outDir.resolve("05_heraldic.png"), w, h);

        // 6. Checkered / brick pattern (white + gray bricks)
        renderer.renderToFile(
                new BannerDefinition(BannerColor.LIGHT_GRAY, List.of(
                        new BannerLayer(BannerPattern.BRICKS, BannerColor.GRAY),
                        new BannerLayer(BannerPattern.BORDER, BannerColor.BROWN))),
                outDir.resolve("06_brick_wall.png"), w, h);

        // 7. Sunset gradient (orange base with red/yellow gradients)
        renderer.renderToFile(
                new BannerDefinition(BannerColor.ORANGE, List.of(
                        new BannerLayer(BannerPattern.GRADIENT_UP, BannerColor.RED),
                        new BannerLayer(BannerPattern.GRADIENT, BannerColor.YELLOW),
                        new BannerLayer(BannerPattern.CIRCLE, BannerColor.YELLOW))),
                outDir.resolve("07_sunset.png"), w, h);

        // 8. Skull and crossbones pirate flag
        renderer.renderToFile(
                new BannerDefinition(BannerColor.BLACK, List.of(
                        new BannerLayer(BannerPattern.SKULL, BannerColor.WHITE),
                        new BannerLayer(BannerPattern.CROSS, BannerColor.WHITE))),
                outDir.resolve("08_pirate.png"), w, h);

        // 9. German flag approximation (black/red/yellow horizontal thirds)
        renderer.renderToFile(
                new BannerDefinition(BannerColor.RED, List.of(
                        new BannerLayer(BannerPattern.HALF_HORIZONTAL, BannerColor.BLACK),
                        new BannerLayer(BannerPattern.STRIPE_BOTTOM, BannerColor.YELLOW))),
                outDir.resolve("09_german_flag.png"), w, h);

        // 10. Mojang logo banner
        renderer.renderToFile(
                new BannerDefinition(BannerColor.WHITE, List.of(
                        new BannerLayer(BannerPattern.MOJANG, BannerColor.RED))),
                outDir.resolve("10_mojang.png"), w, h);

        System.out.println("Rendered 10 banners to " + outDir.toAbsolutePath());
    }
}
