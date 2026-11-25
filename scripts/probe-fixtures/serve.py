#!/usr/bin/env python3
# ADCHAIN-2 (owner list 23, ADR-062): test pages for the field probe, served on the runner and reached from the
# emulator at http://10.0.2.2:8765. Test tooling only; never part of the app. Every page is plain HTML written
# here, modelled on the real AdLinkFly flow (form#go-link → POST /links/go → {"status":"success","url":…}),
# so each mechanism Yumeiro handles is exercised the same way on every run:
#   /chain?to=page|media|hijack  shortener step: 6 s timer, disabled "Get Link", a full-screen ad layer beside
#                                <body>, a "Download now" decoy, pop-up scripts on every tap
#   /links/go (POST)             the site's own answer with the destination
#   /r1 → 302 /r2 → meta refresh /r3 → JS location.replace → /final   (multiple redirects)
#   /media/clip.mp4              direct video (Range supported)
#   /attach                      the same video as Content-Disposition: attachment
#   /attach.zip                  a non-media attachment (download popup, not the player)
#   /files                       a page with both attachments as ordinary links (in-page downloads)
#   /popup                       a normal page with a real target=_blank link, a window.open button, an
#                                automatic window.open on load and a link to an ad host
#   /normal                      an ordinary article (nothing may change on it)
import http.server, json, os, sys, urllib.parse

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
CLIP = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "clip.mp4")
AD = "https://www.popads.net/popunder?zone=1"

CHAIN = """<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1"><title>Probe shortener step</title>
<style>body{font-family:sans-serif;padding:16px}#ad{position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.01)}
.btn{display:block;width:80%;margin:18px auto;padding:14px;font-size:18px}</style></head><body>
<h2>Your link is almost ready</h2><p id=t>Please wait <b id=s>6</b> seconds</p>
<a class=btn href="#" onclick="return false">Download now</a>
<form id="go-link" action="/links/go" method="post"><input type=hidden name=_token value="probe"><input type=hidden name=to value="__TO__">
<button class="btn get-link" id=gl type=submit disabled>Get Link</button></form>
<p>Probe page for Yumeiro's link-chain walker.</p>
<script>
var n=6,s=document.getElementById('s');var iv=setInterval(function(){n--;s.textContent=n;if(n<=0){clearInterval(iv);document.getElementById('t').textContent='Your link is ready';document.getElementById('gl').disabled=false;}},1000);
var ad=document.createElement('div');ad.id='ad';document.documentElement.appendChild(ad);
ad.addEventListener('click',function(){window.open('__AD__','_blank');});
document.addEventListener('click',function(){try{window.open('__AD__');}catch(e){}},true);
document.getElementById('go-link').addEventListener('submit',function(e){e.preventDefault();
 var x=new XMLHttpRequest();x.open('POST','/links/go');x.setRequestHeader('X-Requested-With','XMLHttpRequest');
 x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');
 x.onload=function(){var j=JSON.parse(x.responseText);document.getElementById('gl').textContent='Opening…';
  setTimeout(function(){location.href=(__HIJACK__?'__AD__':j.url);},800);};
 x.send('_token=probe&to=__TO__');});
</script></body></html>"""

POPUP = """<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1"><title>Probe pop-ups</title>
<style>body{font-family:sans-serif;padding:16px} a,button{display:block;margin:22px 0;font-size:18px;padding:12px}</style></head><body>
<h2>Pop-up test page</h2>
<a id=blank href="/final?from=blank" target="_blank">Open article in new tab</a>
<button id=wo onclick="window.open('__AD__','_blank')">Script pop-up</button>
<a id=adlink href="__AD__">Ad link</a>
<p id=auto>auto pop-up tried</p>
<script>setTimeout(function(){try{window.open('__AD__');}catch(e){}},500);</script>
</body></html>""".replace("__AD__", AD)

PAGE = """<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1"><title>__T__</title>
<style>body{font-family:sans-serif;padding:16px}</style></head><body><h2>__T__</h2><p>__B__</p>__X__</body></html>"""


def page(title, body, extra=""):
    return PAGE.replace("__T__", title).replace("__B__", body).replace("__X__", extra)


class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *a):
        sys.stderr.write("FIXTURE %s %s\n" % (self.command, self.path))

    def send(self, code, body, ctype="text/html; charset=utf-8", headers=None):
        b = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(b)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(b)

    def file(self, ctype, disposition=None):
        size = os.path.getsize(CLIP)
        rng = self.headers.get("Range")
        start, end = 0, size - 1
        if rng and rng.startswith("bytes="):
            a, _, b = rng[6:].partition("-")
            start = int(a) if a else 0
            end = int(b) if b else size - 1
        end = min(end, size - 1)
        self.send_response(206 if rng else 200)
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        if rng:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        if disposition:
            self.send_header("Content-Disposition", disposition)
        self.end_headers()
        if self.command == "HEAD":
            return
        with open(CLIP, "rb") as f:
            f.seek(start)
            left = end - start + 1
            try:
                while left > 0:
                    chunk = f.read(min(65536, left))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    left -= len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(u.query)
        p = u.path
        if p == "/chain":
            to = (q.get("to") or ["page"])[0]
            html = CHAIN.replace("__TO__", to).replace("__AD__", AD).replace("__HIJACK__", "true" if to == "hijack" else "false")
            return self.send(200, html)
        if p == "/r1":
            return self.send(302, "", headers={"Location": "/r2"})
        if p == "/r2":
            return self.send(200, page("Redirect 2", "Moving on", '<meta http-equiv="refresh" content="1;url=/r3">'))
        if p == "/r3":
            return self.send(200, page("Redirect 3", "Almost there", "<script>setTimeout(function(){location.replace('/final')},500)</script>"))
        if p == "/final":
            return self.send(200, page("Probe destination", "This is the real destination page. Nothing here is an ad."))
        if p == "/normal":
            return self.send(200, page("Probe ordinary article", "An ordinary page with a Continue link that must never be tapped by Yumeiro.", '<a href="/final">Continue</a>'))
        if p == "/popup":
            return self.send(200, POPUP)
        if p == "/files":
            return self.send(200, page("Probe files", "Two download links on an ordinary page.",
                                       '<p><a href="/attach">Download the episode</a></p><p><a href="/attach.zip">Download the pack</a></p>'))
        if p == "/media/clip.mp4":
            return self.file("video/mp4")
        if p == "/attach":
            return self.file("video/mp4", 'attachment; filename="Probe clip.mp4"')
        if p == "/attach.zip":
            return self.send(200, b"PK\x05\x06" + b"\x00" * 18, "application/zip", {"Content-Disposition": 'attachment; filename="probe-pack.zip"'})
        return self.send(404, page("Not found", p))

    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0)
        form = urllib.parse.parse_qs(self.rfile.read(n).decode() if n else "")
        if self.path.startswith("/links/go"):
            to = (form.get("to") or ["page"])[0]
            dest = {"page": "/r1", "hijack": "/r1", "media": "/media/clip.mp4"}.get(to, "/r1")
            # The destination is on another host name (as on the real shorteners): nip.io answers 10.0.2.2.
            base = "http://10.0.2.2.nip.io:%d" % PORT
            return self.send(200, json.dumps({"status": "success", "message": "", "url": base + dest}), "application/json")
        return self.send(404, "{}", "application/json")


if __name__ == "__main__":
    http.server.ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
