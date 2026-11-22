"""E2 / ADR-055 unit tests: python3 -m unittest discover -s .github/scripts -p 'test_*.py'"""
import os, sys, unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import curate_live as cl  # noqa: E402


def api():
    ch = lambda i, cc="BD", **k: {"id": i, "name": i.split(".")[0], "country": cc, "categories": ["news"], **k}
    st = lambda c, url, labels=(), q="720p", **k: {"channel": c, "url": url, "labels": list(labels), "quality": q, **k}
    return {
        "channels": [ch("A.bd"), ch("B.bd"), ch("Nsfw.bd", is_nsfw=True), ch("Closed.bd", closed="2024-01-01"),
                     ch("Dmca.bd"), ch("Ua.bd"), ch("Geo.bd"), ch("In.in", "IN")],
        "blocklist": [{"channel": "Dmca.bd", "reason": "dmca"}],
        "logos": [{"channel": "A.bd", "feed": None, "in_use": True, "url": "https://l/a.png"}],
        "streams": [st("A.bd", "http://a/1.m3u8", q="480p"), st("A.bd", "https://a/2.m3u8", q="1080p"),
                    st("B.bd", "https://b/1.m3u8", ["Not 24/7"]), st("Nsfw.bd", "https://n.m3u8"),
                    st("Closed.bd", "https://c.m3u8"), st("Dmca.bd", "https://d.m3u8"),
                    st("Ua.bd", "https://u.m3u8", user_agent="X"), st("Geo.bd", "https://g.m3u8", ["Geo-blocked"]),
                    st("In.in", "https://i.m3u8"), st(None, "https://none.m3u8")],
    }


class CurateLiveTest(unittest.TestCase):
    def test_filters_blocklist_nsfw_closed_headers_and_other_countries(self):
        ids = {s["channel"] for s in cl.candidates(api(), "BD")}
        self.assertEqual(ids, {"A.bd", "B.bd", "Geo.bd"})

    def test_rank_puts_24_7_https_hd_first_and_geo_blocked_last(self):
        urls = [s["url"] for s in cl.candidates(api(), "BD")]
        self.assertEqual(urls, ["https://a/2.m3u8", "http://a/1.m3u8", "https://b/1.m3u8", "https://g.m3u8"])

    def test_choose_keeps_one_live_stream_per_channel_up_to_the_limit(self):
        ranked = cl.candidates(api(), "BD")
        got = cl.choose(ranked, 2, lambda u: u != "https://b/1.m3u8", workers=2)
        self.assertEqual([s["channel"] for s in got], ["A.bd", "Geo.bd"])
        self.assertEqual(len(cl.choose(ranked, 1, lambda u: True)), 1)

    def test_alive_needs_hls_and_a_live_first_variant(self):
        pages = {"https://m/master.m3u8": (200, "https://m/master.m3u8", "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow/index.m3u8\n"),
                 "https://m/low/index.m3u8": (200, "https://m/low/index.m3u8", "#EXTM3U\n#EXTINF:6,\nseg.ts\n"),
                 "https://dead/master.m3u8": (200, "https://dead/master.m3u8", "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nx.m3u8\n"),
                 "https://html/": (200, "https://html/", "<html>blocked</html>")}

        def get(u):
            if u not in pages:
                raise OSError("404")
            return pages[u]
        self.assertTrue(cl.alive("https://m/master.m3u8", get))
        self.assertFalse(cl.alive("https://dead/master.m3u8", get))
        self.assertFalse(cl.alive("https://html/", get))
        self.assertFalse(cl.alive("https://missing/", get))

    def test_m3u_has_logo_group_and_no_comma_in_name(self):
        a = api()
        text = cl.m3u(cl.candidates(a, "BD")[:1], a)
        self.assertIn('tvg-logo="https://l/a.png" group-title="News",A', text)
        self.assertTrue(text.startswith("#EXTM3U\n"))


if __name__ == "__main__":
    unittest.main()
