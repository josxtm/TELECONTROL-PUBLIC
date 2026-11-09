import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/io/github/josxtm/mangossh"
TELE = ROOT / "app/src/main/java/es/josxtm/telecontrol/MainActivity.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

class MangoComposeParityTests(unittest.TestCase):
    def test_original_compose_navigation_and_themes_present(self):
        src = (SRC / "MainActivity.kt").read_text()
        for landmark in ("MangoNavigationRail", "DarkColors", "LightColors",
                         "Connections(vm)", "TerminalPage(vm", "DiagnosticsPage(vm",
                         "HistoryPage(vm", "SettingsPage(vm", "MangoApp("):
            self.assertIn(landmark, src)

    def test_original_terminal_and_ssh_core_present(self):
        self.assertIn("@Composable", (SRC / "ui/TerminalView.kt").read_text())
        self.assertIn("class SshEngine", (SRC / "core/SshEngine.kt").read_text())

    def test_only_compose_activity_launched_from_original_telecontrol(self):
        bridge = TELE.read_text()
        self.assertIn("io.github.josxtm.mangossh.MainActivity.class", bridge)
        self.assertNotIn("new Intent(MainActivity.this, io.github.josxtm.mangossh.community.MainActivity.class)", bridge)
        manifest = MANIFEST.read_text()
        self.assertIn('android:name="io.github.josxtm.mangossh.MainActivity"', manifest)
        self.assertIn('android:process=":mangossh"', manifest)

    def test_private_network_configuration_never_embedded(self):
        source = (SRC / "core/CtNetwork.kt").read_text()
        self.assertIn("throw IllegalStateException", source)
        self.assertNotIn("uci set", source)
        self.assertNotIn("eth" + "0.2", source)
        self.assertNotRegex(source, r"LAN\\s*\\+\\s*[0-9]+")
        self.assertNotRegex(source, r"(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])")

if __name__ == "__main__":
    unittest.main()
