package bo.okubi.desktop;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic, network-free catalogue audit. */
public final class DesktopPortsCatalogTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        List<DesktopPortsCatalog.Port> all = DesktopPortsCatalog.all();
        check(all.size() == 32, "Expect 32 verified GitHub PC ports");
        Set<String> ids = new HashSet<>();
        Set<URI> repositories = new HashSet<>();
        for (DesktopPortsCatalog.Port port : all) {
            check(ids.add(port.id()), "Duplicate port ID");
            check(repositories.add(port.github()), "Duplicate GitHub repository");
            check("https".equals(port.github().getScheme()), "HTTPS required");
            check("github.com".equals(port.github().getHost()), "Official GitHub only");
            check(port.releases().toString().startsWith(port.github().toString()),
                    "Releases must stay inside the same repository");
            check(port.description().length() >= 18, "Project description required");
            check(port.instruction().length() >= 18, "Install/data warning required");
            check(DesktopPortsCatalog.byId(port.id()) == port, "Stable IDs");
        }
        check(DesktopPortsCatalog.byId("unknown-port") == null, "No arbitrary repository");
        check(DesktopPortsCatalog.filter("", "Todos", null).size() == all.size(),
                "All entries shown by default");
        check(DesktopPortsCatalog.filter("dIAbLo", "Todos", null).size() == 1,
                "Case-insensitive search");
        check(DesktopPortsCatalog.filter("devilution", "Todos", null).get(0).id()
                        .equals("devilutionx"), "Matching project name");
        List<DesktopPortsCatalog.Port> freebies = DesktopPortsCatalog.filter(
                "", "Todos", DesktopPortsCatalog.Assets.FREE);
        check(!freebies.isEmpty(), "Free project filter returns games");
        check(freebies.stream().allMatch(p -> p.assets() == DesktopPortsCatalog.Assets.FREE),
                "Never label proprietary assets as free");
        check(DesktopPortsCatalog.byId("openrct2").assets()
                == DesktopPortsCatalog.Assets.ORIGINAL, "OpenRCT2 needs original assets");
        check(DesktopPortsCatalog.byId("devilutionx").assets()
                == DesktopPortsCatalog.Assets.ORIGINAL, "Diablo original data required");
        check(DesktopPortsCatalog.byId("endlesssky").assets()
                == DesktopPortsCatalog.Assets.FREE, "Free games recognizable");
        check(DesktopPortsCatalog.filter("", "FPS", null).size() >= 4,
                "Genre filtering");
        check(DesktopPortsCatalog.filter("no-such-game", "Todos", null).isEmpty(),
                "Unknown titles never silently fall back to all");
        check(DesktopPortsCatalog.filter("", "FPS", DesktopPortsCatalog.Assets.FREE).isEmpty(),
                "Combined filters");
        boolean blocked = false;
        try {
            new DesktopPortsCatalog.Port("injected", "X", "Y",
                    DesktopPortsCatalog.Assets.FREE, DesktopPortsCatalog.Load.LIGHT,
                    "This would be an untrusted game repository",
                    "owner/evil", "repo", true, "This is not allowed.");
        } catch (IllegalArgumentException expected) { blocked = true; }
        check(blocked, "Repository owner injection must be refused");
        System.out.println("DesktopPortsCatalogTest: PASS (" + all.size() + " GitHub projects)");
    }
}
