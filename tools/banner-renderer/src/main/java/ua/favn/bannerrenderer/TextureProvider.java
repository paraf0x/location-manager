package ua.favn.bannerrenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.imageio.ImageIO;

/**
 * Downloads, caches, and crops Minecraft banner pattern textures.
 * <p>
 * Pattern textures are 64x64 entity UV maps. The visible front face of the
 * banner cloth is cropped from region (1, 1) with size 20x40 pixels.
 */
public class TextureProvider {

    private static final String DEFAULT_CDN =
            "https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/entity/banner/";

    static final int FACE_X = 1;
    static final int FACE_Y = 1;
    static final int FACE_WIDTH = 20;
    static final int FACE_HEIGHT = 40;

    private final String cdnBaseUrl;
    private final Path cacheDir;
    private final ConcurrentMap<String, BufferedImage> memoryCache = new ConcurrentHashMap<>();

    public TextureProvider(Path cacheDir) {
        this(cacheDir, DEFAULT_CDN);
    }

    public TextureProvider(Path cacheDir, String cdnBaseUrl) {
        this.cacheDir = cacheDir;
        this.cdnBaseUrl = cdnBaseUrl.endsWith("/") ? cdnBaseUrl : cdnBaseUrl + "/";
    }

    /**
     * Get the cropped front-face mask for a pattern.
     * Uses in-memory cache, then disk cache, then downloads from CDN.
     */
    public BufferedImage getFrontFace(BannerPattern pattern) throws IOException {
        String name = pattern.getTextureName();
        BufferedImage cached = memoryCache.get(name);
        if (cached != null) {
            return cached;
        }

        BufferedImage face = loadFromDiskCache(name);
        if (face == null) {
            BufferedImage full = downloadTexture(name);
            face = cropFrontFace(full);
            saveToDiskCache(name, face);
        }

        memoryCache.put(name, face);
        return face;
    }

    private BufferedImage loadFromDiskCache(String name) {
        Path file = cacheDir.resolve(name + ".png");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return ImageIO.read(file.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private void saveToDiskCache(String name, BufferedImage image) {
        try {
            Files.createDirectories(cacheDir);
            ImageIO.write(image, "png", cacheDir.resolve(name + ".png").toFile());
        } catch (IOException e) {
            // Cache write failure is non-fatal
        }
    }

    private BufferedImage downloadTexture(String name) throws IOException {
        String url = cdnBaseUrl + name + ".png";
        try {
            BufferedImage image = ImageIO.read(URI.create(url).toURL());
            if (image == null) {
                throw new IOException("Failed to decode image from " + url);
            }
            return image;
        } catch (IOException e) {
            throw new IOException("Failed to download texture " + name + " from " + url, e);
        }
    }

    static BufferedImage cropFrontFace(BufferedImage fullTexture) {
        if (fullTexture.getWidth() < FACE_X + FACE_WIDTH
                || fullTexture.getHeight() < FACE_Y + FACE_HEIGHT) {
            throw new IllegalArgumentException(
                    "Texture too small: expected at least " + (FACE_X + FACE_WIDTH) + "x"
                            + (FACE_Y + FACE_HEIGHT) + ", got "
                            + fullTexture.getWidth() + "x" + fullTexture.getHeight());
        }
        return fullTexture.getSubimage(FACE_X, FACE_Y, FACE_WIDTH, FACE_HEIGHT);
    }
}
