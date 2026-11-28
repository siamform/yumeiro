"""B10: health check and security vetting of CloudStream extension files (.cs3) for the Extension Market.

Used by build_extension_market.py on every run (every listed file is downloaded and checked; unchanged files reuse
the previous result for a few days) and by check_extension_health.py, which reports on a published index.json.

A file is REMOVED from the market when it is
  dead      - the download fails (HTTP error, timeout, empty) or the author marks it status 0 (down),
  broken    - not a zip, no classes.dex / manifest.json / pluginClassName, or its fileHash does not match,
  unsafe    - it carries code the app can't see before running it: native libraries (.so), nested apk/jar/dex
              payloads, or Android/Java APIs that load or run more code at run time (class loaders, processes),
  hostile   - it asks for things a video/manga source never needs (SMS, contacts, accounts, device admin).
Criteria and the process: .github/extensions/CURATION.md. Only metadata is read; nothing is executed.
"""
import hashlib, io, json, re, zipfile

# Dex type descriptors that mean "loads or runs code the market can't inspect".
UNSAFE_TYPES = {
    b"Ldalvik/system/DexClassLoader;": "loads more code at run time (DexClassLoader)",
    b"Ldalvik/system/InMemoryDexClassLoader;": "loads more code at run time (InMemoryDexClassLoader)",
    b"Ldalvik/system/PathClassLoader;": "loads more code at run time (PathClassLoader)",
    b"Ldalvik/system/BaseDexClassLoader;": "loads more code at run time (BaseDexClassLoader)",
    b"Ljava/lang/ProcessBuilder;": "starts processes (ProcessBuilder)",
}
# APIs a streaming/reading source has no reason to touch.
HOSTILE_TYPES = {
    b"Landroid/telephony/SmsManager;": "sends SMS",
    b"Landroid/provider/ContactsContract;": "reads contacts",
    b"Landroid/accounts/AccountManager;": "reads device accounts",
    b"Landroid/app/admin/DevicePolicyManager;": "uses device admin",
    b"Landroid/provider/Telephony;": "reads messages",
}
PAYLOAD = re.compile(r"(?i)(\.so|\.apk|\.jar|\.dex)$")
CLASSES_DEX = re.compile(r"^classes\d*\.dex$")


class Rejected(Exception):
    def __init__(self, kind, reason):
        super().__init__("%s: %s" % (kind, reason))
        self.kind, self.reason = kind, reason


def vet(data, expected_sha=None):
    """Checks one downloaded .cs3. Returns {sha256, size, pluginClassName, dex} or raises Rejected."""
    if not data:
        raise Rejected("dead", "empty download")
    sha = hashlib.sha256(data).hexdigest()
    if expected_sha and expected_sha.lower() != sha:
        raise Rejected("broken", "fileHash does not match the file")
    try:
        z = zipfile.ZipFile(io.BytesIO(data))
        names = z.namelist()
    except Exception:
        raise Rejected("broken", "not a zip file")
    with z:
        dexes = [n for n in names if CLASSES_DEX.match(n)]
        if not dexes:
            raise Rejected("broken", "no classes.dex")
        if "manifest.json" not in names:
            raise Rejected("broken", "no manifest.json")
        try:
            m = json.loads(z.read("manifest.json").decode("utf-8", "replace"))
        except Exception:
            raise Rejected("broken", "manifest.json is not JSON")
        if not isinstance(m, dict) or not str(m.get("pluginClassName") or "").strip():
            raise Rejected("broken", "manifest has no pluginClassName")
        extra = [n for n in names if PAYLOAD.search(n) and not CLASSES_DEX.match(n)]
        if extra:
            raise Rejected("unsafe", "carries extra code: " + ", ".join(sorted(extra)[:3]))
        total = 0
        for n in dexes:
            info = z.getinfo(n)
            total += info.file_size
            if info.file_size > 40 << 20:
                raise Rejected("broken", "dex larger than 40 MB")
            body = z.read(n)
            if not body.startswith(b"dex\n"):
                raise Rejected("broken", "%s is not a dex file" % n)
            for t, why in UNSAFE_TYPES.items():
                if t in body:
                    raise Rejected("unsafe", why)
            for t, why in HOSTILE_TYPES.items():
                if t in body:
                    raise Rejected("hostile", why)
    return {"sha256": sha, "size": len(data), "pluginClassName": str(m["pluginClassName"]).strip(), "dex": total}
