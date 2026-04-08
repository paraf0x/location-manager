package ua.favn.bannerrenderer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BannerColorTest {

    @Test
    void allSixteenColorsExist() {
        assertEquals(16, BannerColor.values().length);
    }

    @ParameterizedTest
    @EnumSource(BannerColor.class)
    void rgbComponentsInValidRange(BannerColor color) {
        assertTrue(color.getRed() >= 0 && color.getRed() <= 255,
                color + " red out of range: " + color.getRed());
        assertTrue(color.getGreen() >= 0 && color.getGreen() <= 255,
                color + " green out of range: " + color.getGreen());
        assertTrue(color.getBlue() >= 0 && color.getBlue() <= 255,
                color + " blue out of range: " + color.getBlue());
    }

    @ParameterizedTest
    @EnumSource(BannerColor.class)
    void getRgbMatchesComponents(BannerColor color) {
        int expected = (0xFF << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        assertEquals(expected, color.getRgb(), "getRgb() mismatch for " + color);
    }

    @Test
    void spotCheckWhite() {
        assertEquals(255, BannerColor.WHITE.getRed());
        assertEquals(255, BannerColor.WHITE.getGreen());
        assertEquals(255, BannerColor.WHITE.getBlue());
    }

    @Test
    void spotCheckBlack() {
        assertEquals(25, BannerColor.BLACK.getRed());
        assertEquals(25, BannerColor.BLACK.getGreen());
        assertEquals(25, BannerColor.BLACK.getBlue());
    }

    @Test
    void spotCheckRed() {
        assertEquals(153, BannerColor.RED.getRed());
        assertEquals(51, BannerColor.RED.getGreen());
        assertEquals(51, BannerColor.RED.getBlue());
    }
}
