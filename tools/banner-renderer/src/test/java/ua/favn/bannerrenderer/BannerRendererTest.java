package ua.favn.bannerrenderer;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BannerRendererTest {

    @TempDir
    Path tempDir;

    private BannerRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new BannerRenderer(new StubTextureProvider());
    }

    @Test
    void solidBanner_allPixelsAreBaseColor() throws IOException {
        var banner = new BannerDefinition(BannerColor.RED);
        BufferedImage image = renderer.render(banner);

        assertEquals(BannerRenderer.NATIVE_WIDTH, image.getWidth());
        assertEquals(BannerRenderer.NATIVE_HEIGHT, image.getHeight());

        int expectedRgb = BannerColor.RED.getRgb();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(expectedRgb, image.getRGB(x, y),
                        "Pixel mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void bannerWithOneLayer_differsFromSolid() throws IOException {
        var solid = new BannerDefinition(BannerColor.WHITE);
        var layered = new BannerDefinition(BannerColor.WHITE,
                List.of(new BannerLayer(BannerPattern.STRIPE_CENTER, BannerColor.BLUE)));

        BufferedImage solidImg = renderer.render(solid);
        BufferedImage layeredImg = renderer.render(layered);

        assertNotNull(layeredImg);
        assertEquals(solidImg.getWidth(), layeredImg.getWidth());
        assertEquals(solidImg.getHeight(), layeredImg.getHeight());

        boolean anyDifference = false;
        for (int y = 0; y < solidImg.getHeight() && !anyDifference; y++) {
            for (int x = 0; x < solidImg.getWidth() && !anyDifference; x++) {
                if (solidImg.getRGB(x, y) != layeredImg.getRGB(x, y)) {
                    anyDifference = true;
                }
            }
        }
        assertTrue(anyDifference, "Layered banner should differ from solid banner");
    }

    @Test
    void bannerWithSixLayers_doesNotThrow() throws IOException {
        var layers = List.of(
                new BannerLayer(BannerPattern.STRIPE_BOTTOM, BannerColor.WHITE),
                new BannerLayer(BannerPattern.STRIPE_TOP, BannerColor.BLUE),
                new BannerLayer(BannerPattern.CROSS, BannerColor.RED),
                new BannerLayer(BannerPattern.BORDER, BannerColor.BLACK),
                new BannerLayer(BannerPattern.CIRCLE, BannerColor.YELLOW),
                new BannerLayer(BannerPattern.GRADIENT, BannerColor.GREEN));
        var banner = new BannerDefinition(BannerColor.ORANGE, layers);

        BufferedImage image = renderer.render(banner);
        assertNotNull(image);
        assertEquals(BannerRenderer.NATIVE_WIDTH, image.getWidth());
        assertEquals(BannerRenderer.NATIVE_HEIGHT, image.getHeight());
    }

    @Test
    void sevenLayers_throwsException() {
        var layers = List.of(
                new BannerLayer(BannerPattern.BASE, BannerColor.WHITE),
                new BannerLayer(BannerPattern.BASE, BannerColor.WHITE),
                new BannerLayer(BannerPattern.BASE, BannerColor.WHITE),
                new BannerLayer(BannerPattern.BASE, BannerColor.WHITE),
                new BannerLayer(BannerPattern.BASE, BannerColor.WHITE),
                new BannerLayer(BannerPattern.BASE, BannerColor.WHITE),
                new BannerLayer(BannerPattern.BASE, BannerColor.WHITE));

        assertThrows(IllegalArgumentException.class,
                () -> new BannerDefinition(BannerColor.RED, layers));
    }

    @Test
    void renderWithCustomSize_scalesOutput() throws IOException {
        var banner = new BannerDefinition(BannerColor.BLUE);
        BufferedImage image = renderer.render(banner, 40, 80);

        assertEquals(40, image.getWidth());
        assertEquals(80, image.getHeight());
    }

    @Test
    void renderToFile_producesValidPng() throws IOException {
        var banner = new BannerDefinition(BannerColor.GREEN,
                List.of(new BannerLayer(BannerPattern.CREEPER, BannerColor.BLACK)));
        Path output = tempDir.resolve("test_banner.png");

        renderer.renderToFile(banner, output);

        assertTrue(Files.exists(output));
        assertTrue(Files.size(output) > 0);

        BufferedImage read = ImageIO.read(output.toFile());
        assertNotNull(read);
        assertEquals(BannerRenderer.NATIVE_WIDTH, read.getWidth());
        assertEquals(BannerRenderer.NATIVE_HEIGHT, read.getHeight());
    }

    @Test
    void renderToFile_withCustomSize() throws IOException {
        var banner = new BannerDefinition(BannerColor.CYAN);
        Path output = tempDir.resolve("sub/dir/banner.png");

        renderer.renderToFile(banner, output, 48, 96);

        BufferedImage read = ImageIO.read(output.toFile());
        assertEquals(48, read.getWidth());
        assertEquals(96, read.getHeight());
    }

    @Test
    void colorize_transparentPixelsStayTransparent() {
        BufferedImage mask = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        // leave all pixels at default (0x00000000 = fully transparent)

        BufferedImage result = BannerRenderer.colorize(mask, BannerColor.RED);

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(0, (result.getRGB(x, y) >> 24) & 0xFF,
                        "Transparent pixel should remain transparent");
            }
        }
    }

    @Test
    void colorize_whitePixelTakesDyeColor() {
        BufferedImage mask = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        mask.setRGB(0, 0, 0xFFFFFFFF); // opaque white

        BufferedImage result = BannerRenderer.colorize(mask, BannerColor.RED);
        int pixel = result.getRGB(0, 0);

        assertEquals(255, (pixel >> 24) & 0xFF, "alpha");
        assertEquals(BannerColor.RED.getRed(), (pixel >> 16) & 0xFF, "red");
        assertEquals(BannerColor.RED.getGreen(), (pixel >> 8) & 0xFF, "green");
        assertEquals(BannerColor.RED.getBlue(), pixel & 0xFF, "blue");
    }

    @Test
    void colorize_halfIntensityPixel() {
        BufferedImage mask = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        mask.setRGB(0, 0, 0xFF808080); // opaque mid-gray (128)

        BufferedImage result = BannerRenderer.colorize(mask, BannerColor.WHITE);
        int pixel = result.getRGB(0, 0);

        // 128 * 255 / 255 = 128
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        assertTrue(Math.abs(r - 128) <= 1, "red should be ~128, got " + r);
        assertTrue(Math.abs(g - 128) <= 1, "green should be ~128, got " + g);
        assertTrue(Math.abs(b - 128) <= 1, "blue should be ~128, got " + b);
    }

    @Test
    void nullBaseColor_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new BannerDefinition(null, List.of()));
    }

    @Test
    void nullLayers_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new BannerDefinition(BannerColor.WHITE, null));
    }

    @Test
    void nullPatternInLayer_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new BannerLayer(null, BannerColor.WHITE));
    }

    @Test
    void nullColorInLayer_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new BannerLayer(BannerPattern.BASE, null));
    }

    /**
     * Stub TextureProvider that returns synthetic pattern masks without network access.
     * Generates a vertical stripe pattern in the center third of the mask.
     */
    private static class StubTextureProvider extends TextureProvider {

        StubTextureProvider() {
            super(Path.of(System.getProperty("java.io.tmpdir"), "banner-test-stub"));
        }

        @Override
        public BufferedImage getFrontFace(BannerPattern pattern) {
            int w = TextureProvider.FACE_WIDTH;
            int h = TextureProvider.FACE_HEIGHT;
            BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

            int thirdStart = w / 3;
            int thirdEnd = 2 * w / 3;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (x >= thirdStart && x < thirdEnd) {
                        mask.setRGB(x, y, 0xFFFFFFFF); // opaque white in the stripe area
                    }
                    // else stays transparent
                }
            }
            return mask;
        }
    }
}
