# Narrow, reviewed CodeQL suppressions (rule + exact path). Everything else is reported as-is.
# Each entry is justified in docs/security/AUDIT.md ("CodeQL suppressions").
#  1. Weak-crypto rules on interop code that must match third-party formats bit-for-bit:
#     - sources/js/JsCrypto.kt: CryptoJS AES-CBC + EVP_BytesToKey(MD5) used by websites.
#     - eu/kanade/**: vendored Tachiyomi/Aniyomi API; generateId() = MD5 must match upstream ids.
#  2. Certificate pinning on hosts Yumeiro does not control (Kitsu/Trakt APIs rotate certs;
#     Plex is user-hosted). A stale pin would silently break sign-in, so system CAs only.
def suppressed:
  (.ruleId // .rule.id) as $r
  | (.locations[0].physicalLocation.artifactLocation.uri // "") as $u
  | ((($r == "java/weak-cryptographic-algorithm") or ($r == "java/potentially-weak-cryptographic-algorithm"))
      and ($u | test("^app/src/main/java/(eu/kanade/|dev/asfakul/yumeiro/sources/js/JsCrypto\\.kt$)")))
    or (($r == "java/android/missing-certificate-pinning")
      and ($u | test("^app/src/main/java/dev/asfakul/yumeiro/(tracking/(Kitsu|Trakt)|sources/servers/PlexProvider)\\.kt$")));
.runs |= map(.results |= map(select(suppressed | not)))
