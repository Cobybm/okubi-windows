package bo.nexoplay.app.retro;

/** Real javac/java tests of all Phase 21 external-only console formats. */
public final class RetroExpansionFormatsTest {
    private static void check(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }
    private static void eq(String actual, String expected) {
        if (actual == null ? expected != null : !actual.equals(expected))
            throw new AssertionError("expected "+expected+", actual "+actual);
    }
    public static void main(String[] args) {
        eq(RetroExpansionFormats.systemFromName("Super Mario.sfc"), "SNES");
        eq(RetroExpansionFormats.systemFromName("SNES GAME.SMC"), "SNES");
        eq(RetroExpansionFormats.systemFromName("mario.NES"), "NES");
        eq(RetroExpansionFormats.systemFromName("game.unf"), "NES");
        eq(RetroExpansionFormats.systemFromName("sonic.MD"), "MD");
        eq(RetroExpansionFormats.systemFromName("sonic.gen"), "MD");
        eq(RetroExpansionFormats.systemFromName("game.sms"), "SMS");
        eq(RetroExpansionFormats.systemFromName("game.GG"), "GG");
        eq(RetroExpansionFormats.systemFromName("bonk.pce"), "PCE");
        eq(RetroExpansionFormats.systemFromName("adventure.a26"), "A2600");
        for(String bad : new String[] {"", "game.sfc.zip", "game.smc.exe",
                "ambiguous.bin", "game.gba", "game.gb", "game.gbc",
                "game.iso", "game.7z", "game.n64", "game.nes.old",
                "rom.sfc" + ((char)0) + "evil"}) {
            eq(RetroExpansionFormats.systemFromName(bad),null);
        }
        eq(RetroExpansionFormats.systemFromName(null),null);
        String[] systems={"SNES","NES","MD","SMS","GG","PCE","A2600"};
        for(String system : systems) {
            check(RetroExpansionFormats.isExternalClassic(system),
                    "classic console "+system);
            check(RetroExpansionFormats.validKnownSize(system,-1),
                    "unknown file length from SAF may be accepted");
            check(!RetroExpansionFormats.validKnownSize(system,0),
                    "reject empty file");
            check(!RetroExpansionFormats.validKnownSize(system,Long.MAX_VALUE),
                    "reject oversized file");
            check(RetroExpansionFormats.officialEmulatorPage(system).startsWith("https://"),
                    "official webpage must be HTTPS");
            check(RetroExpansionFormats.externalStatus(system).contains("externo"),
                    "no false internal core promise");
        }
        check(!RetroExpansionFormats.isExternalClassic("GB"),"SameBoy remains internal");
        check(!RetroExpansionFormats.isExternalClassic("GBA"),"mGBA remains internal");
        check(!RetroExpansionFormats.isExternalClassic("PSP"),"no unrelated launcher claim");
        check(!RetroExpansionFormats.validKnownSize("GBA",1),"not own policy");
        eq(RetroExpansionFormats.officialEmulatorPage("GBA"),null);
        check(RetroExpansionFormats.validKnownSize("SNES",32768),"small valid SNES");
        check(RetroExpansionFormats.validKnownSize("SNES",16L*1024*1024),"SNES maximum");
        check(!RetroExpansionFormats.validKnownSize("SNES",32767),"SNES too short");
        check(!RetroExpansionFormats.validKnownSize("SNES",16L*1024*1024+1),"SNES too long");
        check(RetroExpansionFormats.validKnownSize("NES",16384),"NES minimum");
        check(!RetroExpansionFormats.validKnownSize("NES",8192),"NES size fail");
        check(RetroExpansionFormats.officialEmulatorPage("SNES").contains("Snes9xPlus"),
                "verified SNES listing");
        check(RetroExpansionFormats.officialEmulatorPage("NES").contains("NesEmu"),
                "verified NES listing");
        check(RetroSystems.byId("SNES").matchesFile("zelda.SFC"),
                "system picker and format recognition match SNES");
        check(RetroSystems.byId("NES").matchesFile("mario.UNF"),
                "system picker and format recognition match NES");
        check(RetroSystems.byId("GBA").matchesFile("pokemon.gba"),
                "existing internal GBA format unchanged");
        check(RetroSystems.byId("GB").matchesFile("rom.gb"),
                "existing internal GB format unchanged");
        check(RetroSystems.byPosition(0).id.equals("GB"),
                "position zero unchanged for old preferences");
        check(RetroSystems.byPosition(2).id.equals("GBA"),
                "position two remains GBA");
        System.out.println("RetroExpansionFormatsTest: OK");
    }
}
