package bo.okubi.desktop;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * One-to-one, manually curated port-to-gameplay image mapping.
 *
 * The key is the exact catalogue GitHub repository identity. Images may live
 * in the same project's official website repository, but never in a different
 * game's gallery. Immutable 40-character Git commit pins avoid mutable artwork.
 * Unknown ports receive no online image; user overrides remain supported.
 */
public final class DesktopOfficialPortArtwork {
    private static final Map<String, URI> SOURCES = Map.ofEntries(
            Map.entry("diasurgical/devilutionx", URI.create(
                    "https://raw.githubusercontent.com/diasurgical/DevilutionX/86cd4137c32b513e429ebe2cb67bdaac7c1f244e/Packaging/vita/sce_sys/pic0.png")),
            Map.entry("dhewm/dhewm3", URI.create(
                    "https://raw.githubusercontent.com/dhewm/dhewm3/505c5c85303b5db3e4a6290550c7dc239c169b6c/docs/img/d3gui-example-16_9.png")),
            Map.entry("keriew/augustus", URI.create(
                    "https://raw.githubusercontent.com/Keriew/augustus/9f367f9936f4e28e074b13d968f8f5ec8e3ff117/doc/main-image.png")),
            Map.entry("arx/arxlibertatis", URI.create(
                    "https://raw.githubusercontent.com/arx/arx-libertatis.org/c3ea1a3dccd90c15042f3432aaca8bc19849ca21/htdocs/images/castle-of-arx-2x.jpg")),
            Map.entry("openttd/openttd", URI.create(
                    "https://raw.githubusercontent.com/OpenTTD/website/37ddb47c5d3b861195aff7a6b997837fdc433b2f/_screenshots/1.9-coldice_1.png")),
            Map.entry("warzone2100/warzone2100", URI.create(
                    "https://raw.githubusercontent.com/Warzone2100/warzone2100/253e370c2af78228a822df6afa82b6261dc94656/doc/images/awaymission.jpg")),
            Map.entry("luanti-org/luanti", URI.create(
                    "https://raw.githubusercontent.com/luanti-org/luanti/a10dc8dbff3b0af8295ae55df44f65b1d9ee92c7/games/devtest/screenshot.png")),
            Map.entry("openra/openra", URI.create(
                    "https://raw.githubusercontent.com/OpenRA/OpenRAWebsiteV3/adb7b675f4a045b5986c6496d42bb8dcce860aba/images/appdata/ingame-ra.png")),
            Map.entry("openrct2/openrct2", URI.create(
                    "https://raw.githubusercontent.com/OpenRCT2/openrct2.github.io/332d91fa74793212a31fd09bcb6796079d139bed/assets/img/screenshot.png")),
            Map.entry("openloco/openloco", URI.create(
                    "https://raw.githubusercontent.com/OpenLoco/OpenLoco.io/661dacf3180a81795aa215f7611c5045881e2985/assets/img/gallery-screenshot-0.png")),
            Map.entry("corsixth/corsixth", URI.create(
                    "https://raw.githubusercontent.com/CorsixTH/corsixth.github.io/3cf60d196e64dc8d3773e97de2997945e85ccbd1/assets/media/screenshots/1-medium.jpg")),
            Map.entry("endless-sky/endless-sky", URI.create(
                    "https://raw.githubusercontent.com/endless-sky/endless-sky.github.io/26142313e628bc98e68e0f600d0527fded30d604/images/screenshots/battle.jpg")),
            Map.entry("supertuxkart/stk-code", URI.create(
                    "https://raw.githubusercontent.com/supertuxkart/stk-website/548be5907f993074fa503232e518a1bd9e1d6b68/assets/gallery/SuperTuxKart_1.5_1.jpg"))
    );

