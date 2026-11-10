package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase76 read-only aggregation of existing retro and Windows port libraries.
 * Never copies ROMs, reads saves, changes favorites or launches executables.
 */
public final class DesktopUnifiedLibrary {
    public enum Kind { RETRO, PC_PORT, GITHUB_PORT }
    public enum Filter {
        ALL("TODO"), RETRO("RETRO"), PC("PORTS PC"),
        FAVORITES("FAVORITOS"), RECENT("RECIENTES");
        public final String label;
        Filter(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Item(Kind kind, String id, String title, String platform,
                       Path file, boolean favorite, long lastPlayed,
                       long seconds, DesktopLibrary.Game retro) {
        public Item {
            if (kind == null || id == null || title == null || platform == null
                    || file == null || seconds < 0 || lastPlayed < 0)
                throw new IllegalArgumentException("Invalid library item");
            if ((kind == Kind.RETRO) != (retro != null))
                throw new IllegalArgumentException("Retro reference mismatch");
        }
        public boolean available() {
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(file);
        }
        public boolean isPort() { return kind != Kind.RETRO; }
        public String subtitle() {
            return kind == Kind.RETRO
                    ? platform + " · " + DesktopPlayStats.duration(seconds) + " jugados"
                    : (kind == Kind.PC_PORT ? "PORT PC revisado" : "GitHub vinculado");
        }
        public String searchText() {
            return (title + " " + platform + " " + subtitle()).toLowerCase(Locale.ROOT);
        }
    }

    public record Summary(int retro, int ports, int favorites,
                          int recent, long trackedSeconds) {
        public int total() { return retro + ports; }
    }

    private DesktopUnifiedLibrary() { }

    public static List<Item> collect(DesktopLibrary library,
                                     DesktopPlayStats stats,
                                     DesktopPortsLibrary portLinks) {
        if (library == null || stats == null || portLinks == null)
            throw new IllegalArgumentException("Missing library");
        ArrayList<Item> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (DesktopLibrary.Game game : library.games()) {
            DesktopPlayStats.Snapshot play = stats.get(game.console, game.rom);
            String id = "retro:" + DesktopPlayStats.id(game.console, game.rom);
            if (ids.add(id))
                items.add(new Item(Kind.RETRO, id, game.title(),
                        game.console.label, game.rom,
                        game.favorite, Math.max(game.lastPlayed, play.lastSessionEpochMillis),
                        play.seconds, game));
        }
        for (DesktopPortsLibrary.LinkedPort port : portLinks.linkedEntries()) {
            String id = "pc:" + port.id();
            if (ids.add(id))
                items.add(new Item(port.reviewed() ? Kind.PC_PORT : Kind.GITHUB_PORT,
                        id, port.title(),
                        port.reviewed() ? "Windows · Port verificado" : "Windows · GitHub",
                        port.executable(), false, 0, 0, null));
        }
        items.sort(Comparator.comparing(Item::favorite).reversed()
                .thenComparing(Comparator.comparingLong(Item::lastPlayed).reversed())
                .thenComparing(Item::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(items);
    }

    public static Summary summary(List<Item> items) {
        int retro = 0, ports = 0, favorites = 0, recent = 0;
        long seconds = 0;
        for (Item item : items) {
            if (item.kind() == Kind.RETRO) {
                retro++;
                seconds = Long.MAX_VALUE - seconds < item.seconds()
                        ? Long.MAX_VALUE : seconds + item.seconds();
            } else ports++;
            if (item.favorite()) favorites++;
            if (item.lastPlayed() > 0) recent++;
        }
        return new Summary(retro, ports, favorites, recent, seconds);
    }

    public static List<Item> filter(List<Item> items, Filter filter, String search) {
        if (filter == null) throw new IllegalArgumentException("Filter");
        String query = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        ArrayList<Item> out = new ArrayList<>();
        for (Item item : items) {
            if (!query.isEmpty() && !item.searchText().contains(query)) continue;
            if (filter == Filter.RETRO && item.kind() != Kind.RETRO) continue;
            if (filter == Filter.PC && !item.isPort()) continue;
            if (filter == Filter.FAVORITES && !item.favorite()) continue;
            if (filter == Filter.RECENT && item.lastPlayed() <= 0) continue;
            out.add(item);
        }
        if (filter == Filter.RECENT) {
            out.sort(Comparator.comparingLong(Item::lastPlayed).reversed());
        }
        return List.copyOf(out);
    }
}
