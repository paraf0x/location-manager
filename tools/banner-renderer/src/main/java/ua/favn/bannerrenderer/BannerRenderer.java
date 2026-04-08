package ua.favn.bannerrenderer;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Renders Minecraft banners (base color + up to 6 pattern layers) as {@link BufferedImage}.
 * <p>
 * The native resolution matches the banner front face in Minecraft's entity texture: 20x40 pixels.
 * Output can be scaled to any target size.
 */
public class BannerRenderer {

    static final int NATIVE_WIDTH = TextureProvider.FACE_WIDTH;
    static final int NATIVE_HEIGHT = TextureProvider.FACE_HEIGHT;

    private final TextureProvider textureProvider;

    public BannerRenderer() {
        this(defaultCacheDir());
    }

    public BannerRenderer(Path cacheDir) {
        this.textureProvider = new TextureProvider(cacheDir);
    }

    /** Visible for testing with a custom/mock TextureProvider. */
    BannerRenderer(TextureProvider textureProvider) {
        this.textureProvider = textureProvider;
    }

    /**
     * Render a banner at native resolution (20x40).
     */
    public BufferedImage render(BannerDefinition banner) throws IOException {
        return render(banner, NATIVE_WIDTH, NATIVE_HEIGHT);
    }

    /**
     * Render a banner scaled to the given dimensions.
     */
    public BufferedImage render(BannerDefinition banner, int width, int height) throws IOException {
        BufferedImage canvas = renderNative(banner);
        if (width == NATIVE_WIDTH && height == NATIVE_HEIGHT) {
            return canvas;
        }
        return scale(canvas, width, height);
    }

    /**
     * Render a banner and write it to a PNG file.
     */
    public void renderToFile(BannerDefinition banner, Path outputFile) throws IOException {
        renderToFile(banner, outputFile, NATIVE_WIDTH, NATIVE_HEIGHT);
    }

    /**
     * Render a banner at the given size and write it to a PNG file.
     */
    public void renderToFile(BannerDefinition banner, Path outputFile, int width, int height)
            throws IOException {
        BufferedImage image = render(banner, width, height);
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ImageIO.write(image, "png", outputFile.toFile());
    }

    private BufferedImage renderNative(BannerDefinition banner) throws IOException {
        BufferedImage canvas = new BufferedImage(NATIVE_WIDTH, NATIVE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            fillBaseColor(g, banner.baseColor());
            for (BannerLayer layer : banner.layers()) {
                drawLayer(g, layer);
            }
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private void fillBaseColor(Graphics2D g, BannerColor color) {
        g.setColor(new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue()));
        g.fillRect(0, 0, NATIVE_WIDTH, NATIVE_HEIGHT);
    }

    private void drawLayer(Graphics2D g, BannerLayer layer) throws IOException {
        BufferedImage mask = textureProvider.getFrontFace(layer.pattern());
        BufferedImage colorized = colorize(mask, layer.color());
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(colorized, 0, 0, null);
    }

    /**
     * Colorize a grayscale pattern mask with a dye color.
     * For each pixel: RGB = mask.RGB * color.RGB / 255, alpha preserved.
     */
    static BufferedImage colorize(BufferedImage mask, BannerColor color) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int cr = color.getRed();
        int cg = color.getGreen();
        int cb = color.getBlue();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = mask.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int mr = (pixel >> 16) & 0xFF;
                int mg = (pixel >> 8) & 0xFF;
                int mb = pixel & 0xFF;

                int nr = (mr * cr) / 255;
                int ng = (mg * cg) / 255;
                int nb = (mb * cb) / 255;

                result.setRGB(x, y, (alpha << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return result;
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static Path defaultCacheDir() {
        return Path.of(System.getProperty("java.io.tmpdir"), "banner-renderer-cache");
    }
}