    // Optional extra screenshots from each game's OWN official GitHub website
    // or documentation repository. Each URL is pinned to an audited commit.
    // Primary images remain exactly as in Phase 77D, preserving its cache.
    private static final Map<String, java.util.List<URI>> EXTRAS = Map.ofEntries(
            Map.entry("arx/arxlibertatis", java.util.List.of(
                    URI.create("https://raw.githubusercontent.com/arx/arx-libertatis.org/c3ea1a3dccd90c15042f3432aaca8bc19849ca21/htdocs/images/spellcasting-2x.jpg"))),
            Map.entry("openttd/openttd", java.util.List.of(
                    URI.create("https://raw.githubusercontent.com/OpenTTD/website/37ddb47c5d3b861195aff7a6b997837fdc433b2f/_screenshots/1.9-coldice_2.png"),
                    URI.create("https://raw.githubusercontent.com/OpenTTD/website/37ddb47c5d3b861195aff7a6b997837fdc433b2f/_screenshots/1.9-coldice_3.png"))),
            Map.entry("warzone2100/warzone2100", java.util.List.of(
                    URI.create("https://raw.githubusercontent.com/Warzone2100/warzone2100/253e370c2af78228a822df6afa82b6261dc94656/doc/images/artillery-far-away.jpg"),
                    URI.create("https://raw.githubusercontent.com/Warzone2100/warzone2100/253e370c2af78228a822df6afa82b6261dc94656/doc/images/building.jpg"))),
            Map.entry("openra/openra", java.util.List.of(
                    URI.create("https://raw.githubusercontent.com/OpenRA/OpenRAWebsiteV3/adb7b675f4a045b5986c6496d42bb8dcce860aba/images/appdata/ingame-cnc.png"),
                    URI.create("https://raw.githubusercontent.com/OpenRA/OpenRAWebsiteV3/adb7b675f4a045b5986c6496d42bb8dcce860aba/images/appdata/ingame-d2k.png"))),
            Map.entry("openloco/openloco", java.util.List.of(
                    URI.create("https://raw.githubusercontent.com/OpenLoco/OpenLoco.io/661dacf3180a81795aa215f7611c5045881e2985/assets/img/gallery-screenshot-1.png"),
                    URI.create("https://raw.githubusercontent.com/OpenLoco/OpenLoco.io/661dacf3180a81795aa215f7611c5045881e2985/assets/img/gallery-screenshot-2.png"))),
            Map.entry("corsixth/corsixth", java.util.List.of(
                    URI.create("https://raw.githubusercontent.com/CorsixTH/corsixth.github.io/3cf60d196e64dc8d3773e97de2997945e85ccbd1/assets/media/screenshots/2-medium.jpg"))),
            Map.entry("endless-sky/endless-sky", java.util.List.of(
                    URI.create("https://raw.githubusercontent.com/endless-sky/endless-sky.github.io/26142313e628bc98e68e0f600d0527fded30d604/images/screenshots/trading.jpg"),
                    URI.create("https://raw.githubusercontent.com/endless-sky/endless-sky.github.io/26142313e628bc98e68e0f600d0527fded30d604/images/screenshots/shipyard.jpg"))),
            Map.entry("supertuxkart/stk-code", java.util.List.of(
                    URI.create("https://raw.githubusercontent.com/supertuxkart/stk-website/548be5907f993074fa503232e518a1bd9e1d6b68/assets/gallery/SuperTuxKart_1.5_2.jpg"),
                    URI.create("https://raw.githubusercontent.com/supertuxkart/stk-website/548be5907f993074fa503232e518a1bd9e1d6b68/assets/gallery/SuperTuxKart_1.5_3.jpg")))
    );

    /** Index 0 is the established cover; following images load only on demand. */
    public static java.util.List<URI> gallery(DesktopPortArtworkService.Ref ref) {
        URI first = source(ref);
        if (first == null) return java.util.List.of();
        java.util.List<URI> more = EXTRAS.getOrDefault(
                ref.fullName().toLowerCase(Locale.ROOT), java.util.List.of());
        java.util.ArrayList<URI> all = new java.util.ArrayList<>(1 + more.size());
        all.add(first);
        all.addAll(more);
        return java.util.List.copyOf(all);
    }

    public static int galleryImageCount() {
        return SOURCES.size() + EXTRAS.values().stream().mapToInt(java.util.List::size).sum();
    }

    private DesktopOfficialPortArtwork() {}

    public static URI source(DesktopPortArtworkService.Ref ref) {
        return ref == null ? null : SOURCES.get(ref.fullName().toLowerCase(Locale.ROOT));
    }

    public static int curatedCount() { return SOURCES.size(); }

    /** Only the exact audited HTTPS raw links above can be fetched. */
    public static boolean permitted(URI url) {
        return url != null && "https".equals(url.getScheme())
                && "raw.githubusercontent.com".equals(url.getHost())
                && url.getUserInfo() == null
                && url.getPort() == -1
                && url.getRawQuery() == null
                && url.getRawFragment() == null
                && (SOURCES.containsValue(url)
                    || EXTRAS.values().stream().anyMatch(images -> images.contains(url)));
    }
}
