package bo.okubi.desktop;

import java.net.URI;
import java.util.Map;

/**
 * Explicitly reviewed visuals from original open-source project repositories.
 * Never bundled with OKUBI. Each link points to an exact upstream Git commit,
 * not a mutable branch or arbitrary user-provided URL.
 */
public final class DesktopOfficialPortArtwork {
    private static final Map<String, URI> SOURCES = Map.of(
            "diasurgical/devilutionx", URI.create(
                    "https://raw.githubusercontent.com/diasurgical/DevilutionX/"
                    + "86cd4137c32b513e429ebe2cb67bdaac7c1f244e/"
                    + "Packaging/vita/sce_sys/pic0.png"),
            "dhewm/dhewm3", URI.create(
                    "https://raw.githubusercontent.com/dhewm/dhewm3/"
                    + "505c5c85303b5db3e4a6290550c7dc239c169b6c/"
                    + "docs/img/d3gui-example-16_9.png"),
            "keriew/augustus", URI.create(
                    "https://raw.githubusercontent.com/Keriew/augustus/"
                    + "9f367f9936f4e28e074b13d968f8f5ec8e3ff117/"
                    + "doc/main-image.png"),
            "arx/arxlibertatis", URI.create(
                    "https://raw.githubusercontent.com/arx/arx-libertatis.org/"
                    + "c3ea1a3dccd90c15042f3432aaca8bc19849ca21/"
                    + "htdocs/images/castle-of-arx-2x.jpg"),
            "openttd/openttd", URI.create(
                    "https://raw.githubusercontent.com/OpenTTD/website/"
                    + "37ddb47c5d3b861195aff7a6b997837fdc433b2f/"
                    + "_screenshots/1.9-coldice_1.png"),
            "warzone2100/warzone2100", URI.create(
                    "https://raw.githubusercontent.com/Warzone2100/warzone2100/"
                    + "253e370c2af78228a822df6afa82b6261dc94656/"
                    + "doc/images/awaymission.jpg"),
            "luanti-org/luanti", URI.create(
                    "https://raw.githubusercontent.com/luanti-org/luanti/"
                    + "a10dc8dbff3b0af8295ae55df44f65b1d9ee92c7/"
                    + "games/devtest/screenshot.png"),
            "openra/openra", URI.create(
                    "https://raw.githubusercontent.com/OpenRA/OpenRA/"
                    + "b4d95a56717d8d0070f195e343f6c2728ce87900/"
                    + "packaging/artwork/macos-background-2x.png")
    );

    private DesktopOfficialPortArtwork() {}

    public static URI source(DesktopPortArtworkService.Ref ref) {
        return SOURCES.get(ref.fullName().toLowerCase(java.util.Locale.ROOT));
    }

    public static int curatedCount() { return SOURCES.size(); }

    /** Audit exact trusted GitHub raw URL: no query, userinfo, port or redirect. */
    public static boolean permitted(URI url) {
        return url != null && "https".equals(url.getScheme())
                && "raw.githubusercontent.com".equals(url.getHost())
                && url.getUserInfo() == null
                && url.getPort() == -1
                && url.getRawQuery() == null
                && url.getRawFragment() == null
                && SOURCES.containsValue(url);
    }
}
