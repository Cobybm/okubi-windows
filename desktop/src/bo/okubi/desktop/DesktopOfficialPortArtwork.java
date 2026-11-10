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
                    + "doc/main-image.png")
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
