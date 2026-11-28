"""B10: unit tests for the Extension Market health check and security vetting (extension_vetting.vet)."""
import hashlib, io, json, os, sys, unittest, zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extension_vetting import Rejected, vet  # noqa: E402

DEX = b"dex\n035\x00" + b"\x00" * 64 + b"Lcom/example/MyProvider;\x00Lcom/lagradost/cloudstream3/MainAPI;\x00"


def cs3(files=None, manifest=None, dex=DEX):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        if dex is not None:
            z.writestr("classes.dex", dex)
        if manifest is not False:
            z.writestr("manifest.json", json.dumps(manifest or {"name": "My", "pluginClassName": "com.example.MyPlugin", "version": 3}))
        for n, b in (files or {}).items():
            z.writestr(n, b)
    return buf.getvalue()


class VetTest(unittest.TestCase):
    def kind(self, data, sha=None):
        with self.assertRaises(Rejected) as c:
            vet(data, sha)
        return c.exception.kind, c.exception.reason

    def test_a_clean_plugin_passes_and_reports_its_class(self):
        data = cs3()
        v = vet(data, hashlib.sha256(data).hexdigest())
        self.assertEqual("com.example.MyPlugin", v["pluginClassName"])
        self.assertEqual(len(data), v["size"])
        # Second dex files are normal (multidex), not extra payloads.
        vet(cs3(files={"classes2.dex": DEX}))

    def test_broken_files_are_removed(self):
        self.assertEqual("dead", self.kind(b"")[0])
        self.assertEqual(("broken", "not a zip file"), self.kind(b"<html>Codeberg is not a CDN.</html>"))
        self.assertEqual(("broken", "no classes.dex"), self.kind(cs3(dex=None)))
        self.assertEqual(("broken", "no manifest.json"), self.kind(cs3(manifest=False)))
        self.assertEqual(("broken", "manifest has no pluginClassName"), self.kind(cs3(manifest={"name": "x"})))
        self.assertEqual(("broken", "classes.dex is not a dex file"), self.kind(cs3(dex=b"PK not dex")))
        data = cs3()
        self.assertEqual(("broken", "fileHash does not match the file"), self.kind(data, "0" * 64))

    def test_code_the_market_cannot_see_is_unsafe(self):
        k, r = self.kind(cs3(files={"lib/arm64-v8a/libx.so": b"\x7fELF"}))
        self.assertEqual("unsafe", k); self.assertIn("lib/arm64-v8a/libx.so", r)
        self.assertEqual("unsafe", self.kind(cs3(files={"assets/more.apk": b"PK"}))[0])
        self.assertEqual("unsafe", self.kind(cs3(files={"payload.jar": b"PK"}))[0])
        self.assertEqual("unsafe", self.kind(cs3(dex=DEX + b"Ldalvik/system/DexClassLoader;\x00"))[0])
        self.assertEqual("unsafe", self.kind(cs3(dex=DEX + b"Ldalvik/system/BaseDexClassLoader;\x00"))[0])
        self.assertEqual("unsafe", self.kind(cs3(dex=DEX + b"Ljava/lang/ProcessBuilder;\x00"))[0])

    def test_apis_a_source_never_needs_are_hostile(self):
        self.assertEqual(("hostile", "sends SMS"), self.kind(cs3(dex=DEX + b"Landroid/telephony/SmsManager;\x00")))
        self.assertEqual(("hostile", "reads device accounts"), self.kind(cs3(dex=DEX + b"Landroid/accounts/AccountManager;\x00")))


if __name__ == "__main__":
    unittest.main()
