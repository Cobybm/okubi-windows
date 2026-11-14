package bo.nexoplay.app;

import java.util.List;

/**
 * Deterministic, read-only selection of the OKUBI home spotlight across both
 * Android games and personal GB/GBC/GBA cartridges.
 *
 * Does not touch app preferences, ROM contents, timestamps or save files.
 */
public final class OkubiLibrarySpotlight {
    private OkubiLibrarySpotlight() {}

    public static final class Candidate {
        public final String id, title;
        public final long lastPlayed;
        public final boolean favorite;

        public Candidate(String id, String title, long lastPlayed, boolean favorite) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.lastPlayed = Math.max(0, lastPlayed);
            this.favorite = favorite;
        }
    }

    /** Highest rank first: played recently, then favorites, then stable name/id. */
    public static int compare(Candidate left, Candidate right) {
        int played = Long.compare(right.lastPlayed, left.lastPlayed);
        if (played != 0) return played;
        if (left.favorite != right.favorite) return left.favorite ? -1 : 1;
        int title = left.title.compareToIgnoreCase(right.title);
        if (title != 0) return title;
        return left.id.compareTo(right.id);
    }

    /**
     * An existing pinned ID takes priority. If it isn't in the current
     * library (e.g. package uninstalled), use the best existing entry without
     * erasing the pin; it becomes active again if the game returns.
     */
    public static String select(String pinnedId, List<Candidate> entries) {
        if (entries == null || entries.isEmpty()) return null;
        Candidate best = null;
        for (Candidate candidate : entries) {
            if (candidate == null || candidate.id == null
                    || candidate.id.isEmpty()) continue;
            if (pinnedId != null && pinnedId.equals(candidate.id))
                return candidate.id;
            if (best == null || compare(candidate, best) < 0)
                best = candidate;
        }
        return best == null ? null : best.id;
    }
}
