package bo.nexoplay.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** JVM tests for the non-destructive, cross-platform home spotlight policy. */
public final class OkubiLibrarySpotlightTest {
    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void eq(String actual,String expected,String message) {
        check((actual==null && expected==null)
                || (actual!=null && actual.equals(expected)), message+
                " actual="+actual+" expected="+expected);
    }
    private static OkubiLibrarySpotlight.Candidate item(
            String id,String title,long when,boolean favorite) {
        return new OkubiLibrarySpotlight.Candidate(id,title,when,favorite);
    }
    public static void main(String[] args) {
        OkubiLibrarySpotlight.Candidate android =
                item("app:android","Minecraft",100L,true);
        OkubiLibrarySpotlight.Candidate gba =
                item("retro:uri:gba","Pokémon Esmeralda",300L,false);
        OkubiLibrarySpotlight.Candidate gb =
                item("retro:uri:gb","Pokémon Rojo",200L,true);
        List<OkubiLibrarySpotlight.Candidate> games = new ArrayList<>(
                Arrays.asList(android,gba,gb));
        eq(OkubiLibrarySpotlight.select(null,games),gba.id,
                "most recent retro outranks older Android, no platform bias");
        eq(OkubiLibrarySpotlight.select("",games),gba.id,
                "empty pin means automatic");
        eq(OkubiLibrarySpotlight.select(android.id,games),android.id,
                "owner can pin Android even if retro more recent");
        eq(OkubiLibrarySpotlight.select(gb.id,games),gb.id,
                "owner can pin retro even if Android exists");
        eq(OkubiLibrarySpotlight.select("uninstalled",games),gba.id,
                "stale pin must fall back gracefully");
        check(games.get(0)==android && games.get(1)==gba && games.get(2)==gb,
                "selection never reorders the real library");
        eq(OkubiLibrarySpotlight.select(null,null),null,"null collection");
        eq(OkubiLibrarySpotlight.select(null,new ArrayList<>()),null,"empty collection");
        eq(OkubiLibrarySpotlight.select(null,Arrays.asList(null,
                item(null,"Invalid",999,false),
                item("","Empty",999,false),gba)),gba.id,
                "invalid ids never chosen or matched");
        OkubiLibrarySpotlight.Candidate fav=item("a","A",0,true);
        OkubiLibrarySpotlight.Candidate noFav=item("b","B",0,false);
        eq(OkubiLibrarySpotlight.select(null,Arrays.asList(noFav,fav)),fav.id,
                "favorite breaks never-played ties");
        OkubiLibrarySpotlight.Candidate first=item("a","Zelda",0,false);
        OkubiLibrarySpotlight.Candidate second=item("b","Alpha",0,false);
        eq(OkubiLibrarySpotlight.select(null,Arrays.asList(first,second)),second.id,
                "alphabetical fallback deterministic");
        eq(OkubiLibrarySpotlight.select(null,Arrays.asList(
                item("zz","Same",0,false),item("aa","Same",0,false))),"aa",
                "stable ID tie break");
        check(OkubiLibrarySpotlight.compare(gba,android)<0,
                "recent retro ranked ahead of Android");
        check(OkubiLibrarySpotlight.compare(
                item("n","Never", -100,false),item("m","New",0,false))<0,
                "negative timestamps bounded to zero");
        System.out.println("OkubiLibrarySpotlightTest: OK");
    }
}
