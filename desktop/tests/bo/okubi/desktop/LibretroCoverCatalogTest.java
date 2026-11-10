package bo.okubi.desktop;

import java.util.List;

/** No-network regression for official libretro directory URLs and matching. */
public final class LibretroCoverCatalogTest {
    private static void check(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
    }

    public static void main(String[] args) {
        String listing = """
                <html><body>
                <a href="Pokemon%20-%20Emerald%20Version%20%28USA%2C%20Europe%29.png">Emerald</a>
                <a href="Pokemon%20-%20Edicion%20Esmeralda%20%28Spain%29.png">Esmeralda</a>
                <a href="Pokemon%20-%20Edicion%20Rojo%20Fuego%20%28Spain%29.png">Rojo Fuego</a>
                <a href="Pokemon%20-%20FireRed%20Version%20%28USA%29.png">FireRed</a>
                <a href="Super%20Mario%20World%20%28USA%29.png">Mario</a>
                <a href="../../secrets.png">unsafe</a>
                </body></html>
                """;
        List<String> names = LibretroCoverCatalog.filenames(listing);
        check(names.size() == 5, "Untrusted traversal href never included");
        check(LibretroCoverCatalog.bestMatch(listing, "Pokemon Esmeralda")
                    .equals("Pokemon - Edicion Esmeralda (Spain).png"),
                "Spanish personal ROM name picks exact Spanish-language cover");
        check(LibretroCoverCatalog.bestMatch(listing, "Pokémon Rojo Fuego")
                    .equals("Pokemon - Edicion Rojo Fuego (Spain).png"),
                "Accents and local game name supported");
        check(LibretroCoverCatalog.bestMatch(listing, "Pokemon Emerald")
                    .equals("Pokemon - Emerald Version (USA, Europe).png"),
                "English alias matched to official cover");
        check(LibretroCoverCatalog.bestMatch(listing, "Super Mario World (USA)")
                    .equals("Super Mario World (USA).png"),
                "DAT region suffix can match a filename without changing game");
        check(LibretroCoverCatalog.bestMatch(listing, "Pokemon Ruby") == null,
                "Never substitute a different game image");
        check(LibretroCoverCatalog.bestMatch(listing, "Pokemon") == null,
                "Broad substring matching is forbidden");
        check(LibretroCoverCatalog.bestMatch(listing, "Pokemon Blazing Emerald") == null,
                "Do not silently assign official cover to ROM hacks");
        check(LibretroCoverCatalog.directoryUrl(DesktopLibrary.Console.GBA)
                    .equals("https://thumbnails.libretro.com/"
                    + "Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/"),
                "Official URL and source path exact");
        check(LibretroCoverCatalog.coverUrl(DesktopLibrary.Console.GBA,
                    "Pokemon - Edicion Esmeralda (Spain).png")
                    .contains("Pokemon%20-%20Edicion%20Esmeralda%20%28Spain%29.png"),
                "Image URL uses safe encoding");
        for (DesktopLibrary.Console console : DesktopLibrary.Console.values())
            check(LibretroCoverCatalog.directoryUrl(console)
                    .startsWith("https://thumbnails.libretro.com/"),
                    "Every system downloads solely from official Libretro");
        try {
            LibretroCoverCatalog.coverUrl(DesktopLibrary.Console.GBA,
                    "../secrets.png");
            throw new AssertionError("Traversal must be rejected");
        } catch (IllegalArgumentException expected) { }
        System.out.println("LibretroCoverCatalogTest: PASS");
    }
}
