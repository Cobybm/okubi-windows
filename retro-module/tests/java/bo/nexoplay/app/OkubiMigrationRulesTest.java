package bo.nexoplay.app;

/** Pure Java CI guard for the migration ZIP allowlist and safe preferences. */
public final class OkubiMigrationRulesTest {
    private static void yes(boolean x,String why){if(!x)throw new AssertionError(why);}
    private static void no(boolean x,String why){yes(!x,why);}
    public static void main(String[] args){
        String sha="0123456789abcdef".repeat(4);
        for(String system:new String[]{"gba","gameboy","snes"}){
            for(String ext:new String[]{"srm","rtc","state"}){
                String name="saves/"+system+"/"+sha+"."+ext;
                yes(OkubiMigrationRules.isSave(name),"safe name: "+name);
                yes(OkubiMigrationRules.maximumFor(name)>0,"sized name: "+name);
            }
        }
        for(String unsafe:new String[]{
                "../../files/okubi/gameboy/"+sha+".srm",
                "/data/data/bo.nexoplay.app.unified/files/"+sha+".srm",
                "saves/gba/"+sha+".gba",
                "saves/gameboy/"+sha+".zip",
                "saves/gba/"+sha.toUpperCase()+".state",
                "saves/../../gameboy/"+sha+".rtc",
                "manifest.json/../saves/gameboy/"+sha+".srm",
                "shared_prefs/nexo_play.xml","assets/personal-roms/rom.gb",
                "saves/nds/"+sha+".srm", "", "saves/gba/"+sha+".state/"}){
            no(OkubiMigrationRules.isAllowedEntry(unsafe),"reject unsafe: "+unsafe);
        }
        for(String system:new String[]{"gameboy","gba","snes"}) {
            for(int slot:new int[]{2,3}) {
                String path="saves/"+system+"/"+sha+".slot"+slot+".state";
                yes(OkubiMigrationRules.isSave(path),"allow extra save slot "+slot);
                yes(OkubiMigrationRules.maximumFor(path)==16L*1024*1024,
                        "state size bound "+slot);
            }
        }
        for(String invalid:new String[]{
                "saves/gba/"+sha+".slot0.state",
                "saves/gba/"+sha+".slot1.state",
                "saves/gba/"+sha+".slot4.state",
                "saves/gba/"+sha+".slot2.srm",
                "saves/gameboy/"+sha+".slot3.state/../anything",
                "saves/gba/../"+sha+".slot2.state"}) {
            no(OkubiMigrationRules.isAllowedEntry(invalid),"reject forged slot");
        }
        yes(OkubiMigrationRules.allowPreference("okubi_retro_slots_v1",
                "slot_"+sha),"allow selected slot for the game SHA");
        no(OkubiMigrationRules.allowPreference("okubi_retro_slots_v1",
                "slot_../../another"),"reject arbitrary preference name");
        yes(OkubiMigrationRules.isAllowedEntry("manifest.json"),"manifest");
        yes(OkubiMigrationRules.isAllowedEntry("preferences.json"),"prefs");
        yes(OkubiMigrationRules.maximumFor("saves/gba/"+sha+".state")
                ==16L*1024*1024,"state limit");
        yes(OkubiMigrationRules.maximumFor("saves/gba/"+sha+".rtc")==65536,
                "RTC bound");
        yes(OkubiMigrationRules.allowPreference("nexo_visual_08","palette"),"theme");
        yes(OkubiMigrationRules.allowPreference("MainActivity","library_order_v09"),"sort");
        yes(OkubiMigrationRules.allowPreference("bo.nexoplay.app.MainActivity",
                "library_order_v09"),"QA app fully qualified activity prefs");
        yes(OkubiMigrationRules.allowPreference("bo.nexoplay.app.MainActivity",
                "cinema_lite_v07"),"QA app lite mode");
        yes(OkubiMigrationRules.allowPreference("okubi_retro_controls_v1",
                "port_x_UP"),"touch coordinate");
        yes(OkubiMigrationRules.allowPreference("okubi_retro_controls_v1",
                "land_dpadX"),"landscape D-pad");
        yes(OkubiMigrationRules.allowPreference("okubi_retro_controls_v1",
                "cinema_land_alpha"),"cinema controller transparency");
        yes(OkubiMigrationRules.allowPreference("okubi_retro_controls_v1",
                "snes_cinema_land_x_A"),"SNES cinema button position");
        yes(OkubiMigrationRules.allowPreference("okubi_retro_controls_v1",
                "snes_land_haptics"),"SNES haptic preference");
        for (String console:new String[]{"GB","GBC","GBA","SNES"}) {
            yes(OkubiMigrationRules.allowPreference("okubi_visual_v1",console),
                    "visual filter migrated for "+console);
            yes(OkubiMigrationRules.allowPreference("okubi_screen_fit_v1",console),
                    "full-screen mode migrated for "+console);
            yes(OkubiMigrationRules.allowPreference("okubi_overlay_opacity_v1",console),
                    "overlay alpha migrated for "+console);
        }
        no(OkubiMigrationRules.allowPreference("okubi_visual_v1",
                "../GB"),"no path-like preference keys");
        no(OkubiMigrationRules.allowPreference("okubi_screen_fit_v1",
                "WII"),"no arbitrary console preference");
        no(OkubiMigrationRules.allowPreference("okubi_retro_controls_v1",
                "snes_cinema_land_debugRoot"),"no arbitrary controller keys");
        yes(OkubiMigrationRules.allowPreference("nexo_play","collections_v05"),
                "game collections");
        yes(OkubiMigrationRules.allowPreference("nexo_play",
                "personal_goals_v10_com.game/MainActivity"),"preserve Android component game goals");
        yes(OkubiMigrationRules.allowPreference("nexo_play",
                "launch_ms_com.game/MainActivity"),"preserve last played timestamp");
        yes(OkubiMigrationRules.allowPreference("nexo_play",
                "personal_goals_v10_com.game.MainActivity"),"safe goals key");
        no(OkubiMigrationRules.allowPreference("okubi_pokemon_repository",
                "game_files_v1"),"no stale SAF metadata migration");
        no(OkubiMigrationRules.allowPreference("nexo_play",
                "cover_v05_content://old"),"no old URI grants");
        no(OkubiMigrationRules.allowPreference("nexo_play",
                "../data/data/anything"),"reject other files");
        System.out.println("OkubiMigrationRulesTest: OK");
    }
}
