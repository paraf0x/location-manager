package ua.favn.baseManager.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URL;
import org.junit.jupiter.api.Test;

class Pl3xmapManagerTest {

    private static final String CACIT_TEXTURES_BASE64 =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA1YWNiNmU5YjY4MjFlYjYwNDgyNjE2YWM3MDBjYWZmZTMzMzE5YTMwODEyYWM5ZjFlYTBjNjcxYWJjMTc3NyJ9fX0=";

    private static final String UNICORNBEEF_TEXTURES_BASE64 =
        "ewogICJ0aW1lc3RhbXAiIDogMTc3MTYxNjMzNzY1MCwKICAicHJvZmlsZUlkIiA6ICIxYTQwNTcxNDc4YmU0MTFjOTBmMDc3MGZmNTI1NTk0MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJVbmljb3JuQmVlZiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9lMDBmZTA5MTEyMzNkNDE4ZDhjNzhmOWRkODk0ZjZmM2UzYWU1YTZmM2U3MzliZTBjY2RmNGQ3ODM4MWMzMzciCiAgICB9LAogICAgIkNBUEUiIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVlNmYzMTkzZTc0Y2QxNmNkZDY2MzdkOWJhZTU0ODRlM2EzN2ZmMmExNGMyZDE1N2M2NTlhMDc4MTBiMWJkY2EiCiAgICB9CiAgfQp9";

    @Test
    void extractUrlFromTextureValue_decodes_cacit_hdb_head() throws Exception {
        URL url = Pl3xmapManager.extractUrlFromTextureValue(CACIT_TEXTURES_BASE64);
        assertNotNull(url);
        assertEquals(
            "http://textures.minecraft.net/texture/205acb6e9b6821eb60482616ac700caffe33319a30812ac9f1ea0c671abc1777",
            url.toString());
    }

    @Test
    void extractUrlFromTextureValue_decodes_real_player_head_with_cape() {
        URL url = Pl3xmapManager.extractUrlFromTextureValue(UNICORNBEEF_TEXTURES_BASE64);
        assertNotNull(url);
        assertEquals(
            "http://textures.minecraft.net/texture/e00fe0911233d418d8c78f9dd894f6f3e3ae5a6f3e739be0ccdf4d78381c337",
            url.toString());
    }

    @Test
    void extractUrlFromTextureValue_returns_null_for_empty_input() {
        assertNull(Pl3xmapManager.extractUrlFromTextureValue(""));
    }

    @Test
    void extractUrlFromTextureValue_returns_null_for_invalid_base64() {
        assertNull(Pl3xmapManager.extractUrlFromTextureValue("!!!not-base64!!!"));
    }
}
