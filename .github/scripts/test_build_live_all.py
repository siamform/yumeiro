"""E2-ALL unit tests (Yumeiro Live): python3 -m unittest discover -s .github/scripts -p 'test_*.py'"""
import json, os, sys, tempfile, unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_live_all as la  # noqa: E402


def api():
    ch = lambda i, cc="BD", name=None, **k: {"id": i, "name": name or i.split(".")[0], "country": cc, "categories": ["news"], **k}
    st = lambda c, url, **k: {"channel": c, "url": url, "labels": [], "quality": "720p", **k}
    return {
        "channels": [ch("A.bd"), ch("B.in", "IN"), ch("Nsfw.bd", is_nsfw=True), ch("Closed.bd", closed="2024"),
                     ch("Dmca.bd"), ch("Hot.bd", name="XXX Hot"), ch("A2.bd", name="A"), ch("Ua.bd"), ch("Panel.bd")],
        "blocklist": [{"channel": "Dmca.bd", "reason": "dmca"}],
        "logos": [],
        "countries": [{"code": "BD", "name": "Bangladesh"}, {"code": "IN", "name": "India"}],
        "streams": [st("A.bd", "http://a/1.m3u8"), st("A.bd", "https://a/2.m3u8"), st("A.bd", "https://a/3.m3u8"),
                    st("B.in", "https://b.m3u8"), st("Nsfw.bd", "https://n.m3u8"), st("Closed.bd", "https://c.m3u8"),
                    st("Dmca.bd", "https://d.m3u8"), st("Hot.bd", "https://h.m3u8"), st("A2.bd", "https://a2.m3u8"),
                    st("Ua.bd", "https://u.m3u8", referrer="https://x"), st("Panel.bd", "http://1.2.3.4:8080/live/user/pass/123.m3u8")],
    }


class BuildLiveAllTest(unittest.TestCase):
    def test_merge_drops_nsfw_adult_closed_blocklist_headers_panels_and_duplicates(self):
        got = la.merged(api())
        self.assertEqual(set(got), {"A.bd", "B.in"})
        self.assertEqual([s["url"] for s in got["A.bd"]], ["https://a/2.m3u8", "https://a/3.m3u8"])  # best 2, https first

    def test_three_strikes_remove_a_stream_and_one_pass_brings_it_back(self):
        st = {}
        st = la.update_state(st, {"u": True}, "t0")
        self.assertTrue(la.shippable(st["u"]))
        for i in range(1, 3):
            st = la.update_state(st, {"u": False}, f"t{i}")
            self.assertTrue(la.shippable(st["u"]), f"strike {i} keeps it")
        st = la.update_state(st, {"u": False}, "t3")
        self.assertFalse(la.shippable(st["u"]))
        st = la.update_state(st, {"u": True}, "t4")
        self.assertTrue(la.shippable(st["u"]))

    def test_a_stream_that_never_passed_is_not_shipped(self):
        st = la.update_state({}, {"u": False}, "t0")
        self.assertFalse(la.shippable(st["u"]))

    def test_pick_falls_back_to_the_next_stream_of_the_channel(self):
        cands = la.merged(api())
        st = la.update_state({}, {"https://a/2.m3u8": False, "https://a/3.m3u8": True, "https://b.m3u8": True}, "t")
        self.assertEqual(sorted(s["url"] for s in la.pick(cands, st)), ["https://a/3.m3u8", "https://b.m3u8"])

    def test_m3u_groups_by_country_name(self):
        a = api()
        body = la.m3u([{"channel": "B.in", "url": "https://b.m3u8"}, {"channel": "A.bd", "url": "https://a/2.m3u8"}], a)
        lines = body.splitlines()
        self.assertEqual(lines[0], "#EXTM3U")
        self.assertIn('group-title="Bangladesh"', lines[1])
        self.assertIn('group-title="India"', lines[3])

    def test_main_writes_playlist_state_and_index_entry(self):
        a = api()
        # 120 extra channels so the "at least 100 alive" guard passes
        for i in range(120):
            a["channels"].append({"id": f"C{i}.bd", "name": f"C{i}", "country": "BD", "categories": []})
            a["streams"].append({"channel": f"C{i}.bd", "url": f"https://c{i}.m3u8", "labels": []})
        with tempfile.TemporaryDirectory() as d:
            src = os.path.join(d, "api"); os.makedirs(src)
            for k in ("streams", "channels", "blocklist", "logos", "countries"):
                with open(os.path.join(src, f"{k}.json"), "w") as f:
                    json.dump(a[k], f)
            out = os.path.join(d, "out"); os.makedirs(out)
            with open(os.path.join(out, "index.json"), "w") as f:
                json.dump({"version": 1, "playlists": [{"id": "mrgify_bdix"}, {"id": la.ID, "old": True}]}, f)
            la.main(["x", out, "--api", src, "--no-probe"])
            idx = json.load(open(os.path.join(out, "index.json")))
            self.assertEqual([p["id"] for p in idx["playlists"]], ["mrgify_bdix", la.ID])
            e = idx["playlists"][1]
            self.assertEqual(e["url"], "https://raw.githubusercontent.com/siamform/yumeiro/streams/playlists/yumeiro_live.m3u")
            self.assertEqual(e["channels"], 122)
            self.assertTrue(os.path.exists(os.path.join(out, "playlists", "yumeiro_live.m3u")))
            self.assertIn("streams", json.load(open(os.path.join(out, "live", "state.json"))))


    def test_state_keys_hide_links(self):
        url = "https://x.example/live.m3u8?token=abc123"
        st = la.update_state({}, {url: True}, "t0")
        saved = {la.key(u): e for u, e in st.items()}
        self.assertNotIn("abc123", json.dumps(saved))
        self.assertEqual(la.by_url(saved, [url]), st)
        self.assertEqual(la.by_url(st, [url]), st)  # version-1 file keyed by link still loads

if __name__ == "__main__":
    unittest.main()
