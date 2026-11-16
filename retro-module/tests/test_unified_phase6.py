"""Phase 6: non-destructive OKUBI UNIFIED migration/source-tool QA contracts.

Run within retro-module/tests without Android. ZIP construction and Android
file permission limitations are documented and independently checked in CI.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/"android-app/app/src/main/java/bo/nexoplay/app"
SCRIPT=ROOT/"tools/okubi_rescue_adb.py"
spec=importlib.util.spec_from_file_location("okubi_rescue_adb",SCRIPT)
rescue=importlib.util.module_from_spec(spec)
spec.loader.exec_module(rescue)
MAIN=(APP/"MainActivity.java").read_text(encoding="utf-8")
BACKUP=(APP/"OkubiUnifiedBackup.java").read_text(encoding="utf-8")
RULES=(APP/"OkubiMigrationRules.java").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")


class Phase6MigrationContracts(unittest.TestCase):
    def test_adb_preference_xml_parser_rejects_uri_but_keeps_settings(self):
        xml=b'''<?xml version="1.0" encoding="utf-8"?>
        <map>
         <string name="player_nickname_v05">JUGADOR</string>
         <string name="cover_v05_app">content://old/private.jpg</string>
         <set name="favorite_components"><string>com.game/.Activity</string></set>
         <string name="collections_v05">[]</string>
         <boolean name="untrusted_setting" value="true"/>
         <long name="launch_ms_com.game/.Activity" value="123456789"/>
        </map>'''
        data=rescue.parse_pref_xml(xml,"nexo_play")
        self.assertEqual("JUGADOR",data["player_nickname_v05"]["value"])
        self.assertEqual(["com.game/.Activity"],data["favorite_components"]["value"])
        self.assertEqual("[]",data["collections_v05"]["value"])
        self.assertEqual(123456789,data["launch_ms_com.game/.Activity"]["value"])
        self.assertNotIn("cover_v05_app",data)
        self.assertNotIn("untrusted_setting",data)

    def test_adb_controls_portrait_landscape_and_stale_uri_rejection(self):
        xml=b'''<map>
        <float name="port_x_A" value="0.24"/>
        <float name="land_dpadX" value="0.3"/>
        <boolean name="port_left" value="true"/>
        <string name="port_skin">CLASSIC_GBA</string>
        <string name="secret_token">hidden</string>
        </map>'''
        data=rescue.parse_pref_xml(xml,"okubi_retro_controls_v1")
        self.assertEqual(4,len(data))
        self.assertTrue(data["port_left"]["value"])
        self.assertEqual("CLASSIC_GBA",data["port_skin"]["value"])
        self.assertAlmostEqual(0.3,data["land_dpadX"]["value"])
        self.assertEqual({},rescue.parse_pref_xml(b'<malformed',"nexo_play"))
        self.assertFalse(rescue.allowed_pref("okubi_pokemon_repository","game_files_v1"))

    def test_adb_archive_matches_java_manifest_and_never_contains_rom(self):
        sha="ab" * 32
        content=b"sav-data"*10
        path=f"saves/gameboy/{sha}.srm"
        with TemporaryDirectory() as tmp:
            output=Path(tmp)/"rescate.zip"
            n,p=rescue.build_archive("bo.nexoplay.app.dpadtest",
                {"nexo_play":{"player_nickname_v05":{
                    "type":"string","value":"OKUBI"}}},
                {path:content},output)
            self.assertEqual((1,1),(n,p))
            with ZipFile(output) as z:
                self.assertEqual({"manifest.json","preferences.json",path},set(z.namelist()))
                self.assertIsNone(z.testzip())
                manifest=json.loads(z.read("manifest.json"))
                self.assertEqual("okubi-unified",manifest["format"])
                self.assertEqual(1,manifest["version"])
                self.assertEqual("bo.nexoplay.app.dpadtest",manifest["source"])
                records={r["path"]:r for r in manifest["files"]}
                self.assertEqual(hashlib.sha256(content).hexdigest(),
                                 records[path]["sha256"])
                self.assertEqual(len(content),records[path]["size"])
                self.assertNotIn("game.gb",",".join(z.namelist()))

    def test_adb_rescue_zip_never_overwrites_a_previous_snapshot(self):
        sha = "c3" * 32
        path = f"saves/gba/{sha}.slot2.state"
        with TemporaryDirectory() as temp:
            folder = Path(temp)
            first = folder / "OKUBI_Rescate_personal.zip"
            source = b"CHECKPOINT_SAVE_CONTENT"
            rescue.build_archive("bo.nexoplay.app.personal", {},
                                 {path: source}, first)
            pristine = first.read_bytes()
            with self.assertRaises(FileExistsError):
                rescue.build_archive("bo.nexoplay.app.personal", {},
                                     {path: b"NEW_SAVE"}, first)
            self.assertEqual(pristine, first.read_bytes())
            second = rescue.next_free_backup(folder, "OKUBI_Rescate_personal")
            self.assertEqual(folder / "OKUBI_Rescate_personal_2.zip", second)
            rescue.build_archive("bo.nexoplay.app.personal", {},
                                 {path: b"NEW_SAVE"}, second)
            self.assertEqual(pristine, first.read_bytes())
            with ZipFile(second) as archive:
                self.assertEqual(b"NEW_SAVE", archive.read(path))

    def test_adb_rescue_requires_two_identical_reads(self):
        path = "files/okubi/gba/" + "de" * 32 + ".state"
        with patch.object(rescue, "read_old", side_effect=[b"partida", b"partida"]) as read:
            self.assertEqual(b"partida",
                             rescue.read_stable_save("adb", "test", path, 20))
            self.assertEqual(2, read.call_count)
        for data in ([b"partida", b"changed"],
                     [b"partida", b"partid"],
                     [b"partida", None],
                     [b"", b""],
                     [b"too-large", b"too-large"]):
            with patch.object(rescue, "read_old", side_effect=data) as read:
                self.assertIsNone(
                    rescue.read_stable_save("adb", "test", path, 7)
                )
                self.assertGreaterEqual(read.call_count, 1)

    def test_adb_script_has_no_mutating_phone_actions(self):
        contents = SCRIPT.read_text(encoding="utf-8")
        self.assertIn('destination.open("xb")', contents)
        self.assertIn('next_free_backup(target_dir,', contents)
        self.assertIn('read_stable_save(', contents)
        self.assertIn('second != first', contents)
        for blocked in ("pm uninstall", "adb install", "adb root",
                        "pm clear", "adb push", "rm -rf"):
            self.assertNotIn(blocked, contents)

    def test_old_versions_remain_untouched_and_canonical_id_is_fixed(self):
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("OKUBI 遊",GRADLE)
        self.assertNotIn("OKUBI_PERSONAL_BUILD",GRADLE)
        self.assertNotIn("OKUBI_INDIE_TEST_BUILD",GRADLE)
        self.assertNotIn("OKUBI_TOUCH_TEST_BUILD",GRADLE)
        self.assertIn("bo.nexoplay.app.unified",WORKFLOW)
        self.assertIn("apksigner",WORKFLOW)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK",WORKFLOW)

    def test_safe_non_destructive_archive_import(self):
        for anchor in (
            "OkubiMigrationRules.isAllowedEntry(name)",
            "staged.containsKey(name)",
            "OkubiMigrationRules.MAX_TOTAL",
            "records.length()!=staged.size()-1",
            "hashes.get(name).equals(record.optString(\"sha256\"))",
            "if(target.exists()){skipped++;continue;}",
            "if(prefs.contains(key)) {",
            "if(!additiveSet(file,key) || !(value instanceof Set))continue;",
            "merged.addAll(incoming)",
            "before.put(file,rollback)",
            "for(File created:committed)",
            "new AtomicFile(created).delete();",
            "OkubiUnifiedBackup.importFrom(this,in)",
        ):
            self.assertIn(anchor,BACKUP if "importFrom(this,in)" not in anchor else MAIN)
        for anchor in ("public static Preview inspect(Context context,InputStream stream)",
                       "Verified verified=validateArchive(stream,staging)",
                       "pendingPreferences(context,verified.settings)",
                       "return new Preview(verified.source,newly,existing,",
                       "private static Verified validateArchive(",
                       "public final int newSaves, existingSaves, settings"):
            self.assertIn(anchor,BACKUP)
        self.assertIn("saves/(gameboy|gba|snes)",RULES)
        self.assertIn("allowPreference",RULES)
        self.assertNotIn("android.permission.MANAGE_EXTERNAL_STORAGE",
                         (ROOT/"android-app/app/src/main/AndroidManifest.xml")
                         .read_text(encoding="utf-8"))

    def test_migration_entrypoint_in_ui(self):
        for marker in ("REQUEST_UNIFIED_EXPORT","REQUEST_UNIFIED_IMPORT",
                       "chooseUnifiedExport()","chooseUnifiedImport()",
                       "previewUnified(selected)","transferUnified(uri,true)",
                       "transferUnified(selected,false)",
                       "Respaldo y migración","Importar nunca reemplaza partidas"):
            self.assertIn(marker,MAIN)
        self.assertIn("Verificación aprobada",MAIN)
        self.assertIn("Respaldo rechazado",MAIN)
        self.assertIn("OkubiUnifiedBackup.inspect(this,source)",MAIN)
        self.assertIn("RetroDpadHitMapTest",WORKFLOW)
        self.assertIn("OkubiMigrationRulesTest",WORKFLOW)
        self.assertIn("RetroRomReaderTest",WORKFLOW)

    def test_old_okubi_apks_do_not_appear_as_new_games(self):
        catalog=(APP/"GameCatalog.java").read_text(encoding="utf-8")
        self.assertIn("if (isOkubiPackage(pkg)) continue;",catalog)
        for package in ("bo.nexoplay.app", "bo.nexoplay.app.personal",
                        "bo.nexoplay.app.dpadtest", "bo.nexoplay.app.indietest",
                        "bo.nexoplay.app.unified"):
            self.assertIn('"' + package + '"',catalog)
        self.assertNotIn("pm uninstall",catalog)

    def test_preview_only_validates_before_user_confirms(self):
        self.assertIn("private static Verified validateArchive",BACKUP)
        self.assertIn("public static Preview inspect(Context context,InputStream stream)",BACKUP)
        preview=BACKUP.split("public static Preview inspect(")[1].split(
            "public static Outcome importFrom(")[0]
        self.assertNotIn("applyPreferences(",preview)
        self.assertNotIn("AtomicFile(",preview)
        self.assertIn("verified=validateArchive(stream,staging)",preview)
        self.assertIn("pendingPreferences(context,verified.settings)",preview)
        self.assertIn("clean(staging)",preview)
        self.assertIn("previewUnified(selected);",MAIN)
        self.assertIn("transferUnified(uri,true)",MAIN)
        self.assertIn("OkubiUnifiedBackup.inspect(this,source)",MAIN)

    def test_activity_preferences_keep_filters_after_package_id_change(self):
        # getPreferences() may use MainActivity.xml for the old app but the
        # fully qualified class name for .personal/.dpadtest/.indietest.
        xml=b'<map><string name="library_order_v09">recent</string><boolean name="cinema_lite_v07" value="true"/></map>'
        for source in ("MainActivity","bo.nexoplay.app.MainActivity"):
            rows=rescue.parse_pref_xml(xml,source)
            self.assertEqual("recent",rows["library_order_v09"]["value"])
            self.assertTrue(rows["cinema_lite_v07"]["value"])
        self.assertIn('"bo.nexoplay.app.MainActivity","MainActivity"',BACKUP)
        self.assertIn('effectivePreferenceFile(String file)',BACKUP)
        self.assertIn('String targetFile=effectivePreferenceFile(file);',BACKUP)
        self.assertIn('previous.putIfAbsent(item.getKey(),item.getValue());',BACKUP)
        self.assertIn('"bo.nexoplay.app.MainActivity"',RULES)

    def test_adb_script_non_destructive_and_no_root(self):
        text=SCRIPT.read_text(encoding="utf-8")
        for x in ("exec-out", '"run-as"', '"cat"',"shared_prefs/",
                  "files/okubi/", "CANDIDATE_PACKAGES", "build_archive("):
            self.assertIn(x,text)
        self.assertNotIn("adb root",text)
        self.assertNotIn("pm uninstall",text)
        self.assertNotIn("rm -rf",text)
        self.assertNotIn("adb install",text)
        self.assertFalse(rescue.allowed_pref("nexo_play","cover_v05_content://"))
        self.assertTrue(rescue.allowed_pref("MainActivity","cinema_lite_v07"))


if __name__=="__main__":
    unittest.main()
